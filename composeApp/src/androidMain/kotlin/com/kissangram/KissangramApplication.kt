package com.kissangram

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Application entry point. Disables Firestore disk persistence so writes go to the server
 * and fail fast when offline instead of queuing indefinitely (Task never completing).
 */
class KissangramApplication : Application() {

    override fun onCreate() {
        super.onCreate()
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
