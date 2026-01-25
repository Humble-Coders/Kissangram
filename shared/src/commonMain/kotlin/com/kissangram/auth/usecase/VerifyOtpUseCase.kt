package com.kissangram.auth.usecase

import com.kissangram.auth.AuthRepository

class VerifyOtpUseCase(
    private val authRepository: AuthRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(otp: String) {
        if (otp.isBlank() || otp.length != 6) {
            throw IllegalArgumentException("OTP must be 6 digits")
        }
        authRepository.verifyOtp(otp)
    }
}
