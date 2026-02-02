package com.kissangram.repository

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Android implementation of CropsRepository.
 * Fetches crops data from Firestore appConfig/crops document.
 */
class AndroidCropsRepository : CropsRepository {
    
    private val firestore = FirebaseFirestore.getInstance(
        FirebaseApp.getInstance(),
        "kissangram"
    )
    
    // Cache for crops data to avoid repeated Firestore reads
    private var cachedCrops: List<String>? = null
    
    override suspend fun getAllCrops(): List<String> {
        // Return cached data if available
        cachedCrops?.let { 
            println("✅ AndroidCropsRepository: Returning ${it.size} cached crops")
            return it 
        }
        
        println("🔍 AndroidCropsRepository: Fetching crops from Firestore...")
        
        // Fetch from Firestore
        val document = firestore.collection("appConfig")
            .document("crops")
            .get()
            .await()
        
        println("📄 AndroidCropsRepository: Document exists = ${document.exists()}")
        
        if (!document.exists()) {
            throw Exception("Crops data not found. Please upload crops data first.")
        }
        
        @Suppress("UNCHECKED_CAST")
        val allCrops = document.get("allCrops") as? List<String>
            ?: throw Exception("Crops list not found in crops data")
        
        println("✅ AndroidCropsRepository: Fetched ${allCrops.size} crops")
        
        // Cache and return
        cachedCrops = allCrops
        return allCrops
    }
    
    /**
     * Clear the cache. Useful when you want to force a refresh.
     */
    fun clearCache() {
        cachedCrops = null
    }
}
