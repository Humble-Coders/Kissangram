package com.kissangram.util

import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.request.ImageRequest
import com.kissangram.repository.StorageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages prefetching of images for faster loading.
 * Prefetches images for upcoming posts in the feed to ensure instant display.
 * Uses low priority to avoid blocking user-initiated image loads.
 */
object ImagePrefetchManager {
    private const val TAG = "ImagePrefetchManager"
    private const val PREFETCH_QUEUE_CAPACITY = 50
    private const val MAX_CONCURRENT_PREFETCHES = 3

    private val prefetchQueue = Channel<String>(PREFETCH_QUEUE_CAPACITY)
    private val activePrefetches = MutableStateFlow<Set<String>>(emptySet())
    val activePrefetchesState: StateFlow<Set<String>> = activePrefetches.asStateFlow()

    private var prefetchJob: Job? = null
    private var imageLoader: ImageLoader? = null

    /**
     * Initialize the prefetch manager with the app's ImageLoader.
     * Should be called once during app startup.
     */
    fun initialize(context: Context, loader: ImageLoader) {
        if (prefetchJob?.isActive == true) {
            Log.w(TAG, "ImagePrefetchManager already initialized")
            return
        }

        imageLoader = loader
        prefetchJob = CoroutineScope(Dispatchers.IO).launch {
            processPrefetchQueue(context)
        }

        Log.d(TAG, "ImagePrefetchManager initialized")
    }

    /**
     * Prefetch images for the given URLs.
     * Only prefetches Firebase Storage URLs to optimize Firebase Storage fetching.
     *
     * @param urls List of image URLs to prefetch
     */
    fun prefetch(urls: List<String>) {
        val firebaseUrls = urls.filter { FirebaseStorageUrlTransformer.isFirebaseStorageUrl(it) }

        if (firebaseUrls.isEmpty()) {
            return
        }

        firebaseUrls.forEach { url ->
            if (!prefetchQueue.trySend(url).isSuccess) {
                Log.w(TAG, "Prefetch queue full, dropping URL: ${url.take(50)}...")
            }
        }

        Log.d(TAG, "Queued ${firebaseUrls.size} Firebase Storage URLs for prefetching")
    }

    /**
     * Cancel prefetching for specific URLs.
     * Useful when user navigates away from a screen.
     */
    fun cancelPrefetch(urls: List<String>) {
        // Note: Coil doesn't support canceling individual prefetch requests easily
        // This is mainly for tracking purposes
        val currentActive = activePrefetches.value
        val toCancel = urls.filter { currentActive.contains(it) }
        if (toCancel.isNotEmpty()) {
            activePrefetches.value = currentActive - toCancel.toSet()
            Log.d(TAG, "Cancelled prefetch for ${toCancel.size} URLs")
        }
    }

    /**
     * Clear all pending prefetches.
     */
    fun clearQueue() {
        while (!prefetchQueue.isEmpty) {
            prefetchQueue.tryReceive()
        }
        Log.d(TAG, "Prefetch queue cleared")
    }

    /**
     * Process the prefetch queue with rate limiting.
     */
    private suspend fun processPrefetchQueue(context: Context) {
        val loader = imageLoader ?: run {
            Log.e(TAG, "ImageLoader not initialized")
            return
        }

        var activeCount = 0

        while (currentCoroutineContext().isActive) {
            try {
                val url = prefetchQueue.receive()

                // Skip if already prefetching this URL
                if (activePrefetches.value.contains(url)) {
                    continue
                }

                // Wait if we've reached max concurrent prefetches
                while (activeCount >= MAX_CONCURRENT_PREFETCHES && currentCoroutineContext().isActive) {
                    kotlinx.coroutines.delay(100)
                    activeCount = activePrefetches.value.size
                }

                if (!currentCoroutineContext().isActive) break

                // Add to active prefetches
                activePrefetches.value = activePrefetches.value + url
                activeCount++

                // Prefetch in background (Coil 3.x removed Priority API; queue ordering handles priority)
                try {
                    loader.enqueue(
                        ImageRequest.Builder(context)
                            .data(url)
                            .build()
                    )
                    Log.d(TAG, "Prefetched image: ${url.take(50)}...")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to prefetch image: ${url.take(50)}...", e)
                } finally {
                    // Remove from active prefetches after a delay
                    kotlinx.coroutines.delay(500) // Small delay to allow request to start
                    activePrefetches.value = activePrefetches.value - url
                    activeCount = activePrefetches.value.size
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing prefetch queue", e)
            }
        }
    }

    /**
     * Get statistics about prefetching.
     */
    fun getStats(): PrefetchStats {
        return PrefetchStats(
            queueSize = prefetchQueue.tryReceive().isSuccess.let { if (it) 1 else 0 },
            activeCount = activePrefetches.value.size,
            maxConcurrent = MAX_CONCURRENT_PREFETCHES
        )
    }

    data class PrefetchStats(
        val queueSize: Int,
        val activeCount: Int,
        val maxConcurrent: Int
    )
}