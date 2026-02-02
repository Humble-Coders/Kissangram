package com.kissangram.usecase

import com.kissangram.repository.LocationRepository

/**
 * Use case to get all available states for location selection.
 */
class GetStatesUseCase(
    private val locationRepository: LocationRepository
) {
    /**
     * Get sorted list of all state names.
     * @return List of state names
     */
    @Throws(Exception::class)
    suspend operator fun invoke(): List<String> {
        return locationRepository.getStates()
    }
}
