import Foundation
import FirebaseFirestore
import Shared

/// Firestore implementation of PostRepository.
/// Handles post creation and retrieval per FIRESTORE_SCHEMA.md.
final class FirestorePostRepository: PostRepository {
    
    private let firestore = Firestore.firestore(database: "kissangram")
    private let authRepository: AuthRepository
    private let userRepository: UserRepository
    
    init(authRepository: AuthRepository, userRepository: UserRepository) {
        self.authRepository = authRepository
        self.userRepository = userRepository
    }
    
    private var postsCollection: CollectionReference {
        firestore.collection(Self.collectionPosts)
    }
    
    // Store last document snapshot per user for pagination
    private var lastDocumentSnapshots: [String: DocumentSnapshot?] = [:]
    
    // Store last document snapshot per post for comment pagination
    private var lastCommentSnapshots: [String: DocumentSnapshot?] = [:]
    
    func createPost(postData: [String: Any?]) async throws -> Post {
        // Let Firestore generate the document ID automatically
        let postRef = postsCollection.document()
        let postId = postRef.documentID
        
        // Build Firestore document data
        var documentData: [String: Any] = [:]
        
        // Copy all data, converting types as needed
        for (key, value) in postData {
            if let val = value {
                documentData[key] = val
            }
        }
        
        // Add the generated ID to the data
        documentData["id"] = postId
        
        // Convert location to GeoPoint if coordinates exist
        if var locationData = postData["location"] as? [String: Any?] {
            let latitude = locationData["latitude"] as? Double
            let longitude = locationData["longitude"] as? Double
            
            var firestoreLocation: [String: Any] = [:]
            if let name = locationData["name"] as? String {
                firestoreLocation["name"] = name
            }
            
            if let lat = latitude, let lon = longitude {
                firestoreLocation["geoPoint"] = GeoPoint(latitude: lat, longitude: lon)
            }
            
            documentData["location"] = firestoreLocation
        }
        
        // Add server timestamp for createdAt and updatedAt
        documentData["createdAt"] = FieldValue.serverTimestamp()
        documentData["updatedAt"] = FieldValue.serverTimestamp()
        
        do {
            // Create post document using the reference
            try await postRef.setData(documentData)
            
            // Fetch the created post to return it
            let doc = try await postRef.getDocument()
            if let post = toPost(from: doc) {
                return post
            } else {
                throw NSError(domain: "FirestorePostRepository", code: 500, userInfo: [NSLocalizedDescriptionKey: "Failed to retrieve created post"])
            }
        } catch {
            throw error
        }
    }
    
    func createPost(postData: [String: Any], completionHandler: @escaping (Post?, Error?) -> Void) {
        // Convert [String: Any] to [String: Any?] for internal use
        let postDataWithOptionals: [String: Any?] = postData.mapValues { $0 }
        
        Task {
            do {
                let result = try await createPost(postData: postDataWithOptionals)
                completionHandler(result, nil)
            } catch {
                completionHandler(nil, error)
            }
        }
    }
    
    func getPost(postId: String) async throws -> Post? {
        let doc = try await postsCollection.document(postId).getDocument()
        
        // Check if current user has liked this post
        let currentUserId = try await authRepository.getCurrentUserId()
        let isLikedByMe = if let userId = currentUserId {
            try await checkIfLiked(postId: postId, userId: userId)
        } else {
            false
        }
        
        return toPost(from: doc, isLikedByMe: isLikedByMe)
    }
    
    /// Check if a single post is liked by the current user.
    private func checkIfLiked(postId: String, userId: String) async throws -> Bool {
        let likeDoc = try await postsCollection
            .document(postId)
            .collection("likes")
            .document(userId)
            .getDocument()
        return likeDoc.exists
    }
    
