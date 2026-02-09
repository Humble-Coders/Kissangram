package com.kissangram.repository

import com.kissangram.model.*
import com.kissangram.repository.StoryRepository
import kotlinx.coroutines.delay

/**
 * Mock implementation of StoryRepository with dummy data for development
 */
class MockStoryRepository : StoryRepository {
    
    override suspend fun getStoryBar(): List<UserStories> {
        delay(400)
        
        val currentTime = System.currentTimeMillis()
        val oneDayInMillis = 24 * 60 * 60 * 1000L
        
        return listOf(
            // Current user's story (first position)
            UserStories(
                userId = "current_user",
                userName = "Your Story",
                userProfileImageUrl = null,
                userRole = UserRole.FARMER,
                userVerificationStatus = VerificationStatus.UNVERIFIED,
                stories = listOf(
                    Story(
                        id = "story_my_1",
                        authorId = "current_user",
                        authorName = "You",
                        authorUsername = "current_user",
                        authorProfileImageUrl = null,
                        authorRole = UserRole.FARMER,
                        authorVerificationStatus = VerificationStatus.UNVERIFIED,
                        media = StoryMedia(
                            url = "https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=800",
                            type = MediaType.IMAGE,
                            thumbnailUrl = null
                        ),
                        textOverlay = TextOverlay("Morning at the farm!", 0.5f, 0.8f),
                        locationName = "Punjab",
                        visibility = PostVisibility.PUBLIC,
                        viewsCount = 45,
                        likesCount = 12,
                        isViewedByMe = true,
                        isLikedByMe = false,
                        createdAt = currentTime - 3600000,
                        expiresAt = currentTime - 3600000 + oneDayInMillis
                    )
                ),
                hasUnviewedStories = false,
                latestStoryTime = currentTime - 3600000
            ),
            UserStories(
                userId = "user1",
                userName = "Rajesh Kumar",
                userProfileImageUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150",
                userRole = UserRole.FARMER,
                userVerificationStatus = VerificationStatus.VERIFIED,
                stories = listOf(
                    Story(
                        id = "story1",
                        authorId = "user1",
                        authorName = "Rajesh Kumar",
                        authorUsername = "rajesh_farmer",
                        authorProfileImageUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150",
                        authorRole = UserRole.FARMER,
                        authorVerificationStatus = VerificationStatus.VERIFIED,
                        media = StoryMedia(
                            url = "https://images.unsplash.com/photo-1574323347407-f5e1ad6d020b?w=800",
                            type = MediaType.IMAGE,
                            thumbnailUrl = null
                        ),
                        textOverlay = null,
                        locationName = "Ludhiana, Punjab",
                        visibility = PostVisibility.PUBLIC,
                        viewsCount = 234,
                        likesCount = 45,
                        isViewedByMe = false,
                        isLikedByMe = false,
                        createdAt = currentTime - 7200000,
                        expiresAt = currentTime - 7200000 + oneDayInMillis
                    )
                ),
                hasUnviewedStories = true,
                latestStoryTime = currentTime - 7200000
            ),
            UserStories(
                userId = "user2",
                userName = "Dr. Priya",
                userProfileImageUrl = "https://images.unsplash.com/photo-1594824476967-48c8b964273f?w=150",
                userRole = UserRole.EXPERT,
                userVerificationStatus = VerificationStatus.VERIFIED,
                stories = listOf(
                    Story(
                        id = "story2",
                        authorId = "user2",
                        authorName = "Dr. Priya Sharma",
                        authorUsername = "dr_priya_agri",
                        authorProfileImageUrl = "https://images.unsplash.com/photo-1594824476967-48c8b964273f?w=150",
                        authorRole = UserRole.EXPERT,
                        authorVerificationStatus = VerificationStatus.VERIFIED,
                        media = StoryMedia(
                            url = "https://images.unsplash.com/photo-1625246333195-78d9c38ad449?w=800",
                            type = MediaType.IMAGE,
                            thumbnailUrl = null
                        ),
                        textOverlay = TextOverlay("New research on wheat irrigation", 0.5f, 0.9f),
                        locationName = null,
                        visibility = PostVisibility.PUBLIC,
                        viewsCount = 567,
                        likesCount = 89,
                        isViewedByMe = true,
                        isLikedByMe = true,
                        createdAt = currentTime - 10800000,
                        expiresAt = currentTime - 10800000 + oneDayInMillis
                    )
                ),
                hasUnviewedStories = false,
                latestStoryTime = currentTime - 10800000
            ),
            UserStories(
                userId = "user4",
                userName = "Gurpreet",
                userProfileImageUrl = "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=150",
                userRole = UserRole.AGRIPRENEUR,
                userVerificationStatus = VerificationStatus.VERIFIED,
                stories = listOf(
                    Story(
                        id = "story3",
                        authorId = "user4",
                        authorName = "Gurpreet Kaur",
                        authorUsername = "gurpreet_dairy",
                        authorProfileImageUrl = "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=150",
                        authorRole = UserRole.AGRIPRENEUR,
                        authorVerificationStatus = VerificationStatus.VERIFIED,
                        media = StoryMedia(
                            url = "https://images.unsplash.com/photo-1550583724-b2692b85b150?w=800",
                            type = MediaType.IMAGE,
                            thumbnailUrl = null
                        ),
                        textOverlay = TextOverlay("Fresh delivery today! 🥛", 0.5f, 0.85f),
                        locationName = "Amritsar",
                        visibility = PostVisibility.PUBLIC,
                        viewsCount = 189,
                        likesCount = 34,
                        isViewedByMe = false,
                        isLikedByMe = false,
                        createdAt = currentTime - 14400000,
                        expiresAt = currentTime - 14400000 + oneDayInMillis
                    )
                ),
                hasUnviewedStories = true,
                latestStoryTime = currentTime - 14400000
            ),
            UserStories(
                userId = "user6",
                userName = "Ramesh",
                userProfileImageUrl = "https://images.unsplash.com/photo-1560250097-0b93528c311a?w=150",
                userRole = UserRole.FARMER,
                userVerificationStatus = VerificationStatus.UNVERIFIED,
                stories = listOf(
                    Story(
                        id = "story4",
                        authorId = "user6",
                        authorName = "Ramesh Yadav",
                        authorUsername = "ramesh_sugarcane",
                        authorProfileImageUrl = "https://images.unsplash.com/photo-1560250097-0b93528c311a?w=150",
                        authorRole = UserRole.FARMER,
                        authorVerificationStatus = VerificationStatus.UNVERIFIED,
                        media = StoryMedia(
                            url = "https://images.unsplash.com/photo-1564517945244-26f9a7c6e4ed?w=800",
                            type = MediaType.IMAGE,
                            thumbnailUrl = null
                        ),
                        textOverlay = null,
                        locationName = "Muzaffarnagar",
                        visibility = PostVisibility.PUBLIC,
                        viewsCount = 98,
                        likesCount = 23,
                        isViewedByMe = true,
                        isLikedByMe = true,
                        createdAt = currentTime - 18000000,
                        expiresAt = currentTime - 18000000 + oneDayInMillis
                    )
                ),
                hasUnviewedStories = false,
                latestStoryTime = currentTime - 18000000
            )
        )
    }
    
