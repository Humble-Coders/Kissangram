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
        // Return empty list - use FirestorePostRepository for actual data
        return []
    }
    
    func addComment(postId: String, text: String, parentCommentId: String?) async throws -> Comment {
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
            parentCommentId: parentCommentId,
            repliesCount: Int32(0),
            likesCount: Int32(0),
            isLikedByMe: false,
            isExpertAnswer: false,
            isBestAnswer: false,
            createdAt: Int64(Date().timeIntervalSince1970 * 1000)
        )
    }
    
    func deleteComment(postId: String, commentId: String, reason: String) async throws {
        try? await Task.sleep(nanoseconds: 300_000_000)
        // Mock implementation - just simulate deletion
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
    
    func createPost(postData: [String: Any], completionHandler: @escaping (Post?, Error?) -> Void) {
        // Convert [String: Any] to [String: Any?] for internal use
        let postDataWithOptionals: [String: Any?] = postData.mapValues { $0 }
        
        Task {
            do {
                try? await Task.sleep(nanoseconds: 500_000_000) // Simulate network delay
                
                let postId = "mock_post_\(Int(Date().timeIntervalSince1970 * 1000))"
                let authorId = postDataWithOptionals["authorId"] as? String ?? "mock_user"
                let authorName = postDataWithOptionals["authorName"] as? String ?? "Mock User"
                let authorUsername = postDataWithOptionals["authorUsername"] as? String ?? "mock_user"
                let authorProfileImageUrl = postDataWithOptionals["authorProfileImageUrl"] as? String
                let text = postDataWithOptionals["text"] as? String ?? ""
                
                // Parse media array
                let mediaArray = (postDataWithOptionals["media"] as? [[String: Any]])?.compactMap { mediaMap -> PostMedia? in
                    guard let url = mediaMap["url"] as? String else { return nil }
                    let typeStr = mediaMap["type"] as? String ?? "image"
                    let thumbnailUrl = mediaMap["thumbnailUrl"] as? String
                    
                    return PostMedia(
                        url: url,
                        type: typeStr == "video" ? .video : .image,
                        thumbnailUrl: thumbnailUrl?.isEmpty == false ? thumbnailUrl : nil
                    )
                } ?? []
                
                // Parse voice caption
                let voiceCaptionMap = postDataWithOptionals["voiceCaption"] as? [String: Any]
                let voiceCaption: VoiceContent? = voiceCaptionMap.flatMap { map in
                    guard let url = map["url"] as? String else { return nil }
                    let duration = (map["durationSeconds"] as? Int) ?? 0
                    return VoiceContent(url: url, durationSeconds: Int32(duration))
                }
                
                // Parse crops and hashtags
                let crops = (postDataWithOptionals["crops"] as? [String]) ?? []
                let hashtags = (postDataWithOptionals["hashtags"] as? [String]) ?? []
                
                // Parse location
                let locationMap = postDataWithOptionals["location"] as? [String: Any]
                let location: PostLocation? = locationMap.flatMap { map in
                    let name = map["name"] as? String ?? ""
                    let latitude = map["latitude"] as? Double
                    let longitude = map["longitude"] as? Double
                    return PostLocation(
                        name: name,
                        latitude: latitude != nil ? KotlinDouble(value: latitude!) : nil,
                        longitude: longitude != nil ? KotlinDouble(value: longitude!) : nil
                    )
                }
                
                // Parse question data
                let questionMap = postDataWithOptionals["question"] as? [String: Any]
                let question: QuestionData? = questionMap.flatMap { map in
                    let targetExpertise = (map["targetExpertise"] as? [String]) ?? []
                    let targetExpertIds = (map["targetExpertIds"] as? [String]) ?? []
                    return QuestionData(
                        targetExpertise: targetExpertise,
                        targetExpertIds: targetExpertIds,
                        targetExperts: [],
                        isAnswered: (map["isAnswered"] as? Bool) ?? false,
                        bestAnswerCommentId: map["bestAnswerCommentId"] as? String
                    )
                }
                
                let typeStr = postDataWithOptionals["type"] as? String ?? "normal"
                let roleStr = postDataWithOptionals["authorRole"] as? String ?? "farmer"
                let verificationStatusStr = postDataWithOptionals["authorVerificationStatus"] as? String ?? "unverified"
                
                let post = Post(
                    id: postId,
                    authorId: authorId,
                    authorName: authorName,
                    authorUsername: authorUsername,
                    authorProfileImageUrl: authorProfileImageUrl,
                    authorRole: stringToUserRole(roleStr),
                    authorVerificationStatus: stringToVerificationStatus(verificationStatusStr),
                    type: typeStr == "question" ? .question : .normal,
                    text: text,
                    media: mediaArray,
                    voiceCaption: voiceCaption,
                    crops: crops,
                    hashtags: hashtags,
                    location: location,
                    question: question,
                    likesCount: 0,
                    commentsCount: 0,
                    savesCount: 0,
                    isLikedByMe: false,
                    isSavedByMe: false,
                    createdAt: Int64(Date().timeIntervalSince1970 * 1000),
                    updatedAt: nil
                )
                
                DispatchQueue.main.async {
                    completionHandler(post, nil)
                }
            } catch {
                DispatchQueue.main.async {
                    completionHandler(nil, error)
                }
            }
        }
    }
    
    private func stringToUserRole(_ roleStr: String) -> UserRole {
        switch roleStr {
        case "expert": return .expert
        case "agripreneur": return .agripreneur
        case "input_seller": return .inputSeller
        case "agri_lover": return .agriLover
        default: return .farmer
        }
    }
    
    private func stringToVerificationStatus(_ statusStr: String) -> VerificationStatus {
        switch statusStr {
        case "pending": return .pending
        case "verified": return .verified
        case "rejected": return .rejected
        default: return .unverified
        }
    }
}
