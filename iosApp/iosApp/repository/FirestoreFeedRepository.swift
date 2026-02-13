import Foundation
import os.log
import FirebaseFirestore
import Shared

/// Firestore implementation of FeedRepository.
/// Reads from users/{currentUserId}/feed (fan-out by onPostCreate Cloud Function).
final class FirestoreFeedRepository: FeedRepository {

    private static let log = Logger(subsystem: "com.kissangram", category: "FirestoreFeedRepo")
    private let firestore = Firestore.firestore(database: "kissangram")
    private let authRepository: AuthRepository
    private var lastDocumentSnapshot: DocumentSnapshot?

    init(authRepository: AuthRepository) {
        self.authRepository = authRepository
    }

    func getHomeFeed(page: Int32, pageSize: Int32) async throws -> [Post] {
        Self.log.debug("getHomeFeed: page=\(page) pageSize=\(pageSize)")
        guard let currentUserId = try await authRepository.getCurrentUserId() else {
            Self.log.warning("getHomeFeed: currentUserId is nil, returning empty")
            return []
        }
        let uidPrefix = String(currentUserId.prefix(8))
        Self.log.debug("getHomeFeed: currentUserId=\(uidPrefix)...")
        guard page >= 0 else { return [] }
        guard pageSize >= 1, pageSize <= 50 else { return [] }

        let feedRef = firestore.collection("users").document(currentUserId).collection("feed")
        var query: Query = feedRef.order(by: "createdAt", descending: true).limit(to: Int(pageSize))

        if page > 0 {
            guard let lastSnap = lastDocumentSnapshot else {
                Self.log.warning("getHomeFeed: page>0 but no cursor, returning empty")
                return []
            }
            query = query.start(afterDocument: lastSnap)
            Self.log.debug("getHomeFeed: using startAfter for page \(page)")
        } else {
            lastDocumentSnapshot = nil
        }

        Self.log.debug("getHomeFeed: executing query users/\(uidPrefix).../feed orderBy createdAt desc limit \(pageSize)")
        let snapshot = try await query.getDocuments()
        let rawCount = snapshot.documents.count
        Self.log.debug("getHomeFeed: snapshot.documents.count=\(rawCount)")
        
        // First, parse all posts to get their IDs
        let postsWithIds = snapshot.documents.compactMap { doc -> Post? in
            let post = toPost(from: doc, isLikedByMe: false) // Temporary, will update after checking likes
            if post == nil { Self.log.warning("getHomeFeed: toPost returned nil for doc \(doc.documentID)") }
            return post
        }
        
        // Batch check which posts are liked by current user
        let likedPostIds = if !postsWithIds.isEmpty {
            try await batchCheckLikes(postIds: postsWithIds.map { $0.id }, userId: currentUserId)
        } else {
            Set<String>()
        }
        
        // Update posts with correct isLikedByMe value
        let posts = postsWithIds.map { post in
            Post(
                id: post.id,
                authorId: post.authorId,
                authorName: post.authorName,
                authorUsername: post.authorUsername,
                authorProfileImageUrl: post.authorProfileImageUrl,
                authorRole: post.authorRole,
                authorVerificationStatus: post.authorVerificationStatus,
                type: post.type,
                text: post.text,
                media: post.media,
                voiceCaption: post.voiceCaption,
                crops: post.crops,
                hashtags: post.hashtags,
                location: post.location,
                question: post.question,
                likesCount: post.likesCount,
                commentsCount: post.commentsCount,
                savesCount: post.savesCount,
                isLikedByMe: likedPostIds.contains(post.id),
                isSavedByMe: post.isSavedByMe,
                createdAt: post.createdAt,
                updatedAt: post.updatedAt
            )
        }
        
        Self.log.debug("getHomeFeed: parsed list.count=\(posts.count) raw=\(rawCount), liked=\(likedPostIds.count)")
        if let last = snapshot.documents.last {
            lastDocumentSnapshot = last
        }
        return posts
    }

    func refreshFeed() async throws -> [Post] {
        Self.log.debug("refreshFeed: clearing cursor, fetching page 0")
        lastDocumentSnapshot = nil
        return try await getHomeFeed(page: 0, pageSize: 20)
    }
    
    // MARK: - Batch Check Likes
    
    /// Batch check which posts are liked by the current user.
    /// Uses parallel async/await for efficient batch fetching.
    private func batchCheckLikes(postIds: [String], userId: String) async throws -> Set<String> {
        if postIds.isEmpty { return Set<String>() }
        
        return try await withThrowingTaskGroup(of: (String, Bool).self) { group in
            var likedPostIds = Set<String>()
            
            // Create tasks to check each post
            for postId in postIds {
                group.addTask {
                    let likeDoc = try await self.firestore
                        .collection("posts")
                        .document(postId)
                        .collection("likes")
                        .document(userId)
                        .getDocument()
                    return (postId, likeDoc.exists)
                }
            }
            
            // Collect results
            for try await (postId, exists) in group {
                if exists {
                    likedPostIds.insert(postId)
                }
            }
            
            Self.log.debug("batchCheckLikes: checked \(postIds.count) posts, found \(likedPostIds.count) liked")
            return likedPostIds
        }
    }

    // MARK: - toPost (same shape as post document in feed)

