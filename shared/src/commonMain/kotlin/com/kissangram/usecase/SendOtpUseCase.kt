package com.kissangram.usecase

import com.kissangram.repository.AuthRepository

class SendOtpUseCase(
    private val authRepository: AuthRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(phoneNumber: String) {
        if (phoneNumber.isBlank() || phoneNumber.length < 10) {
            throw IllegalArgumentException("Invalid phone number")
        }
        authRepository.sendOtp(phoneNumber)
    }
}
