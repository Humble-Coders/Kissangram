package com.kissangram.usecase

import com.kissangram.model.User
import com.kissangram.repository.UserRepository

/**
 * Use case to get the currently authenticated user's profile.
 */
class GetCurrentUserUseCase(
    private val userRepository: UserRepository
) {
    /**
     * Get the current user's profile from Firestore.
     * @return User object or null if not authenticated or profile doesn't exist
     */
    @Throws(Exception::class)
    suspend operator fun invoke(): User? {
        return userRepository.getCurrentUser()
    }
}
