import Foundation
import Shared

/**
 * Mock implementation of StoryRepository with dummy data for development
 */
final class MockStoryRepository: StoryRepository {

    func getStoryBar() async throws -> [UserStories] {
        try? await Task.sleep(nanoseconds: 400_000_000)
        
        let currentTime = Int64(Date().timeIntervalSince1970 * 1000)
        let oneDayInMillis: Int64 = 24 * 60 * 60 * 1000
        
        return [
            // Current user's story (first position)
            UserStories(
                userId: "current_user",
                userName: "Your Story",
                userProfileImageUrl: nil,
                userRole: .farmer,
                userVerificationStatus: .unverified,
                stories: [
                    Story(
                        id: "story_my_1",
                        authorId: "current_user",
                        authorName: "You",
                        authorUsername: "current_user",
                        authorProfileImageUrl: nil,
                        authorRole: .farmer,
                        authorVerificationStatus: .unverified,
                        media: StoryMedia(
                            url: "https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=800",
                            type: .image,
                            thumbnailUrl: nil
                        ),
                        textOverlay: TextOverlay(text: "Morning at the farm!", positionX: 0.5, positionY: 0.8),
                        locationName: "Punjab",
                        visibility: .public_,
                        viewsCount: 45,
                        likesCount: 12,
                        isViewedByMe: true,
                        isLikedByMe: false,
                        createdAt: currentTime - 3600000,
                        expiresAt: currentTime - 3600000 + oneDayInMillis
                    )
                ],
                hasUnviewedStories: false,
                latestStoryTime: currentTime - 3600000
            ),
            UserStories(
                userId: "user1",
                userName: "Rajesh Kumar",
                userProfileImageUrl: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150",
                userRole: .farmer,
                userVerificationStatus: .verified,
                stories: [
                    Story(
                        id: "story1",
                        authorId: "user1",
                        authorName: "Rajesh Kumar",
                        authorUsername: "rajesh_farmer",
                        authorProfileImageUrl: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150",
                        authorRole: .farmer,
                        authorVerificationStatus: .verified,
                        media: StoryMedia(
                            url: "https://images.unsplash.com/photo-1574323347407-f5e1ad6d020b?w=800",
                            type: .image,
                            thumbnailUrl: nil
                        ),
                        textOverlay: nil,
                        locationName: "Ludhiana, Punjab",
                        visibility: .public_,
                        viewsCount: 234,
                        likesCount: 45,
                        isViewedByMe: false,
                        isLikedByMe: false,
                        createdAt: currentTime - 7200000,
                        expiresAt: currentTime - 7200000 + oneDayInMillis
                    )
                ],
                hasUnviewedStories: true,
                latestStoryTime: currentTime - 7200000
            ),
            UserStories(
                userId: "user2",
                userName: "Dr. Priya",
                userProfileImageUrl: "https://images.unsplash.com/photo-1594824476967-48c8b964273f?w=150",
                userRole: .expert,
                userVerificationStatus: .verified,
                stories: [
                    Story(
                        id: "story2",
                        authorId: "user2",
                        authorName: "Dr. Priya Sharma",
                        authorUsername: "dr_priya_agri",
                        authorProfileImageUrl: "https://images.unsplash.com/photo-1594824476967-48c8b964273f?w=150",
                        authorRole: .expert,
                        authorVerificationStatus: .verified,
                        media: StoryMedia(
                            url: "https://images.unsplash.com/photo-1625246333195-78d9c38ad449?w=800",
                            type: .image,
                            thumbnailUrl: nil
                        ),
                        textOverlay: TextOverlay(text: "New research on wheat irrigation", positionX: 0.5, positionY: 0.9),
                        locationName: nil,
                        visibility: .public_,
                        viewsCount: 567,
                        likesCount: 89,
                        isViewedByMe: true,
                        isLikedByMe: true,
                        createdAt: currentTime - 10800000,
                        expiresAt: currentTime - 10800000 + oneDayInMillis
                    )
                ],
                hasUnviewedStories: false,
                latestStoryTime: currentTime - 10800000
            ),
            UserStories(
                userId: "user4",
                userName: "Gurpreet",
                userProfileImageUrl: "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=150",
                userRole: .agripreneur,
                userVerificationStatus: .verified,
                stories: [
                    Story(
                        id: "story3",
                        authorId: "user4",
                        authorName: "Gurpreet Kaur",
                        authorUsername: "gurpreet_dairy",
                        authorProfileImageUrl: "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=150",
                        authorRole: .agripreneur,
                        authorVerificationStatus: .verified,
                        media: StoryMedia(
                            url: "https://images.unsplash.com/photo-1550583724-b2692b85b150?w=800",
                            type: .image,
                            thumbnailUrl: nil
                        ),
                        textOverlay: TextOverlay(text: "Fresh delivery today! 🥛", positionX: 0.5, positionY: 0.85),
                        locationName: "Amritsar",
                        visibility: .public_,
                        viewsCount: 189,
                        likesCount: 34,
                        isViewedByMe: false,
                        isLikedByMe: false,
                        createdAt: currentTime - 14400000,
                        expiresAt: currentTime - 14400000 + oneDayInMillis
                    )
                ],
                hasUnviewedStories: true,
                latestStoryTime: currentTime - 14400000
            )
        ]
    }
    
