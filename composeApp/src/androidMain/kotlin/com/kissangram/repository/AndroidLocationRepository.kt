package com.kissangram.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationServices

import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.kissangram.model.LocationCoordinates
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * Android implementation of LocationRepository.
 * Fetches location data from Firestore appConfig/locations document.
 */
class AndroidLocationRepository(
    private val context: Context
) : LocationRepository {
    
    private val firestore = FirebaseFirestore.getInstance(
        FirebaseApp.getInstance(),
        "kissangram"
    )
    
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    
    private val geocoder = Geocoder(context, Locale.getDefault())
    
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
    
    // MARK: - Geocoding
    
    override fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    override suspend fun requestLocationPermission(): Boolean {
        // Permission request is handled by the UI layer
        // This method is a placeholder for the interface contract
        return hasLocationPermission()
    }
    
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override suspend fun getCurrentLocation(): LocationCoordinates? {
        if (!hasLocationPermission()) {
            throw Exception("Location permission not granted")
        }
        
        return try {
            val cancellationTokenSource = CancellationTokenSource()
            val locationResult = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).await()
            
            locationResult?.let {
                LocationCoordinates(latitude = it.latitude, longitude = it.longitude)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    override suspend fun reverseGeocode(latitude: Double, longitude: Double): String? {
        return try {
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                val address = addresses[0]
                // Build location string: "District, State" or "City, State"
                val district = address.subAdminArea ?: address.locality
                val state = address.adminArea
                
                when {
                    district != null && state != null -> "$district, $state"
                    state != null -> state
                    district != null -> district
                    else -> address.getAddressLine(0) ?: null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    override suspend fun forwardGeocode(locationName: String): LocationCoordinates? {
        return try {
            val addresses = geocoder.getFromLocationName(locationName, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                val address = addresses[0]
                LocationCoordinates(latitude = address.latitude, longitude = address.longitude)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
