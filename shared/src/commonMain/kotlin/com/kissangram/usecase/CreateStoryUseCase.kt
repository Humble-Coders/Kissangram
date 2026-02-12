package com.kissangram.usecase

import com.kissangram.model.*
import com.kissangram.repository.AuthRepository
import com.kissangram.repository.StoryRepository
import com.kissangram.repository.StorageRepository
import com.kissangram.repository.UserRepository

/**
 * Use case for creating a new story.
 * Handles media uploads to Cloudinary and story creation in Firestore.
 */
class CreateStoryUseCase(
    private val storageRepository: StorageRepository,
    private val storyRepository: StoryRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) {
    /**
     * Create a new story.
     * @param input The story input data from the UI
     * @return The created Story object
     * @throws IllegalStateException if no user is authenticated
     * @throws IllegalArgumentException if validation fails
     * @throws Exception if upload or creation fails
     */
    @Throws(Exception::class)
    suspend operator fun invoke(input: CreateStoryInput): Story {
        // 1. Validate input
        validateInput(input)
        
        // 2. Get current user
        val userId = authRepository.getCurrentUserId()
            ?: throw IllegalStateException("No authenticated user")
        
        val user = userRepository.getCurrentUser()
            ?: throw IllegalStateException("User profile not found")
        
        // 3. Upload media to Cloudinary
        println("📤 CreateStoryUseCase: Starting media upload to Cloudinary")
        val mediaData = input.mediaData
            ?: throw IllegalArgumentException("Story must have media")
        val mediaType = input.mediaType
            ?: throw IllegalArgumentException("Story must have media type")
        
        println("📤 CreateStoryUseCase: Uploading story media:")
        println("   - Media Data Size: ${mediaData.size} bytes")
        println("   - Type: $mediaType")
        println("   - Thumbnail Data Size: ${input.thumbnailData?.size ?: 0} bytes")
        
        val uploadResult = storageRepository.uploadPostMediaToCloudinary(
            mediaData = mediaData,
            mediaType = mediaType,
            thumbnailData = input.thumbnailData
        )
        
        println("✅ CreateStoryUseCase: Media uploaded successfully")
        println("   - Media URL: ${uploadResult.mediaUrl}")
        println("   - Thumbnail URL: ${uploadResult.thumbnailUrl ?: "none"}")
        
        // Validate upload result
        if (uploadResult.mediaUrl.isBlank()) {
            throw Exception("Media upload failed: empty media URL returned from Cloudinary")
        }
        
        // 4. Build location data (per FIRESTORE_SCHEMA.md: location: { name: "..." })
        val locationData = input.location?.let { loc ->
            mapOf(
                "name" to loc.name
                // Note: latitude/longitude not in schema, but can be added if needed
            )
        }
        
        // 5. Convert PostVisibility to Firestore format
        val visibilityStr = when (input.visibility) {
            PostVisibility.PUBLIC -> "public"
            PostVisibility.FOLLOWERS -> "followers"
        }
        
        // 6. Convert UserRole and VerificationStatus to Firestore format
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
        
        // 7. Build text overlay data (per FIRESTORE_SCHEMA.md: textOverlay: { text: "...", position: { x, y } })
        val textOverlayData = input.textOverlays.firstOrNull()?.let { overlay ->
            mapOf(
                "text" to overlay.text,
                "position" to mapOf(
                    "x" to overlay.positionX,
                    "y" to overlay.positionY
                )
            )
        }
        
        // 8. Build story data map for Firestore (matching FIRESTORE_SCHEMA.md exactly)
        val storyData = mutableMapOf<String, Any?>(
            "authorId" to userId,
            "authorName" to user.name,
            "authorUsername" to user.username,
            "authorProfileImageUrl" to user.profileImageUrl,
            "authorRole" to roleStr,
            "authorVerificationStatus" to verificationStatusStr,
            "media" to mapOf(
                "url" to uploadResult.mediaUrl,
                "type" to when (mediaType) {
                    MediaType.IMAGE -> "image"
                    MediaType.VIDEO -> "video"
                },
                "thumbnailUrl" to (uploadResult.thumbnailUrl ?: "")
            ),
            "visibility" to visibilityStr,
            "viewsCount" to 0,
            "likesCount" to 0,
            "isActive" to true
        )
        
        // Add optional fields (matching schema structure)
        textOverlayData?.let { storyData["textOverlay"] = it }
        locationData?.let { storyData["location"] = it }
        
        // Note: createdAt will be set by Firestore server timestamp
        // expiresAt will be calculated as createdAt + 24 hours in the repository
        
        // 9. Log story data before sending to Firestore
        println("📤 CreateStoryUseCase: Preparing to create story in Firestore")
        println("📤 CreateStoryUseCase: Story Data Summary:")
        println("   - Author ID: $userId")
        println("   - Author Name: ${user.name}")
        println("   - Author Username: ${user.username}")
        println("   - Visibility: $visibilityStr")
        println("   - Media URL: ${uploadResult.mediaUrl}")
        println("   - Location: ${locationData?.get("name") ?: "none"}")
        println("   - Text Overlay: ${if (textOverlayData != null) "yes" else "no"}")
        println("📤 CreateStoryUseCase: Story data map size: ${storyData.size} fields")
        
        // 10. Create story in Firestore
        println("📤 CreateStoryUseCase: Calling storyRepository.createStory()")
        return try {
            val story = storyRepository.createStory(storyData)
            println("✅ CreateStoryUseCase: Story created successfully in Firestore")
            println("   - Story ID: ${story.id}")
            story
        } catch (e: Exception) {
            println("❌ CreateStoryUseCase: Failed to create story in Firestore")
            println("   - Error: ${e.message}")
            e.printStackTrace()
            throw Exception("Failed to create story in Firestore: ${e.message}", e)
        }
    }
    
    private fun validateInput(input: CreateStoryInput) {
        if (input.mediaData == null) {
            throw IllegalArgumentException("Story must have media")
        }
        if (input.mediaType == null) {
            throw IllegalArgumentException("Story must have media type")
        }
    }
}
