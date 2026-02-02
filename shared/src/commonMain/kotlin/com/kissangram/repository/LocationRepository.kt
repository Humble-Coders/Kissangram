package com.kissangram.repository

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
}
