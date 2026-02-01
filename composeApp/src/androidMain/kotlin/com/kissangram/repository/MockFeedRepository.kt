package com.kissangram.repository

import com.kissangram.model.*
import com.kissangram.repository.FeedRepository
import kotlinx.coroutines.delay

/**
 * Mock implementation of FeedRepository with dummy data for development
 */
class MockFeedRepository : FeedRepository {
    
    private val dummyPosts = createDummyPosts()
    
    override suspend fun getHomeFeed(page: Int, pageSize: Int): List<Post> {
        // Simulate network delay
        delay(500)
        
        val startIndex = page * pageSize
        val endIndex = minOf(startIndex + pageSize, dummyPosts.size)
        
        return if (startIndex < dummyPosts.size) {
            dummyPosts.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
    }
    
    override suspend fun refreshFeed(): List<Post> {
        delay(800)
        return dummyPosts.take(20)
    }
    
    private fun createDummyPosts(): List<Post> {
        val currentTime = System.currentTimeMillis()
        
        return listOf(
            Post(
                id = "post1",
                authorId = "user1",
                authorName = "Rajesh Kumar",
                authorUsername = "rajesh_farmer",
                authorProfileImageUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150",
                authorRole = UserRole.FARMER,
                authorVerificationStatus = VerificationStatus.VERIFIED,
                type = PostType.NORMAL,
                text = "My wheat crop is ready for harvest! 🌾 This season has been great with good rainfall. Expecting 40 quintals per acre this time.",
                media = listOf(
                    PostMedia(
                        url = "https://images.unsplash.com/photo-1574323347407-f5e1ad6d020b?w=800",
                        type = MediaType.IMAGE,
                        thumbnailUrl = null
                    )
                ),
                voiceCaption = null,
                crops = listOf("wheat"),
                hashtags = listOf("#wheat", "#harvest", "#punjab", "#farming"),
                location = PostLocation("Ludhiana, Punjab", 30.9010, 75.8573),
                question = null,
                likesCount = 245,
                commentsCount = 32,
                savesCount = 15,
                isLikedByMe = false,
                isSavedByMe = false,
                createdAt = currentTime - 3600000, // 1 hour ago
                updatedAt = null
            ),
            Post(
                id = "post2",
                authorId = "user2",
                authorName = "Dr. Priya Sharma",
                authorUsername = "dr_priya_agri",
                authorProfileImageUrl = "https://images.unsplash.com/photo-1594824476967-48c8b964273f?w=150",
                authorRole = UserRole.EXPERT,
                authorVerificationStatus = VerificationStatus.VERIFIED,
                type = PostType.NORMAL,
                text = "Important tip for Rabi season: Apply first irrigation 21 days after sowing wheat. This is critical for proper tillering. 💧🌱",
                media = listOf(
                    PostMedia(
                        url = "https://images.unsplash.com/photo-1625246333195-78d9c38ad449?w=800",
                        type = MediaType.IMAGE,
                        thumbnailUrl = null
                    )
                ),
                voiceCaption = null,
                crops = listOf("wheat"),
                hashtags = listOf("#wheat", "#irrigation", "#rabiseason", "#agritips"),
                location = null,
                question = null,
                likesCount = 512,
                commentsCount = 89,
                savesCount = 234,
                isLikedByMe = true,
                isSavedByMe = true,
                createdAt = currentTime - 7200000, // 2 hours ago
                updatedAt = null
            ),
            Post(
                id = "post3",
                authorId = "user3",
                authorName = "Amit Singh",
                authorUsername = "amit_organic",
                authorProfileImageUrl = "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=150",
                authorRole = UserRole.FARMER,
                authorVerificationStatus = VerificationStatus.UNVERIFIED,
                type = PostType.QUESTION,
                text = "My tomato plants are showing yellow leaves at the bottom. Is this a nutrient deficiency or some disease? Please help! 🍅",
                media = listOf(
                    PostMedia(
                        url = "https://images.unsplash.com/photo-1592841200221-a6898f307baa?w=800",
                        type = MediaType.IMAGE,
                        thumbnailUrl = null
                    )
                ),
                voiceCaption = null,
                crops = listOf("tomato"),
                hashtags = listOf("#tomato", "#plantdisease", "#help"),
                location = PostLocation("Nashik, Maharashtra", 20.0063, 73.7901),
                question = QuestionData(
                    targetExpertise = listOf("tomato", "plant_disease"),
                    targetExpertIds = listOf(),
                    targetExperts = listOf(),
                    isAnswered = false,
                    bestAnswerCommentId = null
                ),
                likesCount = 45,
                commentsCount = 12,
                savesCount = 8,
                isLikedByMe = false,
                isSavedByMe = false,
                createdAt = currentTime - 10800000, // 3 hours ago
                updatedAt = null
            ),
            Post(
                id = "post4",
                authorId = "user4",
                authorName = "Gurpreet Kaur",
                authorUsername = "gurpreet_dairy",
                authorProfileImageUrl = "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=150",
                authorRole = UserRole.AGRIPRENEUR,
                authorVerificationStatus = VerificationStatus.VERIFIED,
                type = PostType.NORMAL,
                text = "Started selling fresh milk directly to consumers. No middlemen, fair prices for farmers and quality milk for customers! 🥛🐄",
                media = listOf(
                    PostMedia(
                        url = "https://images.unsplash.com/photo-1550583724-b2692b85b150?w=800",
                        type = MediaType.IMAGE,
                        thumbnailUrl = null
                    )
                ),
                voiceCaption = null,
                crops = listOf(),
                hashtags = listOf("#dairy", "#directselling", "#farmtotable", "#agribusiness"),
                location = PostLocation("Amritsar, Punjab", 31.6340, 74.8723),
                question = null,
                likesCount = 389,
                commentsCount = 56,
                savesCount = 42,
                isLikedByMe = false,
                isSavedByMe = false,
                createdAt = currentTime - 14400000, // 4 hours ago
                updatedAt = null
            ),
            Post(
                id = "post5",
                authorId = "user5",
                authorName = "Suresh Patel",
                authorUsername = "suresh_seeds",
                authorProfileImageUrl = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150",
                authorRole = UserRole.INPUT_SELLER,
                authorVerificationStatus = VerificationStatus.VERIFIED,
                type = PostType.NORMAL,
                text = "New hybrid rice varieties now available! HD-2967 and PBW-550 - high yield and disease resistant. Visit our shop in Karnal.",
                media = listOf(
                    PostMedia(
                        url = "https://images.unsplash.com/photo-1536304993881-ff6e9eefa2a6?w=800",
                        type = MediaType.IMAGE,
                        thumbnailUrl = null
                    )
                ),
                voiceCaption = null,
                crops = listOf("rice"),
                hashtags = listOf("#rice", "#seeds", "#hybridvariety", "#karnal"),
                location = PostLocation("Karnal, Haryana", 29.6857, 76.9905),
                question = null,
                likesCount = 156,
                commentsCount = 23,
                savesCount = 67,
                isLikedByMe = false,
                isSavedByMe = false,
                createdAt = currentTime - 18000000, // 5 hours ago
                updatedAt = null
            ),
            Post(
                id = "post6",
                authorId = "user6",
                authorName = "Ramesh Yadav",
                authorUsername = "ramesh_sugarcane",
                authorProfileImageUrl = "https://images.unsplash.com/photo-1560250097-0b93528c311a?w=150",
                authorRole = UserRole.FARMER,
                authorVerificationStatus = VerificationStatus.UNVERIFIED,
                type = PostType.NORMAL,
                text = "Finally harvested my sugarcane! 80 tonnes per acre this year. Hard work pays off. Thank you to everyone who supported. 🌿",
                media = listOf(
                    PostMedia(
                        url = "https://images.unsplash.com/photo-1564517945244-26f9a7c6e4ed?w=800",
                        type = MediaType.IMAGE,
                        thumbnailUrl = null
                    )
                ),
                voiceCaption = null,
                crops = listOf("sugarcane"),
                hashtags = listOf("#sugarcane", "#harvest", "#farming", "#success"),
                location = PostLocation("Muzaffarnagar, UP", 29.4727, 77.7085),
                question = null,
                likesCount = 423,
                commentsCount = 67,
                savesCount = 28,
                isLikedByMe = true,
                isSavedByMe = false,
                createdAt = currentTime - 21600000, // 6 hours ago
                updatedAt = null
            ),
            Post(
                id = "post7",
                authorId = "user7",
                authorName = "Meena Devi",
                authorUsername = "meena_organic",
                authorProfileImageUrl = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=150",
                authorRole = UserRole.FARMER,
                authorVerificationStatus = VerificationStatus.UNVERIFIED,
                type = PostType.NORMAL,
                text = "Started vermicomposting at home. Best organic fertilizer for vegetables! Here's my setup - anyone can do this with minimal investment. 🌿♻️",
                media = listOf(
                    PostMedia(
                        url = "https://images.unsplash.com/photo-1416879595882-3373a0480b5b?w=800",
                        type = MediaType.IMAGE,
                        thumbnailUrl = null
                    )
                ),
                voiceCaption = null,
                crops = listOf(),
                hashtags = listOf("#organic", "#vermicompost", "#sustainable", "#ecofriendly"),
                location = PostLocation("Jaipur, Rajasthan", 26.9124, 75.7873),
                question = null,
                likesCount = 678,
                commentsCount = 145,
                savesCount = 312,
                isLikedByMe = false,
                isSavedByMe = true,
                createdAt = currentTime - 25200000, // 7 hours ago
                updatedAt = null
            ),
            Post(
                id = "post8",
                authorId = "user8",
                authorName = "Vikram Reddy",
                authorUsername = "vikram_cotton",
                authorProfileImageUrl = "https://images.unsplash.com/photo-1519345182560-3f2917c472ef?w=150",
                authorRole = UserRole.FARMER,
                authorVerificationStatus = VerificationStatus.VERIFIED,
                type = PostType.QUESTION,
                text = "What's the best time to apply growth regulator in cotton? My crop is 45 days old. Experts please advise! 🌱",
                media = listOf(),
                voiceCaption = null,
                crops = listOf("cotton"),
                hashtags = listOf("#cotton", "#growthregulator", "#expertadvice"),
                location = PostLocation("Warangal, Telangana", 18.0000, 79.5883),
                question = QuestionData(
                    targetExpertise = listOf("cotton"),
                    targetExpertIds = listOf(),
                    targetExperts = listOf(),
                    isAnswered = true,
                    bestAnswerCommentId = "comment123"
                ),
                likesCount = 89,
                commentsCount = 34,
                savesCount = 56,
                isLikedByMe = false,
                isSavedByMe = false,
                createdAt = currentTime - 28800000, // 8 hours ago
                updatedAt = null
            )
        )
    }
}