    private func toPost(from doc: DocumentSnapshot, isLikedByMe: Bool = false) -> Post? {
        guard let data = doc.data() else { return nil }

        let id = (doc.get("id") as? String) ?? doc.documentID
            let authorId = (doc.get("authorId") as? String) ?? ""
            let authorName = (doc.get("authorName") as? String) ?? ""
            let authorUsername = (doc.get("authorUsername") as? String) ?? ""
            let authorProfileImageUrl = doc.get("authorProfileImageUrl") as? String
            let authorRoleStr = (doc.get("authorRole") as? String) ?? "farmer"
            let authorVerificationStatusStr = (doc.get("authorVerificationStatus") as? String) ?? "unverified"

            let typeStr = (doc.get("type") as? String) ?? "normal"
            let text = (doc.get("text") as? String) ?? ""

            let mediaArray = (data["media"] as? [[String: Any]])?.compactMap { mediaMap -> PostMedia? in
                guard let url = mediaMap["url"] as? String else { return nil }
                let typeStr = mediaMap["type"] as? String ?? "image"
                let thumbnailUrl = mediaMap["thumbnailUrl"] as? String
                return PostMedia(
                    url: url,
                    type: typeStr == "video" ? .video : .image,
                    thumbnailUrl: thumbnailUrl?.isEmpty == false ? thumbnailUrl : nil
                )
            } ?? []

            let voiceCaptionMap = data["voiceCaption"] as? [String: Any]
            let voiceCaption: VoiceContent? = voiceCaptionMap.flatMap { map in
                guard let url = map["url"] as? String else { return nil }
                let duration = (map["durationSeconds"] as? Int) ?? 0
                return VoiceContent(url: url, durationSeconds: Int32(duration))
            }

            let crops = (data["crops"] as? [String]) ?? []
            let hashtags = (data["hashtags"] as? [String]) ?? []

            let locationMap = data["location"] as? [String: Any]
            let location: PostLocation? = locationMap.flatMap { map in
                let name = map["name"] as? String ?? ""
                let geoPoint = map["geoPoint"] as? GeoPoint
                let latitude = geoPoint?.latitude
                let longitude = geoPoint?.longitude
                return PostLocation(
                    name: name,
                    latitude: latitude != nil ? KotlinDouble(value: latitude!) : nil,
                    longitude: longitude != nil ? KotlinDouble(value: longitude!) : nil
                )
            }

            let questionMap = data["question"] as? [String: Any]
            let question: QuestionData? = questionMap.flatMap { map in
                let targetExpertise = (map["targetExpertise"] as? [String]) ?? []
                let targetExpertIds = (map["targetExpertIds"] as? [String]) ?? []
                let targetExperts = (map["targetExperts"] as? [[String: Any]])?.compactMap { expertMap -> UserInfo? in
                    guard let id = expertMap["id"] as? String,
                          let name = expertMap["name"] as? String,
                          let username = expertMap["username"] as? String else { return nil }
                    let profileImageUrl = expertMap["profileImageUrl"] as? String
                    let roleStr = expertMap["role"] as? String ?? "farmer"
                    let statusStr = expertMap["verificationStatus"] as? String ?? "unverified"
                    return UserInfo(
                        id: id,
                        name: name,
                        username: username,
                        profileImageUrl: profileImageUrl,
                        role: stringToUserRole(roleStr),
                        verificationStatus: stringToVerificationStatus(statusStr)
                    )
                } ?? []
                return QuestionData(
                    targetExpertise: targetExpertise,
                    targetExpertIds: targetExpertIds,
                    targetExperts: targetExperts,
                    isAnswered: (map["isAnswered"] as? Bool) ?? false,
                    bestAnswerCommentId: map["bestAnswerCommentId"] as? String
                )
            }

            let likesCount = (data["likesCount"] as? Int) ?? 0
            let commentsCount = (data["commentsCount"] as? Int) ?? 0
            let savesCount = (data["savesCount"] as? Int) ?? 0

            let createdAt: Int64
            if let createdAtTimestamp = doc.get("createdAt") as? Timestamp {
                createdAt = Int64(createdAtTimestamp.dateValue().timeIntervalSince1970 * 1000)
            } else if let created = data["createdAt"] as? Int64 {
                createdAt = created
            } else {
                createdAt = Int64(Date().timeIntervalSince1970 * 1000)
            }

            let updatedAt: KotlinLong?
            if let updatedAtTimestamp = doc.get("updatedAt") as? Timestamp {
                updatedAt = KotlinLong(value: Int64(updatedAtTimestamp.dateValue().timeIntervalSince1970 * 1000))
            } else if let updated = data["updatedAt"] as? Int64 {
                updatedAt = KotlinLong(value: updated)
            } else {
                updatedAt = nil
            }

            return Post(
                id: id,
                authorId: authorId,
                authorName: authorName,
                authorUsername: authorUsername,
                authorProfileImageUrl: authorProfileImageUrl,
                authorRole: stringToUserRole(authorRoleStr),
                authorVerificationStatus: stringToVerificationStatus(authorVerificationStatusStr),
                type: typeStr == "question" ? .question : .normal,
                text: text,
                media: mediaArray,
                voiceCaption: voiceCaption,
                crops: crops,
                hashtags: hashtags,
                location: location,
                question: question,
                likesCount: Int32(likesCount),
                commentsCount: Int32(commentsCount),
                savesCount: Int32(savesCount),
                isLikedByMe: isLikedByMe,
                isSavedByMe: false, // TODO: Check saved posts similarly
                createdAt: Int64(createdAt),
                updatedAt: updatedAt.map { KotlinLong(value: Int64($0)) }
            )
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