    override suspend fun getStoriesForUser(userId: String): List<Story> {
        delay(300)
        return emptyList()
    }
    
    override suspend fun markStoryAsViewed(storyId: String) {
        delay(100)
        // Mark as viewed in real implementation
    }
    
    override suspend fun getMyStories(): List<Story> {
        delay(300)
        return emptyList()
    }
    
    override suspend fun createStory(storyData: Map<String, Any?>): Story {
        delay(500)
        // Mock implementation - return a dummy story
        val currentTime = System.currentTimeMillis()
        val oneDayInMillis = 24 * 60 * 60 * 1000L
        
        return Story(
            id = "story_${System.currentTimeMillis()}",
            authorId = storyData["authorId"] as? String ?: "unknown",
            authorName = storyData["authorName"] as? String ?: "Unknown",
            authorUsername = storyData["authorUsername"] as? String ?: "unknown",
            authorProfileImageUrl = storyData["authorProfileImageUrl"] as? String,
            authorRole = when (storyData["authorRole"] as? String) {
                "farmer" -> UserRole.FARMER
                "expert" -> UserRole.EXPERT
                "agripreneur" -> UserRole.AGRIPRENEUR
                "input_seller" -> UserRole.INPUT_SELLER
                "agri_lover" -> UserRole.AGRI_LOVER
                else -> UserRole.FARMER
            },
            authorVerificationStatus = when (storyData["authorVerificationStatus"] as? String) {
                "verified" -> VerificationStatus.VERIFIED
                "pending" -> VerificationStatus.PENDING
                "rejected" -> VerificationStatus.REJECTED
                else -> VerificationStatus.UNVERIFIED
            },
            media = (storyData["media"] as? Map<*, *>)?.let { mediaMap ->
                StoryMedia(
                    url = mediaMap["url"] as? String ?: "",
                    type = when (mediaMap["type"] as? String) {
                        "video" -> MediaType.VIDEO
                        else -> MediaType.IMAGE
                    },
                    thumbnailUrl = mediaMap["thumbnailUrl"] as? String
                )
            } ?: StoryMedia("", MediaType.IMAGE, null),
            textOverlay = null,
            locationName = storyData["locationName"] as? String,
            visibility = when (storyData["visibility"] as? String) {
                "followers" -> PostVisibility.FOLLOWERS
                else -> PostVisibility.PUBLIC
            },
            viewsCount = 0,
            likesCount = 0,
            isViewedByMe = false,
            isLikedByMe = false,
            createdAt = currentTime,
            expiresAt = currentTime + oneDayInMillis
        )
    }
}
