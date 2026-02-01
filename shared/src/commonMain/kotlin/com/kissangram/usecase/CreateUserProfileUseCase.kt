package com.kissangram.usecase

import com.kissangram.model.UserRole
import com.kissangram.model.VerificationStatus
import com.kissangram.repository.AuthRepository
import com.kissangram.repository.PreferencesRepository
import com.kissangram.repository.UserRepository

/**
 * Creates the user profile document in Firestore after onboarding.
 * Call after role selection (non-expert) or after expert doc upload (skip or complete).
 */
class CreateUserProfileUseCase(
    private val authRepository: AuthRepository,
    private val preferencesRepository: PreferencesRepository,
    private val userRepository: UserRepository
) {
    //@Throws(Exception::class)
    suspend operator fun invoke(
        role: UserRole,
        verificationDocUrl: String? = null,
        verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED
    ) {

        // log
        println("CreateUserProfileUseCase: role=$role, verificationDocUrl=$verificationDocUrl, verificationStatus=$verificationStatus")

        val userId = authRepository.getCurrentUserId()
            ?: throw IllegalStateException("No authenticated user")
        val phoneNumber = authRepository.getCurrentUserPhoneNumber()
            ?: throw IllegalStateException("User phone number not available")
        val name = authRepository.getCurrentUserDisplayName()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User display name not set")
        val language = preferencesRepository.getSelectedLanguageCode() ?: "en"

        // log
        println("CreateUserProfileUseCase: userId=$userId, phoneNumber=$phoneNumber, name=$name, language=$language")

        println("CreateUserProfileUseCase: about to call userRepository.createUserProfile")
        userRepository.createUserProfile(
            userId = userId,
            phoneNumber = phoneNumber,
            name = name,
            role = role,
            language = language,
            verificationDocUrl = verificationDocUrl,
            verificationStatus = verificationStatus
        )

        // log
        println("CreateUserProfileUseCase: user profile created")
    }
}
