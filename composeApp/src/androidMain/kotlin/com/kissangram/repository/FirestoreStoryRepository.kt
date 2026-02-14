package com.kissangram.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.kissangram.model.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.google.firebase.firestore.Query
import java.util.Date

/**
 * Firestore implementation of StoryRepository.
 * Handles story creation and retrieval per FIRESTORE_SCHEMA.md.
 */
class FirestoreStoryRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(
        com.google.firebase.FirebaseApp.getInstance(),
        DATABASE_NAME
    ),
    private val authRepository: AuthRepository
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
        Log.d(TAG, "getStoryBar: start")
        val currentUserId = authRepository.getCurrentUserId()
        if (currentUserId == null) {
            Log.w(TAG, "getStoryBar: currentUserId is null, returning empty list")
            return emptyList()
        }
        Log.d(TAG, "getStoryBar: currentUserId=${currentUserId.take(8)}...")
        
        return try {
            // Query storiesFeed subcollection ordered by createdAt DESC
            val storiesFeedRef = firestore
                .collection("users")
                .document(currentUserId)
                .collection("storiesFeed")
                .orderBy("createdAt", Query.Direction.DESCENDING)
            
            val snapshot = storiesFeedRef.get().await()
            val rawCount = snapshot.documents.size
            Log.d(TAG, "getStoryBar: fetched $rawCount story documents")
            
            if (rawCount == 0) {
                return emptyList()
            }
            
            val currentTime = System.currentTimeMillis()
            
            // Filter expired stories and parse to Story objects
            val stories = snapshot.documents
                .mapNotNull { doc ->
                    try {
                        // Check if story is expired
                        val expiresAt = doc.getTimestamp("expiresAt")?.toDate()?.time
                        if (expiresAt != null && expiresAt < currentTime) {
                            Log.d(TAG, "getStoryBar: skipping expired story ${doc.id}")
                            return@mapNotNull null
                        }
                        
                        doc.toStory()
                    } catch (e: Exception) {
                        Log.e(TAG, "getStoryBar: failed to parse story ${doc.id}", e)
                        null
                    }
                }
            
            Log.d(TAG, "getStoryBar: parsed ${stories.size} valid stories (after filtering expired)")
            
            if (stories.isEmpty()) {
                return emptyList()
            }
            
            // Batch check views and likes
            val storyIds = stories.map { it.id }
            val viewedStoryIds = batchCheckViews(storyIds, currentUserId)
            val likedStoryIds = batchCheckLikes(storyIds, currentUserId)
            
            // Update stories with view/like status
            val storiesWithStatus = stories.map { story ->
                story.copy(
                    isViewedByMe = viewedStoryIds.contains(story.id),
                    isLikedByMe = likedStoryIds.contains(story.id)
                )
            }
            
            // Group stories by authorId
            val storiesByAuthor = storiesWithStatus.groupBy { it.authorId }
            Log.d(TAG, "getStoryBar: grouped into ${storiesByAuthor.size} authors")
            
            // Create UserStories objects
            val userStoriesList = storiesByAuthor.map { (authorId, authorStories) ->
                val firstStory = authorStories.first()
                val sortedStories = authorStories.sortedByDescending { it.createdAt }
                val hasUnviewedStories = authorStories.any { !it.isViewedByMe }
                val latestStoryTime = authorStories.maxOfOrNull { it.createdAt } ?: 0L
                
                UserStories(
                    userId = authorId,
                    userName = firstStory.authorName,
                    userProfileImageUrl = firstStory.authorProfileImageUrl,
                    userRole = firstStory.authorRole,
                    userVerificationStatus = firstStory.authorVerificationStatus,
                    stories = sortedStories,
                    hasUnviewedStories = hasUnviewedStories,
                    latestStoryTime = latestStoryTime
                )
            }
            
            // Sort by latestStoryTime DESC
            val sortedUserStories = userStoriesList.sortedByDescending { it.latestStoryTime }
            
            Log.d(TAG, "getStoryBar: returning ${sortedUserStories.size} user stories")
            sortedUserStories
            
        } catch (e: Exception) {
            Log.e(TAG, "getStoryBar: failed", e)
            emptyList() // Return empty list on error
        }
    }
    
    /**
     * Batch check which stories are viewed by the current user.
     */
    private suspend fun batchCheckViews(storyIds: List<String>, userId: String): Set<String> = coroutineScope {
        if (storyIds.isEmpty()) return@coroutineScope emptySet()
        
        try {
            val results = storyIds.map { storyId ->
                async {
                    try {
                        val viewDoc = firestore
                            .collection("stories")
                            .document(storyId)
                            .collection("views")
                            .document(userId)
                            .get()
                            .await()
                        if (viewDoc.exists()) storyId else null
                    } catch (e: Exception) {
                        Log.e(TAG, "batchCheckViews: failed for storyId=$storyId", e)
                        null
                    }
                }
            }
            
            val viewedStoryIds = results.mapNotNull { it.await() }.toSet()
            Log.d(TAG, "batchCheckViews: checked ${storyIds.size} stories, found ${viewedStoryIds.size} viewed")
            viewedStoryIds
        } catch (e: Exception) {
            Log.e(TAG, "batchCheckViews: failed", e)
            emptySet()
        }
    }
    
    /**
     * Batch check which stories are liked by the current user.
     */
    private suspend fun batchCheckLikes(storyIds: List<String>, userId: String): Set<String> = coroutineScope {
        if (storyIds.isEmpty()) return@coroutineScope emptySet()
        
        try {
            val results = storyIds.map { storyId ->
                async {
                    try {
                        val likeDoc = firestore
                            .collection("stories")
                            .document(storyId)
                            .collection("likes")
                            .document(userId)
                            .get()
                            .await()
                        if (likeDoc.exists()) storyId else null
                    } catch (e: Exception) {
                        Log.e(TAG, "batchCheckLikes: failed for storyId=$storyId", e)
                        null
                    }
                }
            }
            
            val likedStoryIds = results.mapNotNull { it.await() }.toSet()
            Log.d(TAG, "batchCheckLikes: checked ${storyIds.size} stories, found ${likedStoryIds.size} liked")
            likedStoryIds
        } catch (e: Exception) {
            Log.e(TAG, "batchCheckLikes: failed", e)
            emptySet()
        }
    }
    
    override suspend fun getStoriesForUser(userId: String): List<Story> {
        // TODO: Implement get stories for user
        throw UnsupportedOperationException("Not yet implemented")
    }
    
    override suspend fun markStoryAsViewed(storyId: String) {
        val currentUserId = authRepository.getCurrentUserId()
        if (currentUserId == null) {
            Log.w(TAG, "markStoryAsViewed: currentUserId is null")
            return
        }
        
        try {
            // Check if already viewed to avoid duplicate writes
            val viewDocRef = firestore
                .collection("stories")
                .document(storyId)
                .collection("views")
                .document(currentUserId)
            
            val existingView = viewDocRef.get().await()
            if (existingView.exists()) {
                Log.d(TAG, "markStoryAsViewed: story $storyId already viewed by user")
                return
            }
            
            // Create view document
            val viewData = mapOf(
                "id" to currentUserId,
                "viewedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            
            viewDocRef.set(viewData).await()
            
            // Increment viewsCount on the story document
            firestore
                .collection("stories")
                .document(storyId)
                .update("viewsCount", com.google.firebase.firestore.FieldValue.increment(1))
                .await()
            
            Log.d(TAG, "markStoryAsViewed: marked story $storyId as viewed")
        } catch (e: Exception) {
            Log.e(TAG, "markStoryAsViewed: failed for storyId=$storyId", e)
            throw Exception("Failed to mark story as viewed: ${e.message}", e)
        }
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
