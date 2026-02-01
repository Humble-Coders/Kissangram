import Foundation
import Shared

/**
 * Mock implementation of FeedRepository with dummy data for development
 */
final class MockFeedRepository: FeedRepository {
    
    private let dummyPosts: [Post]
    
    init() {
        self.dummyPosts = MockFeedRepository.createDummyPosts()
    }
    
    func getHomeFeed(page: Int32, pageSize: Int32) async throws -> [Post] {
        // Simulate network delay
        try? await Task.sleep(nanoseconds: 500_000_000)
        
        let startIndex = Int(page * pageSize)
        let endIndex = min(startIndex + Int(pageSize), dummyPosts.count)
        
        if startIndex < dummyPosts.count {
            return Array(dummyPosts[startIndex..<endIndex])
        } else {
            return []
        }
    }
    
    func refreshFeed() async throws -> [Post] {
        try? await Task.sleep(nanoseconds: 800_000_000)
        return Array(dummyPosts.prefix(20))
    }
    
    private static func createDummyPosts() -> [Post] {
        let currentTime = Int64(Date().timeIntervalSince1970 * 1000)
        
        return [
            Post(
                id: "post1",
                authorId: "user1",
                authorName: "Rajesh Kumar",
                authorUsername: "rajesh_farmer",
                authorProfileImageUrl: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150",
                authorRole: .farmer,
                authorVerificationStatus: .verified,
                type: .normal,
                text: "My wheat crop is ready for harvest! 🌾 This season has been great with good rainfall. Expecting 40 quintals per acre this time.",
                media: [
                    PostMedia(
                        url: "https://images.unsplash.com/photo-1574323347407-f5e1ad6d020b?w=800",
                        type: .image,
                        thumbnailUrl: nil
                    )
                ],
                voiceCaption: nil,
                crops: ["wheat"],
                hashtags: ["#wheat", "#harvest", "#punjab", "#farming"],
                location: PostLocation(name: "Ludhiana, Punjab", latitude: 30.9010 as KotlinDouble?, longitude: 75.8573 as KotlinDouble?),
                question: nil,
                likesCount: 245,
                commentsCount: 32,
                savesCount: 15,
                isLikedByMe: false,
                isSavedByMe: false,
                createdAt: currentTime - 3600000,
                updatedAt: nil
            ),
            Post(
                id: "post2",
                authorId: "user2",
                authorName: "Dr. Priya Sharma",
                authorUsername: "dr_priya_agri",
                authorProfileImageUrl: "https://images.unsplash.com/photo-1594824476967-48c8b964273f?w=150",
                authorRole: .expert,
                authorVerificationStatus: .verified,
                type: .normal,
                text: "Important tip for Rabi season: Apply first irrigation 21 days after sowing wheat. This is critical for proper tillering. 💧🌱",
                media: [
                    PostMedia(
                        url: "https://images.unsplash.com/photo-1625246333195-78d9c38ad449?w=800",
                        type: .image,
                        thumbnailUrl: nil
                    )
                ],
                voiceCaption: nil,
                crops: ["wheat"],
                hashtags: ["#wheat", "#irrigation", "#rabiseason", "#agritips"],
                location: nil,
                question: nil,
                likesCount: 512,
                commentsCount: 89,
                savesCount: 234,
                isLikedByMe: true,
                isSavedByMe: true,
                createdAt: currentTime - 7200000,
                updatedAt: nil
            ),
            Post(
                id: "post3",
                authorId: "user3",
                authorName: "Amit Singh",
                authorUsername: "amit_organic",
                authorProfileImageUrl: "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=150",
                authorRole: .farmer,
                authorVerificationStatus: .unverified,
                type: .question,
                text: "My tomato plants are showing yellow leaves at the bottom. Is this a nutrient deficiency or some disease? Please help! 🍅",
                media: [
                    PostMedia(
                        url: "https://images.unsplash.com/photo-1592841200221-a6898f307baa?w=800",
                        type: .image,
                        thumbnailUrl: nil
                    )
                ],
                voiceCaption: nil,
                crops: ["tomato"],
                hashtags: ["#tomato", "#plantdisease", "#help"],
                location: PostLocation(name: "Nashik, Maharashtra", latitude: 20.0063 as KotlinDouble?, longitude: 73.7901 as KotlinDouble?),
                question: QuestionData(
                    targetExpertise: ["tomato", "plant_disease"],
                    targetExpertIds: [],
                    targetExperts: [],
                    isAnswered: false,
                    bestAnswerCommentId: nil
                ),
                likesCount: 45,
                commentsCount: 12,
                savesCount: 8,
                isLikedByMe: false,
                isSavedByMe: false,
                createdAt: currentTime - 10800000,
                updatedAt: nil
            ),
            Post(
                id: "post4",
                authorId: "user4",
                authorName: "Gurpreet Kaur",
                authorUsername: "gurpreet_dairy",
                authorProfileImageUrl: "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=150",
                authorRole: .agripreneur,
                authorVerificationStatus: .verified,
                type: .normal,
                text: "Started selling fresh milk directly to consumers. No middlemen, fair prices for farmers and quality milk for customers! 🥛🐄",
                media: [
                    PostMedia(
                        url: "https://images.unsplash.com/photo-1550583724-b2692b85b150?w=800",
                        type: .image,
                        thumbnailUrl: nil
                    )
                ],
                voiceCaption: nil,
                crops: [],
                hashtags: ["#dairy", "#directselling", "#farmtotable", "#agribusiness"],
                location: PostLocation(name: "Amritsar, Punjab", latitude: 31.6340 as KotlinDouble?, longitude: 74.8723 as KotlinDouble?),
                question: nil,
                likesCount: 389,
                commentsCount: 56,
                savesCount: 42,
                isLikedByMe: false,
                isSavedByMe: false,
                createdAt: currentTime - 14400000,
                updatedAt: nil
            ),
            Post(
                id: "post5",
                authorId: "user5",
                authorName: "Suresh Patel",
                authorUsername: "suresh_seeds",
                authorProfileImageUrl: "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150",
                authorRole: .inputSeller,
                authorVerificationStatus: .verified,
                type: .normal,
                text: "New hybrid rice varieties now available! HD-2967 and PBW-550 - high yield and disease resistant. Visit our shop in Karnal.",
                media: [
                    PostMedia(
                        url: "https://images.unsplash.com/photo-1536304993881-ff6e9eefa2a6?w=800",
                        type: .image,
                        thumbnailUrl: nil
                    )
                ],
                voiceCaption: nil,
                crops: ["rice"],
                hashtags: ["#rice", "#seeds", "#hybridvariety", "#karnal"],
                location: PostLocation(name: "Karnal, Haryana", latitude: 29.6857 as KotlinDouble?, longitude: 76.9905 as KotlinDouble?),
                question: nil,
                likesCount: 156,
                commentsCount: 23,
                savesCount: 67,
                isLikedByMe: false,
                isSavedByMe: false,
                createdAt: currentTime - 18000000,
                updatedAt: nil
            )
        ]
    }
}
