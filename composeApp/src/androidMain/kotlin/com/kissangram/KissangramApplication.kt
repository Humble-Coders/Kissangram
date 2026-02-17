package com.kissangram

import android.app.Application
import android.util.Log
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.cloudinary.android.MediaManager
import com.google.firebase.FirebaseApp
import com.kissangram.util.ImagePrefetchManager
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.HashMap

/**
 * Application entry point. Configures Coil ImageLoader with disk cache for feed scalability.
 */
class KissangramApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Configure Coil with optimized memory + disk cache
        // Enhanced cache for Firebase Storage URLs (100MB disk cache, 35% memory cache)
        // Coil uses OkHttp internally with optimized settings for parallel downloads
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.35) // Increased from 0.25 to 0.35 for faster access
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizeBytes(100 * 1024 * 1024) // 100MB for Firebase Storage caching
                        .build()
                }
                .crossfade(true)
                .build()
        )
        
        Log.d(TAG, "Coil ImageLoader configured: 35% memory cache, 100MB disk cache")
        
        // Initialize ImagePrefetchManager for faster image loading
        val imageLoader = Coil.imageLoader(this)
        ImagePrefetchManager.initialize(this, imageLoader)
        Log.d(TAG, "ImagePrefetchManager initialized")
        
        // Initialize Cloudinary
        initCloudinary()
        
        Log.d(TAG, "onCreate: Initializing Firestore")
        try {
            // Log Firebase project info
            val app = FirebaseApp.getInstance()
            Log.d(TAG, "Firebase project: ${app.options.projectId}")
            Log.d(TAG, "Firebase app name: ${app.name}")
            
            // Use named database "kissangram" instead of "(default)"
            val firestore = FirebaseFirestore.getInstance(FirebaseApp.getInstance(), "kissangram")
            
            // Use DEFAULT settings (with disk persistence) - memory-only was causing "offline" issues
            // The default settings handle network reconnection more gracefully
            Log.d(TAG, "onCreate: Using default Firestore settings (with disk persistence)")
            
            // Run a quick connectivity test in background
            CoroutineScope(Dispatchers.IO).launch {
                testFirestoreConnectivity(firestore)
            }
        } catch (e: Exception) {
            Log.e(TAG, "onCreate: Failed to initialize Firestore", e)
        }
    }
    
    private fun initCloudinary() {
        try {
            val config = HashMap<String, String>()
            config["cloud_name"] = "ddjgu0mng"
            config["api_key"] = "358269548668498"
            config["api_secret"] = "GfgbZyDrqjwwaKVmiP8UaYtQQfU"

            MediaManager.init(this, config)
            Log.d(TAG, "Cloudinary initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Cloudinary. Please add your credentials in KissangramApplication.kt", e)
        }
    }
    
    private suspend fun testFirestoreConnectivity(firestore: FirebaseFirestore) {
        Log.d(TAG, "testFirestoreConnectivity: Starting Firestore connectivity test...")
        try {
            // Try to read a non-existent document - this should fail fast if Firestore is unreachable
            // or return empty if Firestore is working but document doesn't exist
            withTimeout(15_000L) {
                val result = firestore.collection("_connectivity_test").document("ping").get().await()
                if (result.exists()) {
                    Log.d(TAG, "testFirestoreConnectivity: SUCCESS - Firestore is reachable (doc exists)")
                } else {
                    Log.d(TAG, "testFirestoreConnectivity: SUCCESS - Firestore is reachable (doc doesn't exist, but connection works)")
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "testFirestoreConnectivity: TIMEOUT - Firestore is NOT responding after 15s")
            Log.e(TAG, ">>> LIKELY CAUSE: Firestore database is NOT CREATED in Firebase Console <<<")
            Log.e(TAG, ">>> Go to: https://console.firebase.google.com → Your Project → Firestore Database → Create Database <<<")
        } catch (e: Exception) {
            Log.e(TAG, "testFirestoreConnectivity: ERROR - ${e.javaClass.simpleName}: ${e.message}")
            // If we get a permission error, that actually means Firestore IS reachable!
            if (e.message?.contains("PERMISSION_DENIED") == true || e.message?.contains("permission") == true) {
                Log.d(TAG, "testFirestoreConnectivity: Firestore IS reachable but security rules are blocking access")
            }
        }
    }

    companion object {
        private const val TAG = "KissangramApp"
    }
}
