package com.kissangram.repository

import com.kissangram.model.LocationCoordinates

/**
 * Repository interface for fetching location data (states and districts).
 * Reference data is stored in Firestore at appConfig/locations.
 */
interface LocationRepository {
    
    /**
     * Get all available state names, sorted alphabetically.
     * @return List of state names
     */
    @Throws(Exception::class)
    suspend fun getStates(): List<String>
    
    /**
     * Get all districts for a given state.
     * @param state The state name
     * @return List of district names for the state
     */
    @Throws(Exception::class)
    suspend fun getDistricts(state: String): List<String>
    
    /**
     * Get all states with their districts.
     * Useful for caching all data at once.
     * @return Map of state name to list of districts
     */
    @Throws(Exception::class)
    suspend fun getAllStatesAndDistricts(): Map<String, List<String>>
    
    /**
     * Get current GPS location coordinates.
     * @return LocationCoordinates or null if unavailable
     */
    @Throws(Exception::class)
    suspend fun getCurrentLocation(): LocationCoordinates?
    
    /**
     * Reverse geocode: Convert coordinates to location name.
     * @param latitude The latitude
     * @param longitude The longitude
     * @return Location name (e.g., "Ludhiana, Punjab") or null if not found
     */
    @Throws(Exception::class)
    suspend fun reverseGeocode(latitude: Double, longitude: Double): String?
    
    /**
     * Forward geocode: Convert location name to coordinates.
     * @param locationName The location name (e.g., "Ludhiana, Punjab")
     * @return LocationCoordinates or null if not found
     */
    @Throws(Exception::class)
    suspend fun forwardGeocode(locationName: String): LocationCoordinates?
    
    /**
     * Check if location permission is granted.
     * @return true if permission is granted, false otherwise
     */
    fun hasLocationPermission(): Boolean
    
    /**
     * Request location permission.
     * Note: On Android, this should be handled by the UI layer.
     * @return true if permission is granted, false otherwise
     */
    @Throws(Exception::class)
    suspend fun requestLocationPermission(): Boolean
}
