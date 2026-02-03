package com.kissangram.usecase

import com.kissangram.repository.AuthRepository
import com.kissangram.repository.StorageRepository

/**
 * Use case for uploading a profile image to Firebase Storage.
 * Returns the download URL of the uploaded image.
 */
class UploadProfileImageUseCase(
    private val storageRepository: StorageRepository,
    private val authRepository: AuthRepository
) {
    /**
     * Upload a profile image for the current user.
     * @param imageData The image data as ByteArray
     * @return The download URL of the uploaded image
     * @throws IllegalStateException if no user is authenticated
     * @throws Exception if upload fails
     */
    @Throws(Exception::class)
    suspend operator fun invoke(imageData: ByteArray): String {
        val userId = authRepository.getCurrentUserId()
            ?: throw IllegalStateException("No authenticated user")
        
        if (imageData.isEmpty()) {
            throw IllegalArgumentException("Image data cannot be empty")
        }
        
        return storageRepository.uploadProfileImage(userId, imageData)
    }
}
