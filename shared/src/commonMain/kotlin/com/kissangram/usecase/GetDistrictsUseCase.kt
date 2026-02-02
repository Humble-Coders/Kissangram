package com.kissangram.usecase

import com.kissangram.repository.LocationRepository

/**
 * Use case to get all districts for a given state.
 */
class GetDistrictsUseCase(
    private val locationRepository: LocationRepository
) {
    /**
     * Get list of districts for the specified state.
     * @param state The state name
     * @return List of district names, empty if state not found
     */
    @Throws(Exception::class)
    suspend operator fun invoke(state: String): List<String> {
        if (state.isBlank()) {
            return emptyList()
        }
        return locationRepository.getDistricts(state)
    }
}
