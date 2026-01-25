package com.kissangram.auth.usecase

import com.kissangram.auth.AuthRepository

class UpdateUserProfileUseCase(
    private val authRepository: AuthRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(name: String) {
        if (name.isBlank() || name.length < 2) {
            throw IllegalArgumentException("Name must be at least 2 characters")
        }
        authRepository.updateUserProfile(name.trim())
    }
}
