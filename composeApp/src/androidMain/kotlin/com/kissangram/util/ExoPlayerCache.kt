package com.kissangram.util

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.util.LinkedHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * LRU cache for ExoPlayer instances.
 * Reuses players when scrolling back to videos—avoids re-buffering.
 * Thread-safe; max capacity prevents unbounded memory growth.
 * Supports batch preloading for smooth video playback.
 */
object ExoPlayerCache {
    private const val MAX_CAPACITY = 5
    private const val MAX_PRELOAD_CAPACITY = 2 // Max 2 preloaded videos
    private val lock = ReentrantLock()
    private val cache = LinkedHashMap<String, CachedPlayer>(MAX_CAPACITY, 0.75f, true)
    private val preloadCache = LinkedHashMap<String, CachedPlayer>(MAX_PRELOAD_CAPACITY, 0.75f, true)
    
    private data class CachedPlayer(
        val player: ExoPlayer,
        var lastAccess: Long = System.currentTimeMillis(),
        var isPreloaded: Boolean = false
    )
    
    fun getPlayer(context: Context, url: String): ExoPlayer {
        lock.withLock {
            // Check main cache first
            cache[url]?.let { cached ->
                cached.lastAccess = System.currentTimeMillis()
                cached.isPreloaded = false
                return cached.player
            }
            
            // Check preload cache - if found, move to main cache
            preloadCache[url]?.let { preloaded ->
                preloadCache.remove(url)
                preloaded.isPreloaded = false
                preloaded.lastAccess = System.currentTimeMillis()
                cache[url] = preloaded
                return preloaded.player
            }
            
            // Create new player
            if (cache.size >= MAX_CAPACITY) {
                val oldest = cache.keys.firstOrNull()
                if (oldest != null) {
                    cache.remove(oldest)?.player?.apply {
                        stop()
                        release()
                    }
                }
            }
            
            val player = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(url))
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
            }
            cache[url] = CachedPlayer(player, isPreloaded = false)
            return player
        }
    }
    
    /**
     * Preload videos in batch for smooth playback.
     * Preloads up to MAX_PRELOAD_CAPACITY videos.
     * @param context Android context
     * @param urls List of video URLs to preload (will preload first MAX_PRELOAD_CAPACITY)
     */
    fun preloadVideos(context: Context, urls: List<String>) {
        lock.withLock {
            val urlsToPreload = urls.take(MAX_PRELOAD_CAPACITY)
            
            urlsToPreload.forEach { url ->
                // Skip if already in cache or preload cache
                if (cache.containsKey(url) || preloadCache.containsKey(url)) {
                    return@forEach
                }
                
                // Evict oldest preload if at capacity
                if (preloadCache.size >= MAX_PRELOAD_CAPACITY) {
                    val oldest = preloadCache.keys.firstOrNull()
                    if (oldest != null) {
                        preloadCache.remove(oldest)?.player?.apply {
                            stop()
                            release()
                        }
                    }
                }
                
                // Create and preload player
                val player = ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(url))
                    repeatMode = Player.REPEAT_MODE_ONE
                    prepare() // Preload by preparing
                }
                preloadCache[url] = CachedPlayer(player, isPreloaded = true)
            }
        }
    }
    
    /**
     * Clear preload cache to free memory.
     */
    fun clearPreloadCache() {
        lock.withLock {
            preloadCache.values.forEach { it.player.stop(); it.player.release() }
            preloadCache.clear()
        }
    }
}
