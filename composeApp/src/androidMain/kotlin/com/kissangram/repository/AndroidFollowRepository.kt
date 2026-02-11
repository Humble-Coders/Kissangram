package com.kissangram.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.kissangram.model.UserRole
import com.kissangram.model.VerificationStatus
import kotlinx.coroutines.tasks.await

/**
 * Android implementation of FollowRepository.
 * Handles follow/unfollow operations using Firestore per FIRESTORE_SCHEMA.md.
 * 
 * Follow structure:
 * - /users/{currentUserId}/following/{targetUserId} - with denormalized user info
 * - /users/{targetUserId}/followers/{currentUserId} - with denormalized user info
 * - Updates followersCount and followingCount atomically
 */
class AndroidFollowRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(
        com.google.firebase.FirebaseApp.getInstance(),
        DATABASE_NAME
    ),
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : FollowRepository {

    private val usersCollection
        get() = firestore.collection("users")

    override suspend fun followUser(userId: String) {
        val currentUserId = authRepository.getCurrentUserId()
            ?: throw IllegalStateException("No authenticated user")

        if (currentUserId == userId) {
            throw IllegalArgumentException("Cannot follow yourself")
        }

        // Get current user info for denormalization
        val currentUser = userRepository.getCurrentUser()
            ?: throw IllegalStateException("Current user not found")

        // Get target user info for denormalization
        val targetUser = userRepository.getUser(userId)
            ?: throw IllegalArgumentException("User not found")

        val batch = firestore.batch()

        // Add to current user's following list with denormalized target user info
        val currentUserFollowingRef = usersCollection
            .document(currentUserId)
            .collection("following")
            .document(userId)
        
        val followingData = hashMapOf<String, Any>(
            "id" to userId,
            "name" to targetUser.name,
            "username" to targetUser.username,
            "profileImageUrl" to (targetUser.profileImageUrl ?: ""),
            "role" to roleToFirestore(targetUser.role),
            "verificationStatus" to verificationStatusToFirestore(targetUser.verificationStatus),
            "followedAt" to FieldValue.serverTimestamp()
        )
        batch.set(currentUserFollowingRef, followingData)

        // Add to target user's followers list with denormalized current user info
        val targetUserFollowersRef = usersCollection
            .document(userId)
            .collection("followers")
            .document(currentUserId)
        
        val followerData = hashMapOf<String, Any>(
            "id" to currentUserId,
            "name" to currentUser.name,
            "username" to currentUser.username,
            "profileImageUrl" to (currentUser.profileImageUrl ?: ""),
            "role" to roleToFirestore(currentUser.role),
            "verificationStatus" to verificationStatusToFirestore(currentUser.verificationStatus),
            "followedAt" to FieldValue.serverTimestamp()
        )
        batch.set(targetUserFollowersRef, followerData)

        // Update follower/following counts atomically
        val currentUserRef = usersCollection.document(currentUserId)
        batch.update(currentUserRef, "followingCount", FieldValue.increment(1))

        val targetUserRef = usersCollection.document(userId)
        batch.update(targetUserRef, "followersCount", FieldValue.increment(1))

        try {
            batch.commit().await()
            Log.d(TAG, "Successfully followed user: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to follow user: $userId", e)
            throw e
        }
    }

    override suspend fun unfollowUser(userId: String) {
        val currentUserId = authRepository.getCurrentUserId()
            ?: throw IllegalStateException("No authenticated user")

        if (currentUserId == userId) {
            throw IllegalArgumentException("Cannot unfollow yourself")
        }

        val batch = firestore.batch()

        // Remove from current user's following list
        val currentUserFollowingRef = usersCollection
            .document(currentUserId)
            .collection("following")
            .document(userId)
        batch.delete(currentUserFollowingRef)

        // Remove from target user's followers list
        val targetUserFollowersRef = usersCollection
            .document(userId)
            .collection("followers")
            .document(currentUserId)
        batch.delete(targetUserFollowersRef)

        // Update follower/following counts atomically
        val currentUserRef = usersCollection.document(currentUserId)
        batch.update(currentUserRef, "followingCount", FieldValue.increment(-1))

        val targetUserRef = usersCollection.document(userId)
        batch.update(targetUserRef, "followersCount", FieldValue.increment(-1))

        try {
            batch.commit().await()
            Log.d(TAG, "Successfully unfollowed user: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unfollow user: $userId", e)
            throw e
        }
    }

    override suspend fun isFollowing(userId: String): Boolean {
        val currentUserId = authRepository.getCurrentUserId() ?: return false
        
        if (currentUserId == userId) return false

        return try {
            val followingRef = usersCollection
                .document(currentUserId)
                .collection("following")
                .document(userId)
            val doc = followingRef.get().await()
            doc.exists()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check follow status for user: $userId", e)
            false
        }
    }

    override suspend fun isFollowedBy(userId: String): Boolean {
        val currentUserId = authRepository.getCurrentUserId() ?: return false
        
        if (currentUserId == userId) return false

        return try {
            val followersRef = usersCollection
                .document(currentUserId)
                .collection("followers")
                .document(userId)
            val doc = followersRef.get().await()
            doc.exists()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check followed-by status for user: $userId", e)
            false
        }
    }

    private fun roleToFirestore(role: UserRole): String = when (role) {
        UserRole.FARMER -> "farmer"
        UserRole.EXPERT -> "expert"
        UserRole.AGRIPRENEUR -> "agripreneur"
        UserRole.INPUT_SELLER -> "input_seller"
        UserRole.AGRI_LOVER -> "agri_lover"
    }

    private fun verificationStatusToFirestore(status: VerificationStatus): String = when (status) {
        VerificationStatus.UNVERIFIED -> "unverified"
        VerificationStatus.PENDING -> "pending"
        VerificationStatus.VERIFIED -> "verified"
        VerificationStatus.REJECTED -> "rejected"
    }

    companion object {
        private const val TAG = "AndroidFollowRepo"
        private const val DATABASE_NAME = "kissangram"
    }
}
