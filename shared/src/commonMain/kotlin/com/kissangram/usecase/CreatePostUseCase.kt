package com.kissangram.usecase

import com.kissangram.model.*
import com.kissangram.repository.AuthRepository
import com.kissangram.repository.PostRepository
import com.kissangram.repository.StorageRepository
import com.kissangram.repository.UserRepository

/**
 * Use case for creating a new post.
 * Handles media uploads to Cloudinary and post creation in Firestore.
 */
class CreatePostUseCase(
    private val storageRepository: StorageRepository,
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) {
    /**
     * Create a new post.
     * @param input The post input data from the UI
     * @return The created Post object
     * @throws IllegalStateException if no user is authenticated
     * @throws IllegalArgumentException if validation fails
     * @throws Exception if upload or creation fails
     */
    @Throws(Exception::class)
    suspend operator fun invoke(input: CreatePostInput): Post {
        // 1. Validate input
        validateInput(input)
        
        // 2. Get current user
        val userId = authRepository.getCurrentUserId()
            ?: throw IllegalStateException("No authenticated user")
        
        val user = userRepository.getCurrentUser()
            ?: throw IllegalStateException("User profile not found")
        
        // 3. Upload media items to Cloudinary
        println("📤 CreatePostUseCase: Starting media uploads to Cloudinary")
        println("📤 CreatePostUseCase: Total media items: ${input.mediaItems.size}")
        input.mediaItems.forEachIndexed { index, mediaItem ->
            println("📤 CreatePostUseCase: Uploading media item $index:")
            println("   - Media Data Size: ${mediaItem.mediaData.size} bytes")
            println("   - Type: ${mediaItem.type}")
            println("   - Thumbnail Data Size: ${mediaItem.thumbnailData?.size ?: 0} bytes")
        }
        
        val uploadedMedia = mutableListOf<PostMedia>()
        input.mediaItems.forEachIndexed { index, mediaItem ->
            val result = storageRepository.uploadPostMediaToCloudinary(
                mediaData = mediaItem.mediaData,
                mediaType = mediaItem.type,
                thumbnailData = mediaItem.thumbnailData
            )
            println("✅ CreatePostUseCase: Media item $index uploaded successfully")
            println("   - Media URL: ${result.mediaUrl}")
            println("   - Thumbnail URL: ${result.thumbnailUrl ?: "none"}")
            uploadedMedia.add(
                PostMedia(
                    url = result.mediaUrl,
                    type = mediaItem.type,
                    thumbnailUrl = result.thumbnailUrl
                )
            )
        }
        
        // 5. Upload voice caption if exists
        val voiceCaptionUrl = input.voiceCaptionData?.let { audioData ->
            println("📤 CreatePostUseCase: Uploading voice caption to Cloudinary")
            println("   - Audio Data Size: ${audioData.size} bytes")
            println("   - Duration: ${input.voiceCaptionDurationSeconds} seconds")
            val url = storageRepository.uploadVoiceCaptionToCloudinary(audioData)
            println("✅ CreatePostUseCase: Voice caption uploaded successfully")
            println("   - Voice URL: $url")
            url
        } ?: run {
            println("ℹ️ CreatePostUseCase: No voice caption to upload")
            null
        }
        
        val voiceCaption = voiceCaptionUrl?.let { url ->
            VoiceContent(
                url = url,
                durationSeconds = input.voiceCaptionDurationSeconds
            )
        }
        
        // 6. Build location data
        val locationData = input.location?.let { loc ->
            mapOf(
                "name" to loc.name,
                "latitude" to loc.latitude,
                "longitude" to loc.longitude
            )
        }
        
        // 7. Build question data if type is QUESTION
        val questionData = if (input.type == PostType.QUESTION && input.targetExpertise.isNotEmpty()) {
            mapOf(
                "targetExpertise" to input.targetExpertise,
                "targetExpertIds" to emptyList<String>(), // Will be populated by Cloud Function
                "targetExperts" to emptyList<Map<String, Any>>(), // Will be populated by Cloud Function
                "isAnswered" to false,
                "bestAnswerCommentId" to null
            )
        } else null
        
        // 8. Convert PostType and PostVisibility to Firestore format
        val postTypeStr = when (input.type) {
            PostType.NORMAL -> "normal"
            PostType.QUESTION -> "question"
        }
        
        val visibilityStr = when (input.visibility) {
            PostVisibility.PUBLIC -> "public"
            PostVisibility.FOLLOWERS -> "followers"
        }
        
        // 9. Convert UserRole and VerificationStatus to Firestore format
        val roleStr = when (user.role) {
            UserRole.FARMER -> "farmer"
            UserRole.EXPERT -> "expert"
            UserRole.AGRIPRENEUR -> "agripreneur"
            UserRole.INPUT_SELLER -> "input_seller"
            UserRole.AGRI_LOVER -> "agri_lover"
        }
        
        val verificationStatusStr = when (user.verificationStatus) {
            VerificationStatus.UNVERIFIED -> "unverified"
            VerificationStatus.PENDING -> "pending"
            VerificationStatus.VERIFIED -> "verified"
            VerificationStatus.REJECTED -> "rejected"
        }
        
        // 10. Build media array for Firestore
        val mediaArray = uploadedMedia.map { media ->
            mapOf(
                "url" to media.url,
                "type" to when (media.type) {
                    MediaType.IMAGE -> "image"
                    MediaType.VIDEO -> "video"
                },
                "thumbnailUrl" to (media.thumbnailUrl ?: "")
            )
        }
        
        // 11. Build voice caption map for Firestore
        val voiceCaptionMap = voiceCaption?.let {
            mapOf(
                "url" to it.url,
                "durationSeconds" to it.durationSeconds
            )
        }
        
        // 12. Build post data map for Firestore
        // Note: Post ID will be generated by Firestore repository
        val postData = mutableMapOf<String, Any?>(
            "authorId" to userId,
            "authorName" to user.name,
            "authorUsername" to user.username,
            "authorProfileImageUrl" to user.profileImageUrl,
            "authorRole" to roleStr,
            "authorVerificationStatus" to verificationStatusStr,
            "type" to postTypeStr,
            "text" to input.text,
            "media" to mediaArray,
            "crops" to input.crops,
            "hashtags" to input.hashtags,
            "visibility" to visibilityStr,
            "likesCount" to 0,
            "commentsCount" to 0,
            "savesCount" to 0,
            "isActive" to true
        )
        
        // Add optional fields
        voiceCaptionMap?.let { postData["voiceCaption"] = it }
        locationData?.let { postData["location"] = it }
        questionData?.let { postData["question"] = it }
        
        // Note: createdAt and updatedAt will be set by Firestore server timestamp
        // Note: GeoPoint for location will be handled in platform implementations
        
        // 13. Log post data before sending to Firestore
        println("📤 CreatePostUseCase: Preparing to create post in Firestore")
        println("📤 CreatePostUseCase: Post Data Summary:")
        println("   - Author ID: $userId")
        println("   - Author Name: ${user.name}")
        println("   - Author Username: ${user.username}")
        println("   - Post Type: $postTypeStr")
        println("   - Visibility: $visibilityStr")
        println("   - Text: ${input.text.take(100)}${if (input.text.length > 100) "..." else ""}")
        println("   - Media Count: ${mediaArray.size}")
        println("   - Crops: ${input.crops}")
        println("   - Hashtags: ${input.hashtags}")
        println("   - Location: ${locationData?.get("name") ?: "none"}")
        println("   - Voice Caption: ${if (voiceCaptionMap != null) "yes" else "no"}")
        println("   - Question Data: ${if (questionData != null) "yes" else "no"}")
        println("📤 CreatePostUseCase: Full Post Data Map:")
        postData.forEach { (key, value) ->
            when (value) {
                is List<*> -> println("   - $key: List with ${value.size} items")
                is Map<*, *> -> println("   - $key: Map with ${value.size} entries")
                else -> println("   - $key: $value")
            }
        }
        
        // 14. Create post in Firestore
        return postRepository.createPost(postData)
    }
    
    private fun validateInput(input: CreatePostInput) {
        when (input.type) {
            PostType.QUESTION -> {
                if (input.text.isBlank()) {
                    throw IllegalArgumentException("Question text cannot be empty")
                }
            }
            PostType.NORMAL -> {
                if (input.mediaItems.isEmpty()) {
                    throw IllegalArgumentException("Normal post must have at least one media item")
                }
                if (input.text.isBlank()) {
                    throw IllegalArgumentException("Post caption cannot be empty")
                }
            }
        }
        
        if (input.mediaItems.size > 10) {
            throw IllegalArgumentException("Maximum 10 media items allowed")
        }
        
        if (input.text.length > 2000) {
            throw IllegalArgumentException("Post text cannot exceed 2000 characters")
        }
    }
}
