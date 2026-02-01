import Foundation
import Shared

/**
 * Mock implementation of PostRepository with dummy data for development
 */
final class MockPostRepository: PostRepository {
    
    private var likedPosts = Set<String>()
    private var savedPosts = Set<String>()
    
    func getPost(postId: String) async throws -> Post? {
        try? await Task.sleep(nanoseconds: 300_000_000)
        return nil
    }
    
    func likePost(postId: String) async throws {
        try? await Task.sleep(nanoseconds: 200_000_000)
        likedPosts.insert(postId)
    }
    
    func unlikePost(postId: String) async throws {
        try? await Task.sleep(nanoseconds: 200_000_000)
        likedPosts.remove(postId)
    }
    
    func savePost(postId: String) async throws {
        try? await Task.sleep(nanoseconds: 200_000_000)
        savedPosts.insert(postId)
    }
    
    func unsavePost(postId: String) async throws {
        try? await Task.sleep(nanoseconds: 200_000_000)
        savedPosts.remove(postId)
    }
    
    func getComments(postId: String, page: Int32, pageSize: Int32) async throws -> [Comment] {
        try? await Task.sleep(nanoseconds: 400_000_000)
        
        let currentTime = Int64(Date().timeIntervalSince1970 * 1000)
        
        return [
            Comment(
                id: "comment1",
                postId: postId,
                authorId: "user10",
                authorName: "Dr. Sharma",
                authorUsername: "dr_sharma",
                authorProfileImageUrl: "https://images.unsplash.com/photo-1612349317150-e413f6a5b16d?w=150",
                authorRole: .expert,
                authorVerificationStatus: .verified,
                text: "Great progress! Make sure to check soil moisture before next irrigation.",
                voiceComment: nil,
                parentCommentId: nil,
                repliesCount: 2,
                likesCount: 15,
                isLikedByMe: false,
                isExpertAnswer: true,
                isBestAnswer: false,
                createdAt: currentTime - 1800000
            ),
            Comment(
                id: "comment2",
                postId: postId,
                authorId: "user11",
                authorName: "Anil Kumar",
                authorUsername: "anil_farmer",
                authorProfileImageUrl: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150",
                authorRole: .farmer,
                authorVerificationStatus: .unverified,
                text: "Which variety is this? I want to try next season.",
                voiceComment: nil,
                parentCommentId: nil,
                repliesCount: 1,
                likesCount: 5,
                isLikedByMe: true,
                isExpertAnswer: false,
                isBestAnswer: false,
                createdAt: currentTime - 3600000
            )
        ]
    }
    
    func addComment(postId: String, text: String) async throws -> Comment {
        try? await Task.sleep(nanoseconds: 500_000_000)
        
        return Comment(
            id: "comment_new_\(Int(Date().timeIntervalSince1970 * 1000))",
            postId: postId,
            authorId: "current_user",
            authorName: "You",
            authorUsername: "current_user",
            authorProfileImageUrl: nil,
            authorRole: .farmer,
            authorVerificationStatus: .unverified,
            text: text,
            voiceComment: nil,
            parentCommentId: nil,
            repliesCount: 0,
            likesCount: 0,
            isLikedByMe: false,
            isExpertAnswer: false,
            isBestAnswer: false,
            createdAt: Int64(Date().timeIntervalSince1970 * 1000)
        )
    }
    
    func getPostsByUser(userId: String, page: Int32, pageSize: Int32) async throws -> [Post] {
        try? await Task.sleep(nanoseconds: 400_000_000)
        return []
    }
    
    func getPostsByCrop(crop: String, page: Int32, pageSize: Int32) async throws -> [Post] {
        try? await Task.sleep(nanoseconds: 400_000_000)
        return []
    }
    
    func getPostsByHashtag(hashtag: String, page: Int32, pageSize: Int32) async throws -> [Post] {
        try? await Task.sleep(nanoseconds: 400_000_000)
        return []
    }
}