    /// Batch check which posts are liked by the current user.
    private func batchCheckLikes(postIds: [String], userId: String) async throws -> Set<String> {
        if postIds.isEmpty { return Set<String>() }
        
        return try await withThrowingTaskGroup(of: (String, Bool).self) { group in
            var likedPostIds = Set<String>()
            
            // Create tasks to check each post
            for postId in postIds {
                group.addTask {
                    let likeDoc = try await self.postsCollection
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
            
            return likedPostIds
        }
    }
    
    func likePost(postId: String) async throws {
        guard let currentUserId = try await authRepository.getCurrentUserId() else {
            throw NSError(domain: "FirestorePostRepository", code: 401, userInfo: [NSLocalizedDescriptionKey: "No authenticated user"])
        }
        
        guard let user = try await userRepository.getCurrentUser() else {
            throw NSError(domain: "FirestorePostRepository", code: 404, userInfo: [NSLocalizedDescriptionKey: "User profile not found"])
        }
        
        let roleStr: String
        switch user.role {
        case .expert: roleStr = "expert"
        case .agripreneur: roleStr = "agripreneur"
        case .inputSeller: roleStr = "input_seller"
        case .agriLover: roleStr = "agri_lover"
        default: roleStr = "farmer"
        }
        
        let verificationStatusStr: String
        switch user.verificationStatus {
        case .pending: verificationStatusStr = "pending"
        case .verified: verificationStatusStr = "verified"
        case .rejected: verificationStatusStr = "rejected"
        default: verificationStatusStr = "unverified"
        }
        
        let likeData: [String: Any] = [
            "id": currentUserId,
            "name": user.name,
            "username": user.username,
            "profileImageUrl": user.profileImageUrl ?? "",
            "role": roleStr,
            "verificationStatus": verificationStatusStr,
            "likedAt": FieldValue.serverTimestamp()
        ]
        
        let likeRef = postsCollection
            .document(postId)
            .collection("likes")
            .document(currentUserId)
        
        try await likeRef.setData(likeData)
    }
    
    func unlikePost(postId: String) async throws {
        guard let currentUserId = try await authRepository.getCurrentUserId() else {
            throw NSError(domain: "FirestorePostRepository", code: 401, userInfo: [NSLocalizedDescriptionKey: "No authenticated user"])
        }
        
        let likeRef = postsCollection
            .document(postId)
            .collection("likes")
            .document(currentUserId)
        
        try await likeRef.delete()
    }
    
    func savePost(postId: String) async throws {
        // TODO: Implement save functionality
        throw NSError(domain: "FirestorePostRepository", code: 501, userInfo: [NSLocalizedDescriptionKey: "Not yet implemented"])
    }
    
    func unsavePost(postId: String) async throws {
        // TODO: Implement unsave functionality
        throw NSError(domain: "FirestorePostRepository", code: 501, userInfo: [NSLocalizedDescriptionKey: "Not yet implemented"])
    }
    
    func getComments(postId: String, page: Int32, pageSize: Int32) async throws -> [Comment] {
        guard page >= 0 else { return [] }
        guard pageSize >= 1, pageSize <= 50 else { return [] }
        
        let commentsCollection = postsCollection.document(postId).collection("comments")
        
        var query: Query = commentsCollection
            .whereField("isActive", isEqualTo: true)
            .whereField("parentCommentId", isEqualTo: NSNull()) // Only top-level comments
            .order(by: "createdAt", descending: true)
            .limit(to: Int(pageSize))
        
        if page > 0 {
            guard let optionalSnap = lastCommentSnapshots[postId],
                  let lastSnap = optionalSnap else {
                // No cursor for this post, return empty
                return []
            }
            query = query.start(afterDocument: lastSnap)
        } else {
            lastCommentSnapshots[postId] = nil
        }
        
        let snapshot = try await query.getDocuments()
        let documents = snapshot.documents
        
        // First, parse all comments to get their IDs
        let commentsWithIds = documents.compactMap { doc -> Comment? in
            toComment(from: doc, isLikedByMe: false) // Temporary, will update after checking likes
        }
        
        // Batch check which comments are liked by current user
        let currentUserId = try await authRepository.getCurrentUserId()
        let likedCommentIds = if let userId = currentUserId, !commentsWithIds.isEmpty {
            try await batchCheckCommentLikes(commentIds: commentsWithIds.map { $0.id }, postId: postId, userId: userId)
        } else {
            Set<String>()
        }
        
        // Update comments with correct isLikedByMe value
        let comments = commentsWithIds.map { comment in
            Comment(
                id: comment.id,
                postId: comment.postId,
                authorId: comment.authorId,
                authorName: comment.authorName,
                authorUsername: comment.authorUsername,
                authorProfileImageUrl: comment.authorProfileImageUrl,
                authorRole: comment.authorRole,
                authorVerificationStatus: comment.authorVerificationStatus,
                text: comment.text,
                voiceComment: comment.voiceComment,
                parentCommentId: comment.parentCommentId,
                repliesCount: comment.repliesCount,
                likesCount: comment.likesCount,
                isLikedByMe: likedCommentIds.contains(comment.id),
                isExpertAnswer: comment.isExpertAnswer,
                isBestAnswer: comment.isBestAnswer,
                createdAt: comment.createdAt
            )
        }
        
        // Store last document for pagination
        if let lastDoc = documents.last {
            lastCommentSnapshots[postId] = lastDoc
        }
        
        return comments
    }
    
    func getReplies(postId: String, parentCommentId: String, page: Int32, pageSize: Int32) async throws -> [Comment] {
        guard page >= 0 else { return [] }
        guard pageSize >= 1, pageSize <= 50 else { return [] }
        
        let commentsCollection = postsCollection.document(postId).collection("comments")
        let replyKey = "\(postId)_\(parentCommentId)"
        
        var query: Query = commentsCollection
            .whereField("isActive", isEqualTo: true)
            .whereField("parentCommentId", isEqualTo: parentCommentId)
            .order(by: "createdAt", descending: false)
            .limit(to: Int(pageSize))
        
        if page > 0 {
            guard let optionalSnap = lastCommentSnapshots[replyKey],
                  let lastSnap = optionalSnap else {
                return []
            }
            query = query.start(afterDocument: lastSnap)
        } else {
            lastCommentSnapshots[replyKey] = nil
        }
        
        let snapshot = try await query.getDocuments()
        let documents = snapshot.documents
        
        let repliesWithIds = documents.compactMap { doc -> Comment? in
            toComment(from: doc, isLikedByMe: false)
        }
        
        let currentUserId = try await authRepository.getCurrentUserId()
        let likedCommentIds = if let userId = currentUserId, !repliesWithIds.isEmpty {
            try await batchCheckCommentLikes(commentIds: repliesWithIds.map { $0.id }, postId: postId, userId: userId)
        } else {
            Set<String>()
        }
        
        let comments = repliesWithIds.map { comment in
            Comment(
                id: comment.id,
                postId: comment.postId,
                authorId: comment.authorId,
                authorName: comment.authorName,
                authorUsername: comment.authorUsername,
                authorProfileImageUrl: comment.authorProfileImageUrl,
                authorRole: comment.authorRole,
                authorVerificationStatus: comment.authorVerificationStatus,
                text: comment.text,
                voiceComment: comment.voiceComment,
                parentCommentId: comment.parentCommentId,
                repliesCount: comment.repliesCount,
                likesCount: comment.likesCount,
                isLikedByMe: likedCommentIds.contains(comment.id),
                isExpertAnswer: comment.isExpertAnswer,
                isBestAnswer: comment.isBestAnswer,
                createdAt: comment.createdAt
            )
        }
        
        if let lastDoc = documents.last {
            lastCommentSnapshots[replyKey] = lastDoc
        }
        
        return comments
    }
    
    func addComment(postId: String, text: String, parentCommentId: String?) async throws -> Comment {
        guard let currentUserId = try await authRepository.getCurrentUserId() else {
            throw NSError(domain: "FirestorePostRepository", code: 401, userInfo: [NSLocalizedDescriptionKey: "User must be authenticated to add comment"])
        }
        
        // Get current user info
        guard let user = try await userRepository.getCurrentUser() else {
            throw NSError(domain: "FirestorePostRepository", code: 404, userInfo: [NSLocalizedDescriptionKey: "User profile not found"])
        }
        
        let commentsCollection = postsCollection.document(postId).collection("comments")
        let commentRef = commentsCollection.document()
        let commentId = commentRef.documentID
        
        var commentData: [String: Any] = [
            "id": commentId,
            "postId": postId,
            "authorId": currentUserId,
            "authorName": user.name,
            "authorUsername": user.username,
            "authorProfileImageUrl": user.profileImageUrl ?? NSNull(),
            "authorRole": userRoleToFirestore(user.role),
            "authorVerificationStatus": verificationStatusToFirestore(user.verificationStatus),
            "text": text,
            "parentCommentId": parentCommentId ?? NSNull(),
            "repliesCount": 0,
            "likesCount": 0,
            "isExpertAnswer": false,
            "isBestAnswer": false,
            "isActive": true,
            "createdAt": FieldValue.serverTimestamp()
        ]
        
        // Voice comment is optional, not included for now
        commentData["voiceComment"] = NSNull()
        
        do {
            try await commentRef.setData(commentData)
            
            // Fetch the created comment to return it
            let doc = try await commentRef.getDocument()
            guard let comment = toComment(from: doc, isLikedByMe: false) else {
                throw NSError(domain: "FirestorePostRepository", code: 500, userInfo: [NSLocalizedDescriptionKey: "Failed to retrieve created comment"])
            }
            return comment
        } catch {
            throw NSError(domain: "FirestorePostRepository", code: 500, userInfo: [NSLocalizedDescriptionKey: "Failed to add comment: \(error.localizedDescription)"])
        }
    }
    
    func deleteComment(postId: String, commentId: String, reason: String) async throws {
        guard let currentUserId = try await authRepository.getCurrentUserId() else {
            throw NSError(domain: "FirestorePostRepository", code: 401, userInfo: [NSLocalizedDescriptionKey: "User must be authenticated to delete comment"])
        }
        
        let commentRef = postsCollection
            .document(postId)
            .collection("comments")
            .document(commentId)
        
        // Verify the comment belongs to current user
        let commentDoc = try await commentRef.getDocument()
        guard commentDoc.exists else {
            throw NSError(domain: "FirestorePostRepository", code: 404, userInfo: [NSLocalizedDescriptionKey: "Comment not found"])
        }
        
        guard let commentData = commentDoc.data(),
              let commentAuthorId = commentData["authorId"] as? String else {
            throw NSError(domain: "FirestorePostRepository", code: 500, userInfo: [NSLocalizedDescriptionKey: "Invalid comment data"])
        }
        
        if commentAuthorId != currentUserId {
            throw NSError(domain: "FirestorePostRepository", code: 403, userInfo: [NSLocalizedDescriptionKey: "User can only delete their own comments"])
        }
        
        // Soft delete: set isActive = false and store deletion reason
        // This triggers onCommentUpdate Cloud Function which decrements counts
        let updateData: [String: Any] = [
            "isActive": false,
            "deletionReason": reason
        ]
        
        do {
            try await commentRef.updateData(updateData)
        } catch {
            throw NSError(domain: "FirestorePostRepository", code: 500, userInfo: [NSLocalizedDescriptionKey: "Failed to delete comment: \(error.localizedDescription)"])
        }
    }
    
    /// Batch check which comments are liked by the current user.
    private func batchCheckCommentLikes(commentIds: [String], postId: String, userId: String) async throws -> Set<String> {
        if commentIds.isEmpty { return Set<String>() }
        
        return try await withThrowingTaskGroup(of: (String, Bool).self) { group in
            for commentId in commentIds {
                group.addTask {
                    do {
                        let likeDoc = try await self.postsCollection
                            .document(postId)
                            .collection("comments")
                            .document(commentId)
                            .collection("likes")
                            .document(userId)
                            .getDocument()
                        return (commentId, likeDoc.exists)
                    } catch {
                        return (commentId, false)
                    }
                }
            }
            
            var likedIds = Set<String>()
            for try await (commentId, isLiked) in group {
                if isLiked {
                    likedIds.insert(commentId)
                }
            }
            return likedIds
        }
    }
    
    /// Convert Firestore DocumentSnapshot to Comment model
    private func toComment(from doc: DocumentSnapshot, isLikedByMe: Bool = false) -> Comment? {
        guard let data = doc.data() else { return nil }
        
        do {
            let id = (doc.get("id") as? String) ?? doc.documentID
            let postId = (doc.get("postId") as? String) ?? ""
            let authorId = (doc.get("authorId") as? String) ?? ""
            let authorName = (doc.get("authorName") as? String) ?? ""
            let authorUsername = (doc.get("authorUsername") as? String) ?? ""
            let authorProfileImageUrl = doc.get("authorProfileImageUrl") as? String
            let authorRoleStr = (doc.get("authorRole") as? String) ?? "farmer"
            let authorVerificationStatusStr = (doc.get("authorVerificationStatus") as? String) ?? "unverified"
            
            let text = (doc.get("text") as? String) ?? ""
            
            // Parse voice comment
            let voiceCommentMap = data["voiceComment"] as? [String: Any]
            let voiceComment: VoiceContent? = voiceCommentMap.flatMap { map in
                guard let url = map["url"] as? String else { return nil }
                let duration = (map["durationSeconds"] as? Int) ?? 0
                return VoiceContent(url: url, durationSeconds: Int32(duration))
            }
            
            let parentCommentId = doc.get("parentCommentId") as? String
            let repliesCount = Int32((doc.get("repliesCount") as? Int) ?? 0)
            let likesCount = Int32((doc.get("likesCount") as? Int) ?? 0)
            let isExpertAnswer = (doc.get("isExpertAnswer") as? Bool) ?? false
            let isBestAnswer = (doc.get("isBestAnswer") as? Bool) ?? false
            
            let createdAt: Int64
            if let timestamp = doc.get("createdAt") as? Timestamp {
                createdAt = Int64(timestamp.dateValue().timeIntervalSince1970 * 1000)
            } else {
                createdAt = Int64(Date().timeIntervalSince1970 * 1000)
            }
            
            return Comment(
                id: id,
                postId: postId,
                authorId: authorId,
                authorName: authorName,
                authorUsername: authorUsername,
                authorProfileImageUrl: authorProfileImageUrl,
                authorRole: stringToUserRole(authorRoleStr),
                authorVerificationStatus: stringToVerificationStatus(authorVerificationStatusStr),
                text: text,
                voiceComment: voiceComment,
                parentCommentId: parentCommentId,
                repliesCount: repliesCount,
                likesCount: likesCount,
                isLikedByMe: isLikedByMe,
                isExpertAnswer: isExpertAnswer,
                isBestAnswer: isBestAnswer,
                createdAt: createdAt
            )
        } catch {
            return nil
        }
    }
    
    private func userRoleToFirestore(_ role: UserRole) -> String {
        switch role {
        case .farmer: return "farmer"
        case .agripreneur: return "agripreneur"
        case .expert: return "expert"
        case .inputSeller: return "input_seller"
        case .agriLover: return "agri_lover"
        default: return "farmer"
        }
    }
    
    private func verificationStatusToFirestore(_ status: VerificationStatus) -> String {
        switch status {
        case .unverified: return "unverified"
        case .pending: return "pending"
        case .verified: return "verified"
        case .rejected: return "rejected"
        default: return "unverified"
        }
    }
    
    func getPostsByUser(userId: String, page: Int32, pageSize: Int32) async throws -> [Post] {
        guard page >= 0 else { return [] }
        guard pageSize >= 1, pageSize <= 50 else { return [] }
        
        var query: Query = postsCollection
            .whereField("authorId", isEqualTo: userId)
            .whereField("isActive", isEqualTo: true)
            .order(by: "createdAt", descending: true)
            .limit(to: Int(pageSize))
        
        if page > 0 {
            guard let optionalSnap = lastDocumentSnapshots[userId],
                  let lastSnap = optionalSnap else {
                // No cursor for this user, return empty
                return []
            }
            query = query.start(afterDocument: lastSnap)
        } else {
            lastDocumentSnapshots[userId] = nil
        }
        
        let snapshot = try await query.getDocuments()
        let documents = snapshot.documents
        
        // First, parse all posts to get their IDs
        let postsWithIds = documents.compactMap { doc -> Post? in
            toPost(from: doc, isLikedByMe: false) // Temporary, will update after checking likes
        }
        
        // Batch check which posts are liked by current user
        let currentUserId = try await authRepository.getCurrentUserId()
        let likedPostIds = if let userId = currentUserId, !postsWithIds.isEmpty {
            try await batchCheckLikes(postIds: postsWithIds.map { $0.id }, userId: userId)
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
        
        // Store last document for pagination
        if let lastDoc = documents.last {
            lastDocumentSnapshots[userId] = lastDoc
        }
        
        return posts
    }
    
    func getPostsByCrop(crop: String, page: Int32, pageSize: Int32) async throws -> [Post] {
        // TODO: Implement get posts by crop
        return []
    }
    
    func getPostsByHashtag(hashtag: String, page: Int32, pageSize: Int32) async throws -> [Post] {
        // TODO: Implement get posts by hashtag
        return []
    }
    
    // MARK: - Helper Methods
    
    private func toPost(from doc: DocumentSnapshot, isLikedByMe: Bool = false) -> Post? {
        guard let data = doc.data() else { return nil }
        
        do {
            let id = (doc.get("id") as? String) ?? doc.documentID
            let authorId = (doc.get("authorId") as? String) ?? ""
            let authorName = (doc.get("authorName") as? String) ?? ""
            let authorUsername = (doc.get("authorUsername") as? String) ?? ""
            let authorProfileImageUrl = doc.get("authorProfileImageUrl") as? String
            let authorRoleStr = (doc.get("authorRole") as? String) ?? "farmer"
            let authorVerificationStatusStr = (doc.get("authorVerificationStatus") as? String) ?? "unverified"
            
            let typeStr = (doc.get("type") as? String) ?? "normal"
            let text = (doc.get("text") as? String) ?? ""
            
            // Parse media array
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
            
            // Parse voice caption
            let voiceCaptionMap = data["voiceCaption"] as? [String: Any]
            let voiceCaption: VoiceContent? = voiceCaptionMap.flatMap { map in
                guard let url = map["url"] as? String else { return nil }
                let duration = (map["durationSeconds"] as? Int) ?? 0
                return VoiceContent(url: url, durationSeconds: Int32(duration))
            }
            
            // Parse crops and hashtags
            let crops = (data["crops"] as? [String]) ?? []
            let hashtags = (data["hashtags"] as? [String]) ?? []
            
            // Parse location
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
            
            // Parse question data
            let questionMap = data["question"] as? [String: Any]
            let question: QuestionData? = questionMap.flatMap { map in
                let targetExpertise = (map["targetExpertise"] as? [String]) ?? []
                let targetExpertIds = (map["targetExpertIds"] as? [String]) ?? []
                let targetExperts = (map["targetExperts"] as? [[String: Any]])?.compactMap { expertMap -> UserInfo? in
                    guard let id = expertMap["id"] as? String,
                          let name = expertMap["name"] as? String,
                          let username = expertMap["username"] as? String else {
                        return nil
                    }
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
            
            // Parse timestamps
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
                isSavedByMe: false, // TODO: Check from current user's saved posts
                createdAt: Int64(createdAt),
                updatedAt: updatedAt.map { KotlinLong(value: Int64($0)) }
            )
        } catch {
            return nil
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
    
    private static let collectionPosts = "posts"
}
