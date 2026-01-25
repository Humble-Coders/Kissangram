package com.kissangram.auth

interface AuthRepository {
    @Throws(Exception::class)
    suspend fun sendOtp(phoneNumber: String)
    
    @Throws(Exception::class)
    suspend fun verifyOtp(otp: String)
    
    @Throws(Exception::class)
    suspend fun updateUserProfile(name: String)
    
    suspend fun getCurrentUserId(): String?
    suspend fun isUserAuthenticated(): Boolean
    suspend fun signOut()
}
