package com.kissangram.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.kissangram.model.*
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Firestore implementation of StoryRepository.
 * Handles story creation and retrieval per FIRESTORE_SCHEMA.md.
 */
class FirestoreStoryRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(
        com.google.firebase.FirebaseApp.getInstance(),
        DATABASE_NAME
    )
) : StoryRepository {
    
    private val storiesCollection
        get() = firestore.collection("stories")
    
    override suspend fun createStory(storyData: Map<String, Any?>): Story {
        Log.d(TAG, "📤 FirestoreStoryRepository: Starting story creation in Firestore")
        Log.d(TAG, "📤 FirestoreStoryRepository: Story Data Summary:")
        Log.d(TAG, "   - Author ID: ${storyData["authorId"]}")
        Log.d(TAG, "   - Author Name: ${storyData["authorName"]}")
        Log.d(TAG, "   - Visibility: ${storyData["visibility"]}")
        Log.d(TAG, "   - Media URL: ${(storyData["media"] as? Map<*, *>)?.get("url")}")
        Log.d(TAG, "   - Location: ${(storyData["location"] as? Map<*, *>)?.get("name") ?: "none"}")
        Log.d(TAG, "   - Text Overlay: ${if (storyData["textOverlay"] != null) "yes" else "no"}")
        
        // Let Firestore generate the document ID automatically
        val storyRef = storiesCollection.document()
        val storyId = storyRef.id
        Log.d(TAG, "📤 FirestoreStoryRepository: Generated Story ID: $storyId")
        
        // Build Firestore document data
        val documentData = mutableMapOf<String, Any?>()
        documentData.putAll(storyData)
        documentData["id"] = storyId  // Add the generated ID to the data
        
        // Add server timestamp for createdAt
        documentData["createdAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
        
        // Calculate expiresAt as createdAt + 24 hours
        // We'll set this after getting the actual createdAt timestamp
        // For now, we'll use a client-side estimate (Cloud Function will set it properly later)
        val estimatedExpiresAt = Date(System.currentTimeMillis() + (24 * 60 * 60 * 1000L))
        documentData["expiresAt"] = Timestamp(estimatedExpiresAt)
        
        return try {
            // Create story document
            Log.d(TAG, "📤 FirestoreStoryRepository: Sending document to Firestore...")
            storyRef.set(documentData).await()
            
            Log.d(TAG, "✅ FirestoreStoryRepository: Story created successfully in Firestore")
            Log.d(TAG, "   - Story ID: $storyId")
            
            // Fetch the created story to return it
            val doc = storyRef.get().await()
            doc.toStory() ?: throw Exception("Failed to retrieve created story")
        } catch (e: Exception) {
            Log.e(TAG, "createStory: FAILED", e)
            throw Exception("Failed to create story: ${e.message}", e)
        }
    }
    
    override suspend fun getStoryBar(): List<UserStories> {
        // TODO: Implement story bar retrieval
        // Query stories from followed users, group by author
        throw UnsupportedOperationException("Not yet implemented")
    }
    
    override suspend fun getStoriesForUser(userId: String): List<Story> {
        // TODO: Implement get stories for user
        throw UnsupportedOperationException("Not yet implemented")
    }
    
    override suspend fun markStoryAsViewed(storyId: String) {
        // TODO: Implement mark story as viewed
        throw UnsupportedOperationException("Not yet implemented")
    }
    
    override suspend fun getMyStories(): List<Story> {
        // TODO: Implement get my stories
        throw UnsupportedOperationException("Not yet implemented")
    }
    
    /**
     * Extension function to convert Firestore DocumentSnapshot to Story model
     */
    private fun com.google.firebase.firestore.DocumentSnapshot.toStory(): Story? {
        return try {
            val id = getString("id") ?: this.id
            val authorId = getString("authorId") ?: return null
            val authorName = getString("authorName") ?: return null
            val authorUsername = getString("authorUsername") ?: return null
            val authorProfileImageUrl = getString("authorProfileImageUrl")
            val authorRoleStr = getString("authorRole") ?: "farmer"
            val authorVerificationStatusStr = getString("authorVerificationStatus") ?: "unverified"
            
            // Parse media
            val mediaMap = get("media") as? Map<String, Any> ?: return null
            val mediaUrl = mediaMap["url"] as? String ?: return null
            val mediaTypeStr = mediaMap["type"] as? String ?: "image"
            val mediaType = when (mediaTypeStr) {
                "video" -> MediaType.VIDEO
                else -> MediaType.IMAGE
            }
            val thumbnailUrl = mediaMap["thumbnailUrl"] as? String
            
            val storyMedia = StoryMedia(
                url = mediaUrl,
                type = mediaType,
                thumbnailUrl = thumbnailUrl
            )
            
            // Parse text overlay (optional)
            val textOverlayMap = get("textOverlay") as? Map<String, Any>
            val textOverlay = textOverlayMap?.let {
                val text = it["text"] as? String ?: return@let null
                val positionMap = it["position"] as? Map<String, Any>
                val positionX = (positionMap?.get("x") as? Number)?.toFloat() ?: return@let null
                val positionY = (positionMap?.get("y") as? Number)?.toFloat() ?: return@let null
                TextOverlay(
                    text = text,
                    positionX = positionX,
                    positionY = positionY
                )
            }
            
            // Parse location (optional) - per schema: location: { name: "..." }
            val locationMap = get("location") as? Map<String, Any>
            val locationName = locationMap?.get("name") as? String
            
            // Parse visibility
            val visibilityStr = getString("visibility") ?: "public"
            val visibility = when (visibilityStr) {
                "followers" -> PostVisibility.FOLLOWERS
                else -> PostVisibility.PUBLIC
            }
            
            val viewsCount = (getLong("viewsCount") ?: 0L).toInt()
            val likesCount = (getLong("likesCount") ?: 0L).toInt()
            
            val createdAt = getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
            val expiresAt = getTimestamp("expiresAt")?.toDate()?.time ?: (createdAt + 24 * 60 * 60 * 1000L)
            
            Story(
                id = id,
                authorId = authorId,
                authorName = authorName,
                authorUsername = authorUsername,
                authorProfileImageUrl = authorProfileImageUrl,
                authorRole = stringToUserRole(authorRoleStr),
                authorVerificationStatus = stringToVerificationStatus(authorVerificationStatusStr),
                media = storyMedia,
                textOverlay = textOverlay,
                locationName = locationName,
                visibility = visibility,
                viewsCount = viewsCount,
                likesCount = likesCount,
                isViewedByMe = false, // TODO: Check from views subcollection
                isLikedByMe = false, // TODO: Check from likes subcollection
                createdAt = createdAt,
                expiresAt = expiresAt
            )
        } catch (e: Exception) {
            Log.e(TAG, "toStory: Failed to parse story document", e)
            null
        }
    }
    
    private fun stringToUserRole(roleStr: String): UserRole {
        return when (roleStr) {
            "expert" -> UserRole.EXPERT
            "agripreneur" -> UserRole.AGRIPRENEUR
            "input_seller" -> UserRole.INPUT_SELLER
            "agri_lover" -> UserRole.AGRI_LOVER
            else -> UserRole.FARMER
        }
    }
    
    private fun stringToVerificationStatus(statusStr: String): VerificationStatus {
        return when (statusStr) {
            "pending" -> VerificationStatus.PENDING
            "verified" -> VerificationStatus.VERIFIED
            "rejected" -> VerificationStatus.REJECTED
            else -> VerificationStatus.UNVERIFIED
        }
    }
    
    companion object {
        private const val TAG = "FirestoreStoryRepo"
        private const val DATABASE_NAME = "kissangram"
    }
}
