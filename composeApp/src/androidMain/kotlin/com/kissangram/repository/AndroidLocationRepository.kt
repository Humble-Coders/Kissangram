package com.kissangram.repository

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Android implementation of LocationRepository.
 * Fetches location data from Firestore appConfig/locations document.
 */
class AndroidLocationRepository : LocationRepository {
    
    private val firestore = FirebaseFirestore.getInstance(
        FirebaseApp.getInstance(),
        "kissangram"
    )
    
    // Cache for location data to avoid repeated Firestore reads
    private var cachedStates: List<String>? = null
    private var cachedStatesAndDistricts: Map<String, List<String>>? = null
    
    override suspend fun getStates(): List<String> {
        // Return cached data if available
        cachedStates?.let { 
            println("✅ AndroidLocationRepository: Returning ${it.size} cached states")
            return it 
        }
        
        println("🔍 AndroidLocationRepository: Fetching states from Firestore...")
        
        // Fetch from Firestore
        val document = firestore.collection("appConfig")
            .document("locations")
            .get()
            .await()
        
        println("📄 AndroidLocationRepository: Document exists = ${document.exists()}")
        
        if (!document.exists()) {
            throw Exception("Location data not found. Please upload location data first.")
        }
        
        @Suppress("UNCHECKED_CAST")
        val stateNames = document.get("stateNames") as? List<String>
            ?: throw Exception("State names not found in location data")
        
        println("✅ AndroidLocationRepository: Fetched ${stateNames.size} states")
        
        // Cache and return
        cachedStates = stateNames
        return stateNames
    }
    
    override suspend fun getDistricts(state: String): List<String> {
        // Try to get from cache first
        cachedStatesAndDistricts?.get(state)?.let { return it }
        
        // Fetch all data if not cached
        val allData = getAllStatesAndDistricts()
        return allData[state] ?: emptyList()
    }
    
    override suspend fun getAllStatesAndDistricts(): Map<String, List<String>> {
        // Return cached data if available
        cachedStatesAndDistricts?.let { return it }
        
        // Fetch from Firestore
        val document = firestore.collection("appConfig")
            .document("locations")
            .get()
            .await()
        
        if (!document.exists()) {
            throw Exception("Location data not found. Please upload location data first.")
        }
        
        @Suppress("UNCHECKED_CAST")
        val statesAndDistricts = document.get("statesAndDistricts") as? Map<String, List<String>>
            ?: throw Exception("States and districts data not found")
        
        // Also cache the state names
        @Suppress("UNCHECKED_CAST")
        val stateNames = document.get("stateNames") as? List<String>
        cachedStates = stateNames
        
        // Cache and return
        cachedStatesAndDistricts = statesAndDistricts
        return statesAndDistricts
    }
    
    /**
     * Clear the cache. Useful when you want to force a refresh.
     */
    fun clearCache() {
        cachedStates = null
        cachedStatesAndDistricts = null
    }
}
