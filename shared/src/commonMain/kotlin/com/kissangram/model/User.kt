package com.kissangram.model

/**
 * User profile data model
 */
data class User(
    val id: String,
    val phoneNumber: String,
    val name: String,
    val username: String,
    val profileImageUrl: String?,
    val bio: String?,
    val role: UserRole,
    val verificationStatus: VerificationStatus,
    val location: UserLocation?,
    val expertise: List<String>,
    val followersCount: Int,
    val followingCount: Int,
    val postsCount: Int,
    val language: String,
    val createdAt: Long,
    val lastActiveAt: Long?
)

/**
 * Simplified user info for embedding in other models (denormalized)
 */
data class UserInfo(
    val id: String,
    val name: String,
    val username: String,
    val profileImageUrl: String?,
    val role: UserRole,
    val verificationStatus: VerificationStatus
)

data class UserLocation(
    val district: String?,
    val state: String?,
    val country: String?,
    val village: String?
)

enum class VerificationStatus {
    UNVERIFIED,
    PENDING,
    VERIFIED,
    REJECTED
}
