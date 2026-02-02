package com.kissangram.usecase

import com.kissangram.model.UserRole
import com.kissangram.repository.UserRepository

/**
 * Use case to update user profile.
 * Validates input and delegates to UserRepository.
 */
class UpdateProfileUseCase(
    private val userRepository: UserRepository
) {
    /**
     * Update user profile with the given fields.
     * Only non-null fields will be updated.
     * 
     * @param name User's display name (min 2 chars)
     * @param bio User's bio (max 150 chars)
     * @param profileImageUrl URL of the profile image
     * @param role User's role
     * @param state User's state
     * @param district User's district
     * @param village User's village (optional)
     * @param crops List of crops the user grows
     * @throws IllegalArgumentException if validation fails
     */
    @Throws(Exception::class)
    suspend operator fun invoke(
        name: String? = null,
        bio: String? = null,
        profileImageUrl: String? = null,
        role: UserRole? = null,
        state: String? = null,
        district: String? = null,
        village: String? = null,
        crops: List<String>? = null
    ) {
        // Validate name if provided
        name?.let {
            if (it.isBlank()) {
                throw IllegalArgumentException("Name cannot be empty")
            }
            if (it.length < 2) {
                throw IllegalArgumentException("Name must be at least 2 characters")
            }
            if (it.length > 50) {
                throw IllegalArgumentException("Name cannot exceed 50 characters")
            }
        }
        
        // Validate bio if provided
        bio?.let {
            if (it.length > 150) {
                throw IllegalArgumentException("Bio cannot exceed 150 characters")
            }
        }
        
        // Delegate to repository
        userRepository.updateFullProfile(
            name = name,
            bio = bio,
            profileImageUrl = profileImageUrl,
            role = role,
            state = state,
            district = district,
            village = village,
            crops = crops
        )
    }
}
