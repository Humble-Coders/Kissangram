package com.kissangram.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.kissangram.model.*
import kotlinx.coroutines.tasks.await

/**
 * Firestore implementation of PostRepository.
 * Handles post creation and retrieval per FIRESTORE_SCHEMA.md.
 */
class FirestorePostRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(
        com.google.firebase.FirebaseApp.getInstance(),
        DATABASE_NAME
    )
) : PostRepository {
    
    private val postsCollection
        get() = firestore.collection("posts")
    
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
        return doc.toPost()
    }
    
    override suspend fun likePost(postId: String) {
        // TODO: Implement like functionality
        throw UnsupportedOperationException("Not yet implemented")
    }
    
    override suspend fun unlikePost(postId: String) {
        // TODO: Implement unlike functionality
        throw UnsupportedOperationException("Not yet implemented")
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
        // TODO: Implement get comments
        throw UnsupportedOperationException("Not yet implemented")
    }
    
    override suspend fun addComment(postId: String, text: String): Comment {
        // TODO: Implement add comment
        throw UnsupportedOperationException("Not yet implemented")
    }
    
    override suspend fun getPostsByUser(userId: String, page: Int, pageSize: Int): List<Post> {
        // TODO: Implement get posts by user
        throw UnsupportedOperationException("Not yet implemented")
    }
    
    override suspend fun getPostsByCrop(crop: String, page: Int, pageSize: Int): List<Post> {
        // TODO: Implement get posts by crop
        throw UnsupportedOperationException("Not yet implemented")
    }
    
    override suspend fun getPostsByHashtag(hashtag: String, page: Int, pageSize: Int): List<Post> {
        // TODO: Implement get posts by hashtag
        throw UnsupportedOperationException("Not yet implemented")
    }
    
    private fun com.google.firebase.firestore.DocumentSnapshot.toPost(): Post? {
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
                isLikedByMe = false, // TODO: Check from current user's liked posts
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
    
    companion object {
        private const val TAG = "FirestorePostRepo"
        private const val DATABASE_NAME = "kissangram"
    }
}
