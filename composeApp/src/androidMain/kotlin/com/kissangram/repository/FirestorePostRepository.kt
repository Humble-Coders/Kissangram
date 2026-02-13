package com.kissangram.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.DocumentSnapshot
import com.kissangram.model.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Firestore implementation of PostRepository.
 * Handles post creation and retrieval per FIRESTORE_SCHEMA.md.
 */
class FirestorePostRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(
        com.google.firebase.FirebaseApp.getInstance(),
        DATABASE_NAME
    ),
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : PostRepository {
    
    private val postsCollection
        get() = firestore.collection("posts")
    
    // Store last document snapshot per user for pagination
    private val lastDocumentSnapshots = mutableMapOf<String, DocumentSnapshot?>()
    
    // Store last document snapshot per post for comment pagination
    private val lastCommentSnapshots = mutableMapOf<String, DocumentSnapshot?>()
    
    override suspend fun createPost(postData: Map<String, Any?>): Post {
        Log.d(TAG, "📤 FirestorePostRepository: Starting post creation in Firestore")
        Log.d(TAG, "📤 FirestorePostRepository: Post Data Summary:")
        Log.d(TAG, "   - Author ID: ${postData["authorId"]}")
        Log.d(TAG, "   - Author Name: ${postData["authorName"]}")
        Log.d(TAG, "   - Post Type: ${postData["type"]}")
        Log.d(TAG, "   - Text: ${(postData["text"] as? String)?.take(100) ?: "none"}")
        Log.d(TAG, "   - Media Count: ${(postData["media"] as? List<*>)?.size ?: 0}")
        Log.d(TAG, "   - Crops: ${postData["crops"]}")
        Log.d(TAG, "   - Hashtags: ${postData["hashtags"]}")
        Log.d(TAG, "   - Location: ${(postData["location"] as? Map<*, *>)?.get("name") ?: "none"}")
        Log.d(TAG, "   - Voice Caption: ${if (postData["voiceCaption"] != null) "yes" else "no"}")
        Log.d(TAG, "   - Question Data: ${if (postData["question"] != null) "yes" else "no"}")
        Log.d(TAG, "📤 FirestorePostRepository: Full Post Data Map:")
        postData.forEach { (key, value) ->
            when (value) {
                is List<*> -> Log.d(TAG, "   - $key: List with ${value.size} items")
                is Map<*, *> -> Log.d(TAG, "   - $key: Map with ${value.size} entries")
                else -> Log.d(TAG, "   - $key: $value")
            }
        }
        
        // Let Firestore generate the document ID automatically
        val postRef = postsCollection.document()
        val postId = postRef.id
        Log.d(TAG, "📤 FirestorePostRepository: Generated Post ID: $postId")
        
        // Build Firestore document data
        val documentData = mutableMapOf<String, Any?>()
        documentData.putAll(postData)
        documentData["id"] = postId  // Add the generated ID to the data
        
        // Convert location to GeoPoint if coordinates exist
        val locationData = postData["location"] as? Map<*, *>
        if (locationData != null) {
            val latitude = locationData["latitude"] as? Double
            val longitude = locationData["longitude"] as? Double
            
            val firestoreLocation = mutableMapOf<String, Any?>(
                "name" to locationData["name"]
            )
            
            if (latitude != null && longitude != null) {
                firestoreLocation["geoPoint"] = GeoPoint(latitude, longitude)
            }
            
            documentData["location"] = firestoreLocation
        }
        
        // Add server timestamp for createdAt
        documentData["createdAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
        documentData["updatedAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
        
        return try {
            // Create post document using the reference
            Log.d(TAG, "📤 FirestorePostRepository: Sending document to Firestore...")
            postRef.set(documentData).await()
            
            Log.d(TAG, "✅ FirestorePostRepository: Post created successfully in Firestore")
            Log.d(TAG, "   - Post ID: $postId")
            
            // Fetch the created post to return it
            val doc = postRef.get().await()
            doc.toPost() ?: throw Exception("Failed to retrieve created post")
        } catch (e: Exception) {
            Log.e(TAG, "createPost: FAILED", e)
            throw Exception("Failed to create post: ${e.message}", e)
        }
    }
    
    override suspend fun getPost(postId: String): Post? {
        val doc = postsCollection.document(postId).get().await()
        if (!doc.exists()) return null
        
        // Check if current user has liked this post
        val currentUserId = authRepository.getCurrentUserId()
        val isLikedByMe = if (currentUserId != null) {
            checkIfLiked(postId, currentUserId)
        } else {
            false
        }
        
        return doc.toPost(isLikedByMe = isLikedByMe)
    }
    
    /**
     * Check if a single post is liked by the current user.
     */
    private suspend fun checkIfLiked(postId: String, userId: String): Boolean {
        return try {
            val likeDoc = postsCollection
                .document(postId)
                .collection("likes")
                .document(userId)
                .get()
                .await()
            likeDoc.exists()
        } catch (e: Exception) {
            Log.e(TAG, "checkIfLiked: failed for postId=$postId", e)
            false
        }
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
                        val likeDoc = postsCollection
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
            emptySet()
        }
    }
    
    override suspend fun likePost(postId: String) {
        Log.d(TAG, "likePost: postId=$postId")
        
        val currentUserId = authRepository.getCurrentUserId()
            ?: throw IllegalStateException("No authenticated user")
        
        val user = userRepository.getCurrentUser()
            ?: throw IllegalStateException("User profile not found")
        
        try {
            val likeData = mapOf(
                "id" to currentUserId,
                "name" to user.name,
                "username" to user.username,
                "profileImageUrl" to (user.profileImageUrl ?: ""),
                "role" to when (user.role) {
                    UserRole.EXPERT -> "expert"
                    UserRole.AGRIPRENEUR -> "agripreneur"
                    UserRole.INPUT_SELLER -> "input_seller"
                    UserRole.AGRI_LOVER -> "agri_lover"
                    else -> "farmer"
                },
                "verificationStatus" to when (user.verificationStatus) {
                    VerificationStatus.PENDING -> "pending"
                    VerificationStatus.VERIFIED -> "verified"
                    VerificationStatus.REJECTED -> "rejected"
                    else -> "unverified"
                },
                "likedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            
            val likeRef = postsCollection
                .document(postId)
                .collection("likes")
                .document(currentUserId)
            
            likeRef.set(likeData).await()
            Log.d(TAG, "likePost: SUCCESS - postId=$postId userId=$currentUserId")
        } catch (e: Exception) {
            Log.e(TAG, "likePost: FAILED", e)
            throw Exception("Failed to like post: ${e.message}", e)
        }
    }
    
    override suspend fun unlikePost(postId: String) {
        Log.d(TAG, "unlikePost: postId=$postId")
        
        val currentUserId = authRepository.getCurrentUserId()
            ?: throw IllegalStateException("No authenticated user")
        
        try {
            val likeRef = postsCollection
                .document(postId)
                .collection("likes")
                .document(currentUserId)
            
            likeRef.delete().await()
            Log.d(TAG, "unlikePost: SUCCESS - postId=$postId userId=$currentUserId")
        } catch (e: Exception) {
            Log.e(TAG, "unlikePost: FAILED", e)
            throw Exception("Failed to unlike post: ${e.message}", e)
        }
    }
    
    override suspend fun savePost(postId: String) {
        // TODO: Implement save functionality
        throw UnsupportedOperationException("Not yet implemented")
    }
    
    override suspend fun unsavePost(postId: String) {
        // TODO: Implement unsave functionality
        throw UnsupportedOperationException("Not yet implemented")
    }
    
    override suspend fun getComments(postId: String, page: Int, pageSize: Int): List<Comment> {
        Log.d(TAG, "getComments: postId=$postId page=$page pageSize=$pageSize")
        require(page >= 0) { "Page must be non-negative" }
        require(pageSize in 1..50) { "Page size must be between 1 and 50" }
        
        return try {
            val commentsCollection = postsCollection.document(postId).collection("comments")
            
            var query: Query = commentsCollection
                .whereEqualTo("isActive", true)
                .whereEqualTo("parentCommentId", null) // Only top-level comments
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(pageSize.toLong())
            
            if (page > 0) {
                val lastSnap = lastCommentSnapshots[postId]
                if (lastSnap == null) {
                    Log.w(TAG, "getComments: page > 0 but no cursor for postId $postId; returning empty")
                    return emptyList()
                }
                query = query.startAfter(lastSnap)
                Log.d(TAG, "getComments: using startAfter for page $page")
            } else {
                lastCommentSnapshots[postId] = null
            }
            
            Log.d(TAG, "getComments: executing query comments where postId=$postId isActive=true parentCommentId=null orderBy createdAt desc limit $pageSize")
            val snapshot = query.get().await()
            val rawCount = snapshot.documents.size
            Log.d(TAG, "getComments: snapshot.documents.size=$rawCount")
            
            // Parse all comments to get their IDs
            val commentsWithIds = snapshot.documents.mapNotNull { doc ->
                val comment = doc.toComment(isLikedByMe = false) // Temporary, will update after checking likes
                if (comment == null) Log.w(TAG, "getComments: toComment() returned null for doc ${doc.id}")
                comment
            }
            
            // Batch check which comments are liked by current user
            val currentUserId = authRepository.getCurrentUserId()
            val likedCommentIds = if (currentUserId != null && commentsWithIds.isNotEmpty()) {
                batchCheckCommentLikes(commentsWithIds.map { it.id }, postId, currentUserId)
            } else {
                emptySet()
            }
            
            // Update comments with correct isLikedByMe value
            val list = commentsWithIds.map { comment ->
                comment.copy(isLikedByMe = likedCommentIds.contains(comment.id))
            }
            
            Log.d(TAG, "getComments: parsed list.size=${list.size} (raw=$rawCount), liked=${likedCommentIds.size}")
            
            // Store last document for pagination
            if (snapshot.documents.isNotEmpty()) {
                lastCommentSnapshots[postId] = snapshot.documents.last()
            }
            
            list
        } catch (e: Exception) {
            Log.e(TAG, "getComments: FAILED", e)
            throw Exception("Failed to get comments: ${e.message}", e)
        }
    }
    
    override suspend fun addComment(postId: String, text: String, parentCommentId: String?): Comment {
        Log.d(TAG, "addComment: postId=$postId text=${text.take(50)}... parentCommentId=$parentCommentId")
        
        val currentUserId = authRepository.getCurrentUserId()
            ?: throw IllegalStateException("User must be authenticated to add comment")
        
        // Get current user info
        val user = userRepository.getCurrentUser()
            ?: throw IllegalStateException("User profile not found")
        
        val commentsCollection = postsCollection.document(postId).collection("comments")
        val commentRef = commentsCollection.document()
        val commentId = commentRef.id
        
        val commentData = mutableMapOf<String, Any?>(
            "id" to commentId,
            "postId" to postId,
            "authorId" to currentUserId,
            "authorName" to user.name,
            "authorUsername" to user.username,
            "authorProfileImageUrl" to user.profileImageUrl,
            "authorRole" to user.role.toFirestoreString(),
            "authorVerificationStatus" to verificationStatusToFirestore(user.verificationStatus),
            "text" to text,
            "parentCommentId" to parentCommentId,
            "repliesCount" to 0,
            "likesCount" to 0,
            "isExpertAnswer" to false,
            "isBestAnswer" to false,
            "isActive" to true,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        
        // Voice comment is optional, not included for now
        commentData["voiceComment"] = null
        
        return try {
            Log.d(TAG, "addComment: Creating comment document in Firestore...")
            commentRef.set(commentData).await()
            
            Log.d(TAG, "✅ addComment: Comment created successfully")
            Log.d(TAG, "   - Comment ID: $commentId")
            
            // Fetch the created comment to return it
            val doc = commentRef.get().await()
            doc.toComment(isLikedByMe = false) ?: throw Exception("Failed to retrieve created comment")
        } catch (e: Exception) {
            Log.e(TAG, "addComment: FAILED", e)
            throw Exception("Failed to add comment: ${e.message}", e)
        }
    }
    
    override suspend fun deleteComment(postId: String, commentId: String, reason: String) {
        Log.d(TAG, "deleteComment: postId=$postId commentId=$commentId reason=${reason.take(50)}...")
        
        val currentUserId = authRepository.getCurrentUserId()
            ?: throw IllegalStateException("User must be authenticated to delete comment")
        
        val commentRef = postsCollection
            .document(postId)
            .collection("comments")
            .document(commentId)
        
        // Verify the comment belongs to current user
        val commentDoc = commentRef.get().await()
        if (!commentDoc.exists()) {
            throw IllegalArgumentException("Comment not found")
        }
        
        val commentData = commentDoc.data
        val commentAuthorId = commentData?.get("authorId") as? String
        if (commentAuthorId != currentUserId) {
            throw IllegalStateException("User can only delete their own comments")
        }
        
        // Soft delete: set isActive = false and store deletion reason
        // This triggers onCommentUpdate Cloud Function which decrements counts
        val updateData = mapOf(
            "isActive" to false,
            "deletionReason" to reason
        )
        
        try {
            commentRef.update(updateData).await()
            Log.d(TAG, "✅ deleteComment: Comment soft-deleted successfully")
        } catch (e: Exception) {
            Log.e(TAG, "deleteComment: FAILED", e)
            throw Exception("Failed to delete comment: ${e.message}", e)
        }
    }
    
    /**
     * Batch check which comments are liked by the current user.
     * Uses parallel coroutines for efficient batch fetching.
     */
    private suspend fun batchCheckCommentLikes(commentIds: List<String>, postId: String, userId: String): Set<String> = coroutineScope {
        if (commentIds.isEmpty()) return@coroutineScope emptySet()
        
        try {
            // Use coroutines to check all comments in parallel
            val results = commentIds.map { commentId ->
                async {
                    try {
                        val likeDoc = postsCollection
                            .document(postId)
                            .collection("comments")
                            .document(commentId)
                            .collection("likes")
                            .document(userId)
                            .get()
                            .await()
                        if (likeDoc.exists()) commentId else null
                    } catch (e: Exception) {
                        Log.e(TAG, "batchCheckCommentLikes: failed for commentId=$commentId", e)
                        null
                    }
                }
            }
            
            results.mapNotNull { it.await() }.toSet()
        } catch (e: Exception) {
            Log.e(TAG, "batchCheckCommentLikes: FAILED", e)
            emptySet()
        }
    }
    
    /**
     * Convert Firestore DocumentSnapshot to Comment model
     */
    private fun com.google.firebase.firestore.DocumentSnapshot.toComment(isLikedByMe: Boolean = false): Comment? {
        return try {
            val data = data ?: return null
            
            val id = getString("id") ?: id
            val postId = getString("postId") ?: return null
            val authorId = getString("authorId") ?: return null
            val authorName = getString("authorName") ?: return null
            val authorUsername = getString("authorUsername") ?: return null
            val authorProfileImageUrl = getString("authorProfileImageUrl")
            val authorRoleStr = getString("authorRole") ?: "farmer"
            val authorVerificationStatusStr = getString("authorVerificationStatus") ?: "unverified"
            
            val text = getString("text") ?: ""
            
            // Parse voice comment
            val voiceCommentMap = get("voiceComment") as? Map<String, Any>
            val voiceComment = voiceCommentMap?.let {
                VoiceContent(
                    url = it["url"] as? String ?: return@let null,
                    durationSeconds = (it["durationSeconds"] as? Number)?.toInt() ?: 0
                )
            }
            
            val parentCommentId = getString("parentCommentId")
            val repliesCount = (getLong("repliesCount") ?: 0L).toInt()
            val likesCount = (getLong("likesCount") ?: 0L).toInt()
            val isExpertAnswer = getBoolean("isExpertAnswer") ?: false
            val isBestAnswer = getBoolean("isBestAnswer") ?: false
            
            val createdAt = getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
            
            Comment(
                id = id,
                postId = postId,
                authorId = authorId,
                authorName = authorName,
                authorUsername = authorUsername,
                authorProfileImageUrl = authorProfileImageUrl,
                authorRole = stringToUserRole(authorRoleStr),
                authorVerificationStatus = stringToVerificationStatus(authorVerificationStatusStr),
                text = text,
                voiceComment = voiceComment,
                parentCommentId = parentCommentId,
                repliesCount = repliesCount,
                likesCount = likesCount,
                isLikedByMe = isLikedByMe,
                isExpertAnswer = isExpertAnswer,
                isBestAnswer = isBestAnswer,
                createdAt = createdAt
            )
        } catch (e: Exception) {
            Log.e(TAG, "toComment: Failed to parse comment document", e)
            null
        }
    }
    
    override suspend fun getPostsByUser(userId: String, page: Int, pageSize: Int): List<Post> {
        Log.d(TAG, "getPostsByUser: userId=$userId page=$page pageSize=$pageSize")
        require(page >= 0) { "Page must be non-negative" }
        require(pageSize in 1..50) { "Page size must be between 1 and 50" }
        
        return try {
            var query: Query = postsCollection
                .whereEqualTo("authorId", userId)
                .whereEqualTo("isActive", true)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(pageSize.toLong())
            
            if (page > 0) {
                val lastSnap = lastDocumentSnapshots[userId]
                if (lastSnap == null) {
                    Log.w(TAG, "getPostsByUser: page > 0 but no cursor for userId $userId; returning empty")
                    return emptyList()
                }
                query = query.startAfter(lastSnap)
                Log.d(TAG, "getPostsByUser: using startAfter for page $page")
            } else {
                lastDocumentSnapshots[userId] = null
            }
            
            Log.d(TAG, "getPostsByUser: executing query posts where authorId=$userId isActive=true orderBy createdAt desc limit $pageSize")
            val snapshot = query.get().await()
            val rawCount = snapshot.documents.size
            Log.d(TAG, "getPostsByUser: snapshot.documents.size=$rawCount")
            
            // First, parse all posts to get their IDs
            val postsWithIds = snapshot.documents.mapNotNull { doc ->
                val post = doc.toPost(isLikedByMe = false) // Temporary, will update after checking likes
                if (post == null) Log.w(TAG, "getPostsByUser: toPost() returned null for doc ${doc.id}")
                post
            }
            
            // Batch check which posts are liked by current user
            val currentUserId = authRepository.getCurrentUserId()
            val likedPostIds = if (currentUserId != null && postsWithIds.isNotEmpty()) {
                batchCheckLikes(postsWithIds.map { it.id }, currentUserId)
            } else {
                emptySet()
            }
            
            // Update posts with correct isLikedByMe value
            val list = postsWithIds.map { post ->
                post.copy(isLikedByMe = likedPostIds.contains(post.id))
            }
            
            Log.d(TAG, "getPostsByUser: parsed list.size=${list.size} (raw=$rawCount), liked=${likedPostIds.size}")
            
            // Store last document for pagination
            if (snapshot.documents.isNotEmpty()) {
                lastDocumentSnapshots[userId] = snapshot.documents.last()
            }
            
            list
        } catch (e: Exception) {
            Log.e(TAG, "getPostsByUser: FAILED", e)
            throw Exception("Failed to get posts by user: ${e.message}", e)
        }
    }
    
    override suspend fun getPostsByCrop(crop: String, page: Int, pageSize: Int): List<Post> {
        // TODO: Implement get posts by crop
        throw UnsupportedOperationException("Not yet implemented")
    }
    
    override suspend fun getPostsByHashtag(hashtag: String, page: Int, pageSize: Int): List<Post> {
        // TODO: Implement get posts by hashtag
        throw UnsupportedOperationException("Not yet implemented")
    }
    
    private fun com.google.firebase.firestore.DocumentSnapshot.toPost(isLikedByMe: Boolean = false): Post? {
        return try {
            val data = data ?: return null
            
            val id = getString("id") ?: id
            val authorId = getString("authorId") ?: return null
            val authorName = getString("authorName") ?: return null
            val authorUsername = getString("authorUsername") ?: return null
            val authorProfileImageUrl = getString("authorProfileImageUrl")
            val authorRoleStr = getString("authorRole") ?: "farmer"
            val authorVerificationStatusStr = getString("authorVerificationStatus") ?: "unverified"
            
            val typeStr = getString("type") ?: "normal"
            val text = getString("text") ?: ""
            
            // Parse media array
            val mediaList = (get("media") as? List<Map<String, Any>>)?.mapNotNull { mediaMap ->
                val url = mediaMap["url"] as? String ?: return@mapNotNull null
                val typeStr = mediaMap["type"] as? String ?: "image"
                val thumbnailUrl = mediaMap["thumbnailUrl"] as? String
                
                PostMedia(
                    url = url,
                    type = when (typeStr) {
                        "video" -> MediaType.VIDEO
                        else -> MediaType.IMAGE
                    },
                    thumbnailUrl = thumbnailUrl?.takeIf { it.isNotEmpty() }
                )
            } ?: emptyList()
            
            // Parse voice caption
            val voiceCaptionMap = get("voiceCaption") as? Map<String, Any>
            val voiceCaption = voiceCaptionMap?.let {
                VoiceContent(
                    url = it["url"] as? String ?: return@let null,
                    durationSeconds = (it["durationSeconds"] as? Number)?.toInt() ?: 0
                )
            }
            
            // Parse crops and hashtags
            val crops = (get("crops") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val hashtags = (get("hashtags") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            
            // Parse location
            val locationMap = get("location") as? Map<String, Any>
            val location = locationMap?.let {
                val geoPoint = it["geoPoint"] as? GeoPoint
                PostLocation(
                    name = it["name"] as? String ?: "",
                    latitude = geoPoint?.latitude,
                    longitude = geoPoint?.longitude
                )
            }
            
            // Parse question data
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
                isSavedByMe = false, // TODO: Check from current user's saved posts
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        } catch (e: Exception) {
            Log.e(TAG, "toPost: Failed to parse post document", e)
            null
        }
    }
    
    private fun stringToUserRole(roleStr: String?): UserRole {
        return when (roleStr) {
            "expert" -> UserRole.EXPERT
            "agripreneur" -> UserRole.AGRIPRENEUR
            "input_seller" -> UserRole.INPUT_SELLER
            "agri_lover" -> UserRole.AGRI_LOVER
            else -> UserRole.FARMER
        }
    }
    
    private fun stringToVerificationStatus(statusStr: String?): VerificationStatus {
        return when (statusStr) {
            "pending" -> VerificationStatus.PENDING
            "verified" -> VerificationStatus.VERIFIED
            "rejected" -> VerificationStatus.REJECTED
            else -> VerificationStatus.UNVERIFIED
        }
    }
    
    private fun verificationStatusToFirestore(status: VerificationStatus): String {
        return when (status) {
            VerificationStatus.UNVERIFIED -> "unverified"
            VerificationStatus.PENDING -> "pending"
            VerificationStatus.VERIFIED -> "verified"
            VerificationStatus.REJECTED -> "rejected"
        }
    }
    
    companion object {
        private const val TAG = "FirestorePostRepo"
        private const val DATABASE_NAME = "kissangram"
    }
}