    func getStoriesForUser(userId: String) async throws -> [Story] {
        try? await Task.sleep(nanoseconds: 300_000_000)
        return []
    }
    
    func markStoryAsViewed(storyId: String) async throws {
        try? await Task.sleep(nanoseconds: 100_000_000)
    }
    
    func getMyStories() async throws -> [Story] {
        try? await Task.sleep(nanoseconds: 300_000_000)
        return []
    }
    
    // CHANGED: Parameter type from [String: Any?] to [String: Any]
    // Kotlin's Map<String, Any?> maps to Swift's [String: Any] in KMM
    func createStory(storyData: [String: Any]) async throws -> Story {
        try? await Task.sleep(nanoseconds: 500_000_000)
        
        // Mock implementation - return a dummy story
        let currentTime = Int64(Date().timeIntervalSince1970 * 1000)
        let oneDayInMillis: Int64 = 24 * 60 * 60 * 1000
        
        // Extract visibility from storyData
        let visibilityStr = storyData["visibility"] as? String ?? "public"
        let visibility: Shared.PostVisibility = visibilityStr == "followers" ? .followers : .public_
        
        // Extract media data
        let mediaMap = storyData["media"] as? [String: Any] ?? [:]
        let mediaUrl = mediaMap["url"] as? String ?? ""
        let mediaTypeStr = mediaMap["type"] as? String ?? "image"
        let mediaType: Shared.MediaType = mediaTypeStr == "video" ? .video : .image
        let thumbnailUrl = mediaMap["thumbnailUrl"] as? String
        
        return Story(
            id: "story_\(currentTime)",
            authorId: storyData["authorId"] as? String ?? "unknown",
            authorName: storyData["authorName"] as? String ?? "Unknown",
            authorUsername: storyData["authorUsername"] as? String ?? "unknown",
            authorProfileImageUrl: storyData["authorProfileImageUrl"] as? String,
            authorRole: parseUserRole(from: storyData["authorRole"] as? String),
            authorVerificationStatus: parseVerificationStatus(from: storyData["authorVerificationStatus"] as? String),
            media: StoryMedia(
                url: mediaUrl,
                type: mediaType,
                thumbnailUrl: thumbnailUrl
            ),
            textOverlay: nil,
            locationName: storyData["locationName"] as? String,
            visibility: visibility,
            viewsCount: 0,
            likesCount: 0,
            isViewedByMe: false,
            isLikedByMe: false,
            createdAt: currentTime,
            expiresAt: currentTime + oneDayInMillis
        )
    }
    
    // MARK: - Helper Methods
    
    private func parseUserRole(from roleStr: String?) -> UserRole {
        switch roleStr {
        case "farmer": return .farmer
        case "expert": return .expert
        case "agripreneur": return .agripreneur
        case "input_seller": return .inputSeller
        case "agri_lover": return .agriLover
        default: return .farmer
        }
    }
    
    private func parseVerificationStatus(from statusStr: String?) -> VerificationStatus {
        switch statusStr {
        case "verified": return .verified
        case "pending": return .pending
        case "rejected": return .rejected
        default: return .unverified
        }
    }
}
