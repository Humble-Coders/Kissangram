package com.kissangram.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.kissangram.model.User
import com.kissangram.model.UserInfo
import com.kissangram.model.UserRole
import com.kissangram.model.VerificationStatus
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.io.IOException

/**
 * Firestore implementation of UserRepository.
 * Creates and reads user profile at /users/{userId} per FIRESTORE_SCHEMA.md.
 */
class FirestoreUserRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(
        com.google.firebase.FirebaseApp.getInstance(),
        DATABASE_NAME
    ),
    private val authRepository: com.kissangram.repository.AuthRepository
) : com.kissangram.repository.UserRepository {

    private val usersCollection
        get() = firestore.collection("users")

    override suspend fun createUserProfile(
        userId: String,
        phoneNumber: String,
        name: String,
        role: UserRole,
        language: String,
        verificationDocUrl: String?,
        verificationStatus: VerificationStatus
    ) {
        Log.d(TAG, "createUserProfile ENTRY: userId=$userId")

        val username = generateUsername(name, userId)
        val searchKeywords = buildSearchKeywords(name, username)
        val now = System.currentTimeMillis()

        // Check if user document already exists to preserve count fields
        val userDocRef = usersCollection.document(userId)
        val existingDoc = try {
            userDocRef.get().await()
        } catch (e: Exception) {
            Log.w(TAG, "createUserProfile: Error checking existing user, assuming new user", e)
            null
        }
        
        val userExists = existingDoc?.exists() == true
        Log.d(TAG, "createUserProfile: User exists=$userExists")

        // Build data map with base fields
        val data = hashMapOf<String, Any>(
            FIELD_ID to userId,
            FIELD_PHONE_NUMBER to phoneNumber,
            FIELD_NAME to name,
            FIELD_USERNAME to username,
            FIELD_ROLE to roleToFirestore(role),
            FIELD_VERIFICATION_STATUS to verificationStatusToFirestore(verificationStatus),
            FIELD_EXPERTISE to emptyList<String>(),
            FIELD_LANGUAGE to language,
            FIELD_UPDATED_AT to now,
            FIELD_LAST_ACTIVE_AT to now,
            FIELD_SEARCH_KEYWORDS to searchKeywords,
            FIELD_IS_ACTIVE to true
        )
        
        // Only set count fields and createdAt if user doesn't exist
        // This preserves existing follow counts when user logs in again
        if (!userExists) {
            data[FIELD_FOLLOWERS_COUNT] = 0L
            data[FIELD_FOLLOWING_COUNT] = 0L
            data[FIELD_POSTS_COUNT] = 0L
            data[FIELD_CREATED_AT] = now
            Log.d(TAG, "createUserProfile: New user - setting count fields to 0")
        } else {
            Log.d(TAG, "createUserProfile: Existing user - preserving count fields")
        }
        
        verificationDocUrl?.let { data[FIELD_VERIFICATION_DOC_URL] = it }

        Log.d(TAG, "createUserProfile: Writing user document with ${data.size} fields (timeout 30s)")
        
        // Use longer timeout and let Firestore handle offline queueing with disk persistence
        try {
            withTimeout(30_000L) {
                usersCollection.document(userId).set(data, SetOptions.merge()).await()
            }
            Log.d(TAG, "createUserProfile: SUCCESS - User profile ${if (userExists) "updated" else "created"}")
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "createUserProfile: Firestore write timed out after 30s", e)
            throw IOException("Request timed out. Check your connection and try again.", e)
        } catch (e: Exception) {
            Log.e(TAG, "createUserProfile: Firestore write FAILED", e)
            throw IOException("Failed to create profile: ${e.message}", e)
        }
    }

    override suspend fun getCurrentUser(): User? {
        val userId = authRepository.getCurrentUserId() ?: return null
        return getUser(userId)
    }

    override suspend fun getUser(userId: String): User? {
        val doc = usersCollection.document(userId).get().await()
        if (!doc.exists()) return null
        return doc.toUser()
    }

    override suspend fun getUserInfo(userId: String): UserInfo? {
        val user = getUser(userId) ?: return null
        return UserInfo(
            id = user.id,
            name = user.name,
            username = user.username,
            profileImageUrl = user.profileImageUrl,
            role = user.role,
            verificationStatus = user.verificationStatus
        )
    }

    override suspend fun updateProfile(
        name: String?,
        username: String?,
        bio: String?,
        profileImageUrl: String?
    ) {
        val userId = authRepository.getCurrentUserId()
            ?: throw IllegalStateException("No authenticated user")
        val updates = mutableMapOf<String, Any?>(FIELD_UPDATED_AT to System.currentTimeMillis())
        name?.let { updates[FIELD_NAME] = it }
        username?.let { updates[FIELD_USERNAME] = it }
        bio?.let { updates[FIELD_BIO] = it }
        profileImageUrl?.let { updates[FIELD_PROFILE_IMAGE_URL] = it }
        if (updates.size > 1) {
            usersCollection.document(userId).update(updates.filterValues { it != null }).await()
        }
    }
    
    override suspend fun updateFullProfile(
        name: String?,
        bio: String?,
        profileImageUrl: String?,
        role: UserRole?,
        state: String?,
        district: String?,
        village: String?,
        crops: List<String>?
    ) {
        val userId = authRepository.getCurrentUserId()
            ?: throw IllegalStateException("No authenticated user")
        
        Log.d(TAG, "updateFullProfile ENTRY: userId=$userId")
        
        val updates = mutableMapOf<String, Any>(FIELD_UPDATED_AT to System.currentTimeMillis())
        
        // Basic fields
        name?.let { 
            updates[FIELD_NAME] = it 
            // Also update search keywords when name changes
            val currentUsername = getUser(userId)?.username ?: ""
            updates[FIELD_SEARCH_KEYWORDS] = buildSearchKeywords(it, currentUsername)
        }
        bio?.let { updates[FIELD_BIO] = it }
        profileImageUrl?.let {
            updates[FIELD_PROFILE_IMAGE_URL] = it
            Log.d(TAG, "updateFullProfile: Including profileImageUrl (length=${it.length})")
        }
        
        // Role
        role?.let { updates[FIELD_ROLE] = roleToFirestore(it) }
        
        // Location - build location map only if at least one location field is provided
        if (state != null || district != null || village != null) {
            val locationMap = mutableMapOf<String, Any>()
            state?.let { locationMap["state"] = it }
            district?.let { locationMap["district"] = it }
            village?.let { locationMap["village"] = it }
            locationMap["country"] = "India" // Default country
            updates[FIELD_LOCATION] = locationMap
        }
        
        // Crops - stored in expertise field per schema
        crops?.let { updates[FIELD_EXPERTISE] = it }
        
        if (updates.size > 1) {
            Log.d(TAG, "updateFullProfile: Updating ${updates.size} fields: ${updates.keys}")
            try {
                withTimeout(30_000L) {
                    usersCollection.document(userId).update(updates).await()
                }
                Log.d(TAG, "updateFullProfile: SUCCESS")
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "updateFullProfile: Firestore write timed out", e)
                throw IOException("Request timed out. Check your connection and try again.", e)
            } catch (e: Exception) {
                Log.e(TAG, "updateFullProfile: Firestore write FAILED", e)
                throw IOException("Failed to update profile: ${e.message}", e)
            }
        } else {
            Log.d(TAG, "updateFullProfile: No fields to update")
        }
    }

    override suspend fun searchUsers(query: String, limit: Int): List<UserInfo> {
        if (query.isBlank()) return emptyList()
        
        val searchTerm = query.trim().lowercase()
        // Allow single character searches for live incremental search (A -> An -> Ansh)
        
        return try {
            val querySnapshot = usersCollection
                .whereArrayContains(FIELD_SEARCH_KEYWORDS, searchTerm)
                .whereEqualTo(FIELD_IS_ACTIVE, true)
                // TODO: Uncomment after creating Firestore composite index
                // Create index in Firebase Console: Collection: users, Fields: searchKeywords (Arrays), isActive (Ascending), followersCount (Descending)
                // Or use the link from the error message when it appears
                // .orderBy(FIELD_FOLLOWERS_COUNT, com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
            
            querySnapshot.documents.mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null
                    val id = doc.id
                    val name = data[FIELD_NAME] as? String ?: return@mapNotNull null
                    val username = data[FIELD_USERNAME] as? String ?: return@mapNotNull null
                    val profileImageUrl = data[FIELD_PROFILE_IMAGE_URL] as? String
                    val roleStr = data[FIELD_ROLE] as? String ?: "farmer"
                    val statusStr = data[FIELD_VERIFICATION_STATUS] as? String ?: "unverified"
                    
                    UserInfo(
                        id = id,
                        name = name,
                        username = username,
                        profileImageUrl = profileImageUrl,
                        role = firestoreToRole(roleStr),
                        verificationStatus = firestoreToVerificationStatus(statusStr)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing user document ${doc.id}", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching users", e)
            emptyList()
        }
    }
    override suspend fun isUsernameAvailable(username: String): Boolean = true
    override suspend fun getFollowers(userId: String, page: Int, pageSize: Int): List<UserInfo> = emptyList()
    override suspend fun getFollowing(userId: String, page: Int, pageSize: Int): List<UserInfo> = emptyList()
    
    override suspend fun getSuggestedUsers(limit: Int): List<UserInfo> {
        Log.d(TAG, "getSuggestedUsers: limit=$limit")
        require(limit > 0) { "Limit must be positive" }
        
        return try {
            val currentUserId = authRepository.getCurrentUserId() ?: return emptyList()
            
            // Get list of followed user IDs
            val followingSnapshot = usersCollection
                .document(currentUserId)
                .collection("following")
                .get()
                .await()
            
            val followedUserIds = followingSnapshot.documents.map { it.id }.toSet() + currentUserId
            
            Log.d(TAG, "getSuggestedUsers: currentUserId=$currentUserId, following ${followedUserIds.size - 1} users")
            
            // Query users excluding current user and followed users
            // Order by followersCount DESC, prioritize verified users
            // REQUIRES FIRESTORE INDEX:
            // Collection: users
            // Fields: isActive (Ascending), followersCount (Descending)
            // If you see an index error, Firebase will provide a direct link in logcat like:
            // https://console.firebase.google.com/v1/r/project/kissangram-19531/firestore/indexes?create_composite=...
            val querySnapshot = usersCollection
                .whereEqualTo(FIELD_IS_ACTIVE, true)
                .orderBy(FIELD_FOLLOWERS_COUNT, com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit((limit * 2).toLong()) // Fetch more to shuffle and filter
                .get()
                .await()
            
            val allUsers = querySnapshot.documents.mapNotNull { doc ->
                val userId = doc.id
                // Skip current user and already followed users
                if (userId in followedUserIds) {
                    return@mapNotNull null
                }
                
                try {
                    val data = doc.data ?: return@mapNotNull null
                    val name = data[FIELD_NAME] as? String ?: return@mapNotNull null
                    val username = data[FIELD_USERNAME] as? String ?: return@mapNotNull null
                    val profileImageUrl = data[FIELD_PROFILE_IMAGE_URL] as? String
                    val roleStr = data[FIELD_ROLE] as? String ?: "farmer"
                    val statusStr = data[FIELD_VERIFICATION_STATUS] as? String ?: "unverified"
                    
                    UserInfo(
                        id = userId,
                        name = name,
                        username = username,
                        profileImageUrl = profileImageUrl,
                        role = firestoreToRole(roleStr),
                        verificationStatus = firestoreToVerificationStatus(statusStr)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing user document ${doc.id}", e)
                    null
                }
            }
            
            // Prioritize verified users, then shuffle for randomness
            val verifiedUsers = allUsers.filter { it.verificationStatus == VerificationStatus.VERIFIED }
            val otherUsers = allUsers.filter { it.verificationStatus != VerificationStatus.VERIFIED }
            val prioritizedUsers = (verifiedUsers + otherUsers).shuffled().take(limit)
            
            Log.d(TAG, "getSuggestedUsers: returning ${prioritizedUsers.size} users")
            prioritizedUsers
        } catch (e: Exception) {
            Log.e(TAG, "getSuggestedUsers: FAILED", e)
            // If you see an index error, check logcat for the exact Firebase Console link
            // Example format: https://console.firebase.google.com/v1/r/project/kissangram-19531/firestore/indexes?create_composite=...
            // Required index: Collection: users, Fields: isActive (Ascending), followersCount (Descending)
            if (e.message?.contains("index") == true || e.message?.contains("Index") == true) {
                Log.e(TAG, "MISSING FIRESTORE INDEX for getSuggestedUsers!")
                Log.e(TAG, "Collection: users")
                Log.e(TAG, "Fields: isActive (Ascending), followersCount (Descending)")
                Log.e(TAG, "Check the error message above for the exact Firebase Console link to create this index")
            }
            emptyList()
        }
    }

    private fun generateUsername(name: String, userId: String): String {
        val base = name.trim().lowercase().replace(Regex("[^a-z0-9]"), "_").take(20)
        val suffix = userId.takeLast(6)
        return "${base}_$suffix"
    }

    private fun buildSearchKeywords(name: String, username: String): List<String> {
        val words = name.trim().lowercase().split(Regex("\\s+")).filter { it.length > 1 }
        return (words + listOf(username)).distinct()
    }

    private fun roleToFirestore(role: UserRole): String = when (role) {
        UserRole.FARMER -> "farmer"
        UserRole.EXPERT -> "expert"
        UserRole.AGRIPRENEUR -> "agripreneur"
        UserRole.INPUT_SELLER -> "input_seller"
        UserRole.AGRI_LOVER -> "agri_lover"
    }

    private fun verificationStatusToFirestore(s: VerificationStatus): String = when (s) {
        VerificationStatus.UNVERIFIED -> "unverified"
        VerificationStatus.PENDING -> "pending"
        VerificationStatus.VERIFIED -> "verified"
        VerificationStatus.REJECTED -> "rejected"
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toUser(): User {
        val id = getString(FIELD_ID) ?: id
        val roleStr = getString(FIELD_ROLE) ?: "farmer"
        val statusStr = getString(FIELD_VERIFICATION_STATUS) ?: "unverified"
        
        // Parse location map (only names, no geoPoint)
        @Suppress("UNCHECKED_CAST")
        val locationMap = get(FIELD_LOCATION) as? Map<String, Any?>
        val userLocation = locationMap?.let {
            com.kissangram.model.UserLocation(
                district = it["district"] as? String,
                state = it["state"] as? String,
                country = it["country"] as? String,
                village = it["village"] as? String
            )
        }
        
        return User(
            id = id,
            phoneNumber = getString(FIELD_PHONE_NUMBER) ?: "",
            name = getString(FIELD_NAME) ?: "",
            username = getString(FIELD_USERNAME) ?: "",
            profileImageUrl = getString(FIELD_PROFILE_IMAGE_URL),
            bio = getString(FIELD_BIO),
            role = firestoreToRole(roleStr),
            verificationStatus = firestoreToVerificationStatus(statusStr),
            location = userLocation,
            expertise = (get(FIELD_EXPERTISE) as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            followersCount = (getLong(FIELD_FOLLOWERS_COUNT) ?: 0L).toInt(),
            followingCount = (getLong(FIELD_FOLLOWING_COUNT) ?: 0L).toInt(),
            postsCount = (getLong(FIELD_POSTS_COUNT) ?: 0L).toInt(),
            language = getString(FIELD_LANGUAGE) ?: "en",
            createdAt = getLong(FIELD_CREATED_AT) ?: 0L,
            lastActiveAt = getLong(FIELD_LAST_ACTIVE_AT)
        )
    }

    private fun firestoreToRole(s: String): UserRole = when (s) {
        "expert" -> UserRole.EXPERT
        "agripreneur" -> UserRole.AGRIPRENEUR
        "input_seller" -> UserRole.INPUT_SELLER
        "agri_lover" -> UserRole.AGRI_LOVER
        else -> UserRole.FARMER
    }

    private fun firestoreToVerificationStatus(s: String): VerificationStatus = when (s) {
        "pending" -> VerificationStatus.PENDING
        "verified" -> VerificationStatus.VERIFIED
        "rejected" -> VerificationStatus.REJECTED
        else -> VerificationStatus.UNVERIFIED
    }

    companion object {
        private const val TAG = "FirestoreUserRepo"
        private const val DATABASE_NAME = "kissangram"
        private const val COLLECTION_USERS = "users"
        private const val FIELD_ID = "id"
        private const val FIELD_PHONE_NUMBER = "phoneNumber"
        private const val FIELD_NAME = "name"
        private const val FIELD_USERNAME = "username"
        private const val FIELD_PROFILE_IMAGE_URL = "profileImageUrl"
        private const val FIELD_BIO = "bio"
        private const val FIELD_ROLE = "role"
        private const val FIELD_VERIFICATION_STATUS = "verificationStatus"
        private const val FIELD_VERIFICATION_DOC_URL = "verificationDocUrl"
        private const val FIELD_VERIFICATION_SUBMITTED_AT = "verificationSubmittedAt"
        private const val FIELD_VERIFIED_AT = "verifiedAt"
        private const val FIELD_LOCATION = "location"
        private const val FIELD_EXPERTISE = "expertise"
        private const val FIELD_FOLLOWERS_COUNT = "followersCount"
        private const val FIELD_FOLLOWING_COUNT = "followingCount"
        private const val FIELD_POSTS_COUNT = "postsCount"
        private const val FIELD_LANGUAGE = "language"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_UPDATED_AT = "updatedAt"
        private const val FIELD_LAST_ACTIVE_AT = "lastActiveAt"
        private const val FIELD_SEARCH_KEYWORDS = "searchKeywords"
        private const val FIELD_IS_ACTIVE = "isActive"
    }
}
