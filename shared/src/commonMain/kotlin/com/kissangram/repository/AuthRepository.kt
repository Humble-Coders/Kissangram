package com.kissangram.repository

interface AuthRepository {
    @Throws(Exception::class)
    suspend fun sendOtp(phoneNumber: String)
    
    @Throws(Exception::class)
    suspend fun verifyOtp(otp: String)
    
    @Throws(Exception::class)
    suspend fun updateUserProfile(name: String)
    
    suspend fun getCurrentUserId(): String?
    /** Phone number of the signed-in user (e.g. +919876543210). */
    suspend fun getCurrentUserPhoneNumber(): String?
    /** Display name set after Name screen. */
    suspend fun getCurrentUserDisplayName(): String?
    suspend fun isUserAuthenticated(): Boolean
    suspend fun signOut()
}
