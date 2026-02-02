package com.kissangram.repository

/**
 * Repository interface for fetching crops data.
 * Reference data is stored in Firestore at appConfig/crops.
 */
interface CropsRepository {
    
    /**
     * Get all available crops as a flat list, sorted alphabetically.
     * @return List of crop names
     */
    @Throws(Exception::class)
    suspend fun getAllCrops(): List<String>
}
