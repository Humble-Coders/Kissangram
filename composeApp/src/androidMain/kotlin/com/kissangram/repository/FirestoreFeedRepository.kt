package com.kissangram.repository

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import com.kissangram.model.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Firestore implementation of FeedRepository.
 * Reads from users/{currentUserId}/feed (fan-out by onPostCreate Cloud Function).
 * Uses cursor-based pagination; stateful for load-more (stores last snapshot).
 */
class FirestoreFeedRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(
        com.google.firebase.FirebaseApp.getInstance(),
        DATABASE_NAME
    ),
    private val authRepository: AuthRepository
) : FeedRepository {

    private var lastDocumentSnapshot: DocumentSnapshot? = null

    override suspend fun getHomeFeed(page: Int, pageSize: Int): List<Post> {
        Log.d(TAG, "getHomeFeed: page=$page pageSize=$pageSize")
        val currentUserId = authRepository.getCurrentUserId()
        if (currentUserId == null) {
            Log.w(TAG, "getHomeFeed: currentUserId is null, returning empty list")
            return emptyList()
        }
        Log.d(TAG, "getHomeFeed: currentUserId=${currentUserId.take(8)}...")
        require(page >= 0) { "Page must be non-negative" }
        require(pageSize in 1..50) { "Page size must be between 1 and 50" }

        return try {
            var query: Query = firestore
                .collection("users")
                .document(currentUserId)
                .collection("feed")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(pageSize.toLong())

            if (page > 0) {
                val lastSnap = lastDocumentSnapshot
                if (lastSnap == null) {
                    Log.w(TAG, "getHomeFeed: page > 0 but no cursor; returning empty")
                    return emptyList()
                }
                query = query.startAfter(lastSnap)
                Log.d(TAG, "getHomeFeed: using startAfter for page $page")
            } else {
                lastDocumentSnapshot = null
            }

            Log.d(TAG, "getHomeFeed: executing query users/$currentUserId/feed orderBy createdAt desc limit $pageSize")
            val snapshot = query.get().await()
            val rawCount = snapshot.documents.size
            Log.d(TAG, "getHomeFeed: snapshot.documents.size=$rawCount")
            
            // First, parse all posts to get their IDs
            val postsWithIds = snapshot.documents.mapNotNull { doc ->
                val post = doc.toPost(isLikedByMe = false) // Temporary, will update after checking likes
                if (post == null) Log.w(TAG, "getHomeFeed: toPost() returned null for doc ${doc.id}")
                post
            }
            
            // Batch check which posts are liked by current user
            val likedPostIds = if (postsWithIds.isNotEmpty()) {
                batchCheckLikes(postsWithIds.map { it.id }, currentUserId)
            } else {
                emptySet()
            }
            
            // Update posts with correct isLikedByMe value
            val list = postsWithIds.map { post ->
                post.copy(isLikedByMe = likedPostIds.contains(post.id))
            }
            
            Log.d(TAG, "getHomeFeed: parsed list.size=${list.size} (raw=$rawCount), liked=${likedPostIds.size}")
            if (snapshot.documents.isNotEmpty()) {
                lastDocumentSnapshot = snapshot.documents.last()
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "getHomeFeed failed: page=$page", e)
            throw e
        }
    }

    override suspend fun refreshFeed(): List<Post> {
        Log.d(TAG, "refreshFeed: clearing cursor, fetching page 0")
        lastDocumentSnapshot = null
        return getHomeFeed(0, DEFAULT_PAGE_SIZE)
    }

    /**
     * Batch check which posts are liked by the current user.
     * Uses parallel coroutines for efficient batch fetching.
     */
    private suspend fun batchCheckLikes(postIds: List<String>, userId: String): Set<String> = coroutineScope {
        if (postIds.isEmpty()) return@coroutineScope emptySet()
        
        try {
            // Use coroutines to check all posts in parallel
            val results = postIds.map { postId ->
                async {
                    try {
                        val likeDoc = firestore
                            .collection("posts")
                            .document(postId)
                            .collection("likes")
                            .document(userId)
                            .get()
                            .await()
                        if (likeDoc.exists()) postId else null
                    } catch (e: Exception) {
                        Log.e(TAG, "batchCheckLikes: failed for postId=$postId", e)
                        null
                    }
                }
            }
            
            // Wait for all checks to complete and collect results
            val likedPostIds = results.mapNotNull { it.await() }.toSet()
            
            Log.d(TAG, "batchCheckLikes: checked ${postIds.size} posts, found ${likedPostIds.size} liked")
            likedPostIds
        } catch (e: Exception) {
            Log.e(TAG, "batchCheckLikes: failed", e)
            emptySet() // Return empty set on error, posts will show as not liked
        }
    }

    private fun DocumentSnapshot.toPost(isLikedByMe: Boolean = false): Post? {
        return try {
            val data = data ?: return null
            val id = getString("id") ?: this.id
            val authorId = getString("authorId") ?: return null
            val authorName = getString("authorName") ?: return null
            val authorUsername = getString("authorUsername") ?: return null
            val authorProfileImageUrl = getString("authorProfileImageUrl")
            val authorRoleStr = getString("authorRole") ?: "farmer"
            val authorVerificationStatusStr = getString("authorVerificationStatus") ?: "unverified"
            val typeStr = getString("type") ?: "normal"
            val text = getString("text") ?: ""

            val mediaList = (get("media") as? List<Map<String, Any>>)?.mapNotNull { mediaMap ->
                val url = mediaMap["url"] as? String ?: return@mapNotNull null
                val typeStrMedia = mediaMap["type"] as? String ?: "image"
                val thumbnailUrl = mediaMap["thumbnailUrl"] as? String
                PostMedia(
                    url = url,
                    type = when (typeStrMedia) {
                        "video" -> MediaType.VIDEO
                        else -> MediaType.IMAGE
                    },
                    thumbnailUrl = thumbnailUrl?.takeIf { it.isNotEmpty() }
                )
            } ?: emptyList()

            val voiceCaptionMap = get("voiceCaption") as? Map<String, Any>
            val voiceCaption = voiceCaptionMap?.let {
                VoiceContent(
                    url = it["url"] as? String ?: return@let null,
                    durationSeconds = (it["durationSeconds"] as? Number)?.toInt() ?: 0
                )
            }

            val crops = (get("crops") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val hashtags = (get("hashtags") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

            val locationMap = get("location") as? Map<String, Any>
            val location = locationMap?.let {
                val geoPoint = it["geoPoint"] as? GeoPoint
                PostLocation(
                    name = it["name"] as? String ?: "",
                    latitude = geoPoint?.latitude,
                    longitude = geoPoint?.longitude
                )
            }

            val questionMap = get("question") as? Map<String, Any>
            val question = questionMap?.let {
                QuestionData(
                    targetExpertise = (it["targetExpertise"] as? List<*>)?.mapNotNull { e -> e as? String } ?: emptyList(),
                    targetExpertIds = (it["targetExpertIds"] as? List<*>)?.mapNotNull { e -> e as? String } ?: emptyList(),
                    targetExperts = (it["targetExperts"] as? List<*>)?.mapNotNull { e ->
                        val expertMap = e as? Map<String, Any>
                        expertMap?.let {
                            UserInfo(
                                id = it["id"] as? String ?: "",
                                name = it["name"] as? String ?: "",
                                username = it["username"] as? String ?: "",
                                profileImageUrl = it["profileImageUrl"] as? String,
                                role = stringToUserRole(it["role"] as? String),
                                verificationStatus = stringToVerificationStatus(it["verificationStatus"] as? String)
                            )
                        }
                    } ?: emptyList(),
                    isAnswered = it["isAnswered"] as? Boolean ?: false,
                    bestAnswerCommentId = it["bestAnswerCommentId"] as? String
                )
            }

            val likesCount = (getLong("likesCount") ?: 0L).toInt()
            val commentsCount = (getLong("commentsCount") ?: 0L).toInt()
            val savesCount = (getLong("savesCount") ?: 0L).toInt()
            val createdAt = getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
            val updatedAt = getTimestamp("updatedAt")?.toDate()?.time

            Post(
                id = id,
                authorId = authorId,
                authorName = authorName,
                authorUsername = authorUsername,
                authorProfileImageUrl = authorProfileImageUrl,
                authorRole = stringToUserRole(authorRoleStr),
                authorVerificationStatus = stringToVerificationStatus(authorVerificationStatusStr),
                type = when (typeStr) {
                    "question" -> PostType.QUESTION
                    else -> PostType.NORMAL
                },
                text = text,
                media = mediaList,
                voiceCaption = voiceCaption,
                crops = crops,
                hashtags = hashtags,
                location = location,
                question = question,
                likesCount = likesCount,
                commentsCount = commentsCount,
                savesCount = savesCount,
                isLikedByMe = isLikedByMe,
                isSavedByMe = false, // TODO: Check saved posts similarly
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        } catch (e: Exception) {
            Log.e(TAG, "toPost: Failed to parse feed document", e)
            null
        }
    }

    private fun stringToUserRole(roleStr: String?): UserRole = when (roleStr) {
        "expert" -> UserRole.EXPERT
        "agripreneur" -> UserRole.AGRIPRENEUR
        "input_seller" -> UserRole.INPUT_SELLER
        "agri_lover" -> UserRole.AGRI_LOVER
        else -> UserRole.FARMER
    }

    private fun stringToVerificationStatus(statusStr: String?): VerificationStatus = when (statusStr) {
        "pending" -> VerificationStatus.PENDING
        "verified" -> VerificationStatus.VERIFIED
        "rejected" -> VerificationStatus.REJECTED
        else -> VerificationStatus.UNVERIFIED
    }

    companion object {
        private const val TAG = "FirestoreFeedRepo"
        private const val DATABASE_NAME = "kissangram"
        private const val DEFAULT_PAGE_SIZE = 20
    }
}
