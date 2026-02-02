package com.kissangram.usecase

import com.kissangram.repository.CropsRepository

/**
 * Use case to get all available crops for selection.
 */
class GetAllCropsUseCase(
    private val cropsRepository: CropsRepository
) {
    /**
     * Get sorted list of all crop names.
     * @return List of crop names
     */
    @Throws(Exception::class)
    suspend operator fun invoke(): List<String> {
        return cropsRepository.getAllCrops()
    }
}
