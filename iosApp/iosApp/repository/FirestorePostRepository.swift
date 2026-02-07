import Foundation
import FirebaseFirestore
import Shared

/// Firestore implementation of PostRepository.
/// Handles post creation and retrieval per FIRESTORE_SCHEMA.md.
final class FirestorePostRepository: PostRepository {
    
    private let firestore = Firestore.firestore(database: "kissangram")
    
    private var postsCollection: CollectionReference {
        firestore.collection(Self.collectionPosts)
    }
    
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
        return toPost(from: doc)
    }
    
    func likePost(postId: String) async throws {
        // TODO: Implement like functionality
        throw NSError(domain: "FirestorePostRepository", code: 501, userInfo: [NSLocalizedDescriptionKey: "Not yet implemented"])
    }
    
    func unlikePost(postId: String) async throws {
        // TODO: Implement unlike functionality
        throw NSError(domain: "FirestorePostRepository", code: 501, userInfo: [NSLocalizedDescriptionKey: "Not yet implemented"])
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
        // TODO: Implement get comments
        return []
    }
    
    func addComment(postId: String, text: String) async throws -> Comment {
        // TODO: Implement add comment
        throw NSError(domain: "FirestorePostRepository", code: 501, userInfo: [NSLocalizedDescriptionKey: "Not yet implemented"])
    }
    
    func getPostsByUser(userId: String, page: Int32, pageSize: Int32) async throws -> [Post] {
        // TODO: Implement get posts by user
        return []
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
    
    private func toPost(from doc: DocumentSnapshot) -> Post? {
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
                isLikedByMe: false, // TODO: Check from current user's liked posts
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
