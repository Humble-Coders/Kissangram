import Foundation
import os.log
import FirebaseFirestore
import Shared

/// Firestore implementation of StoryRepository.
/// Handles story creation and retrieval per FIRESTORE_SCHEMA.md.
final class FirestoreStoryRepository: StoryRepository {
    private static let log = Logger(subsystem: "com.kissangram", category: "FirestoreStoryRepo")
    private let firestore = Firestore.firestore(database: "kissangram")
    private let authRepository: AuthRepository
    
    init(authRepository: AuthRepository) {
        self.authRepository = authRepository
    }
    
    private var storiesCollection: CollectionReference {
        firestore.collection(Self.collectionStories)
    }
    func createStory(storyData: [String : Any], completionHandler: @escaping @Sendable (Story?, (any Error)?) -> Void) {
        let storyRef = storiesCollection.document()
               let storyId = storyRef.documentID

               // Build Firestore document data
               var documentData: [String: Any] = [:]

               for (key, value) in storyData {
                   documentData[key] = value
               }

               // Add generated ID
               documentData["id"] = storyId

               // Server timestamp
               documentData["createdAt"] = FieldValue.serverTimestamp()

               // expiresAt = now + 24h (client-side estimate)
               let estimatedExpiresAt = Date().addingTimeInterval(24 * 60 * 60)
               documentData["expiresAt"] = Timestamp(date: estimatedExpiresAt)

               // 🔑 Bridge async/await → completionHandler
               Task {
                   do {
                       // Create document
                       try await storyRef.setData(documentData)

                       // Fetch created document
                       let doc = try await storyRef.getDocument()

                       if let story = toStory(from: doc) {
                           completionHandler(story, nil)
                       } else {
                           completionHandler(
                               nil,
                               NSError(
                                   domain: "FirestoreStoryRepository",
                                   code: 500,
                                   userInfo: [NSLocalizedDescriptionKey: "Failed to retrieve created story"]
                               )
                           )
                       }
                   } catch {
                       completionHandler(nil, error)
                   }
               }
           }
    

    
   
   
    
 
    
    
    
    func getStoryBar() async throws -> [UserStories] {
        Self.log.debug("getStoryBar: start")
        guard let currentUserId = try await authRepository.getCurrentUserId() else {
            Self.log.warning("getStoryBar: currentUserId is nil, returning empty")
            return []
        }
        let uidPrefix = String(currentUserId.prefix(8))
        Self.log.debug("getStoryBar: currentUserId=\(uidPrefix)...")
        
        do {
            // Query storiesFeed subcollection ordered by createdAt DESC
            let storiesFeedRef = firestore
                .collection("users")
                .document(currentUserId)
                .collection("storiesFeed")
                .order(by: "createdAt", descending: true)
            
            let snapshot = try await storiesFeedRef.getDocuments()
            let rawCount = snapshot.documents.count
            Self.log.debug("getStoryBar: fetched \(rawCount) story documents")
            
            if rawCount == 0 {
                return []
            }
            
            let currentTime = Int64(Date().timeIntervalSince1970 * 1000)
            
            // Filter expired stories and parse to Story objects
            let stories = snapshot.documents.compactMap { doc -> Story? in
                do {
                    // Check if story is expired
                    if let expiresAtTimestamp = doc.get("expiresAt") as? Timestamp {
                        let expiresAt = Int64(expiresAtTimestamp.dateValue().timeIntervalSince1970 * 1000)
                        if expiresAt < currentTime {
                            Self.log.debug("getStoryBar: skipping expired story \(doc.documentID)")
                            return nil
                        }
                    }
                    
                    return toStory(from: doc)
                } catch {
                    Self.log.error("getStoryBar: failed to parse story \(doc.documentID): \(error.localizedDescription)")
                    return nil
                }
            }
            
            Self.log.debug("getStoryBar: parsed \(stories.count) valid stories (after filtering expired)")
            
            if stories.isEmpty {
                return []
            }
            
            // Batch check views and likes
            let storyIds = stories.map { $0.id }
            let viewedStoryIds = try await batchCheckViews(storyIds: storyIds, userId: currentUserId)
            let likedStoryIds = try await batchCheckLikes(storyIds: storyIds, userId: currentUserId)
            
            // Update stories with view/like status
            let storiesWithStatus = stories.map { story in
                Story(
                    id: story.id,
                    authorId: story.authorId,
                    authorName: story.authorName,
                    authorUsername: story.authorUsername,
                    authorProfileImageUrl: story.authorProfileImageUrl,
                    authorRole: story.authorRole,
                    authorVerificationStatus: story.authorVerificationStatus,
                    media: story.media,
                    textOverlay: story.textOverlay,
                    locationName: story.locationName,
                    visibility: story.visibility,
                    viewsCount: story.viewsCount,
                    likesCount: story.likesCount,
                    isViewedByMe: viewedStoryIds.contains(story.id),
                    isLikedByMe: likedStoryIds.contains(story.id),
                    createdAt: story.createdAt,
                    expiresAt: story.expiresAt
                )
            }
            
            // Group stories by authorId
            let storiesByAuthor = Dictionary(grouping: storiesWithStatus, by: { $0.authorId })
            Self.log.debug("getStoryBar: grouped into \(storiesByAuthor.count) authors")
            
            // Create UserStories objects
            let userStoriesList = storiesByAuthor.map { (authorId, authorStories) in
                let firstStory = authorStories.first!
                let sortedStories = authorStories.sorted { $0.createdAt > $1.createdAt }
                let hasUnviewedStories = authorStories.contains { !$0.isViewedByMe }
                let latestStoryTime = authorStories.map { $0.createdAt }.max() ?? 0
                
                return UserStories(
                    userId: authorId,
                    userName: firstStory.authorName,
                    userProfileImageUrl: firstStory.authorProfileImageUrl,
                    userRole: firstStory.authorRole,
                    userVerificationStatus: firstStory.authorVerificationStatus,
                    stories: sortedStories,
                    hasUnviewedStories: hasUnviewedStories,
                    latestStoryTime: latestStoryTime
                )
            }
            
            // Sort by latestStoryTime DESC
            let sortedUserStories = userStoriesList.sorted { $0.latestStoryTime > $1.latestStoryTime }
            
            Self.log.debug("getStoryBar: returning \(sortedUserStories.count) user stories")
            return sortedUserStories
            
        } catch {
            Self.log.error("getStoryBar: failed: \(error.localizedDescription)")
            return [] // Return empty array on error
        }
    }
    
    /// Batch check which stories are viewed by the current user.
    private func batchCheckViews(storyIds: [String], userId: String) async throws -> Set<String> {
        if storyIds.isEmpty { return Set<String>() }
        
        return try await withThrowingTaskGroup(of: (String, Bool).self) { group in
            var viewedStoryIds = Set<String>()
            
            // Create tasks to check each story
            for storyId in storyIds {
                group.addTask {
                    let viewDoc = try await self.firestore
                        .collection("stories")
                        .document(storyId)
                        .collection("views")
                        .document(userId)
                        .getDocument()
                    return (storyId, viewDoc.exists)
                }
            }
            
            // Collect results
            for try await (storyId, exists) in group {
                if exists {
                    viewedStoryIds.insert(storyId)
                }
            }
            
            Self.log.debug("batchCheckViews: checked \(storyIds.count) stories, found \(viewedStoryIds.count) viewed")
            return viewedStoryIds
        }
    }
    
    /// Batch check which stories are liked by the current user.
    private func batchCheckLikes(storyIds: [String], userId: String) async throws -> Set<String> {
        if storyIds.isEmpty { return Set<String>() }
        
        return try await withThrowingTaskGroup(of: (String, Bool).self) { group in
            var likedStoryIds = Set<String>()
            
            // Create tasks to check each story
            for storyId in storyIds {
                group.addTask {
                    let likeDoc = try await self.firestore
                        .collection("stories")
                        .document(storyId)
                        .collection("likes")
                        .document(userId)
                        .getDocument()
                    return (storyId, likeDoc.exists)
                }
            }
            
            // Collect results
            for try await (storyId, exists) in group {
                if exists {
                    likedStoryIds.insert(storyId)
                }
            }
            
            Self.log.debug("batchCheckLikes: checked \(storyIds.count) stories, found \(likedStoryIds.count) liked")
            return likedStoryIds
        }
    }

    func getStoriesForUser(userId: String) async throws -> [Story] {
        throw NSError(domain: "FirestoreStoryRepository", code: 501, userInfo: [NSLocalizedDescriptionKey: "Not yet implemented"])
    }

    func getMyStories() async throws -> [Story] {
        throw NSError(domain: "FirestoreStoryRepository", code: 501, userInfo: [NSLocalizedDescriptionKey: "Not yet implemented"])
    }
    func markStoryAsViewed(storyId: String) async throws {
        guard let currentUserId = try await authRepository.getCurrentUserId() else {
            Self.log.warning("markStoryAsViewed: currentUserId is nil")
            return
        }
        
        do {
            // Check if already viewed to avoid duplicate writes
            let viewDocRef = firestore
                .collection("stories")
                .document(storyId)
                .collection("views")
                .document(currentUserId)
            
            let existingView = try await viewDocRef.getDocument()
            if existingView.exists {
                Self.log.debug("markStoryAsViewed: story \(storyId) already viewed by user")
                return
            }
            
            // Create view document
            let viewData: [String: Any] = [
                "id": currentUserId,
                "viewedAt": FieldValue.serverTimestamp()
            ]
            
            try await viewDocRef.setData(viewData)
            
            // Increment viewsCount on the story document
            try await firestore
                .collection("stories")
                .document(storyId)
                .updateData(["viewsCount": FieldValue.increment(Int64(1))])
            
            Self.log.debug("markStoryAsViewed: marked story \(storyId) as viewed")
        } catch {
            Self.log.error("markStoryAsViewed: failed for storyId=\(storyId): \(error.localizedDescription)")
            throw error
        }
    }
    
  
    
    // MARK: - Helper Methods
    
    private func toStory(from doc: DocumentSnapshot) -> Story? {
        guard let data = doc.data() else { return nil }
        
        do {
            let id = (doc.get("id") as? String) ?? doc.documentID
            let authorId = (doc.get("authorId") as? String) ?? ""
            let authorName = (doc.get("authorName") as? String) ?? ""
            let authorUsername = (doc.get("authorUsername") as? String) ?? ""
            let authorProfileImageUrl = doc.get("authorProfileImageUrl") as? String
            let authorRoleStr = (doc.get("authorRole") as? String) ?? "farmer"
            let authorVerificationStatusStr = (doc.get("authorVerificationStatus") as? String) ?? "unverified"
            
            // Parse media
            guard let mediaMap = data["media"] as? [String: Any],
                  let mediaUrl = mediaMap["url"] as? String else {
                return nil
            }
            let mediaTypeStr = mediaMap["type"] as? String ?? "image"
            let mediaType: Shared.MediaType = mediaTypeStr == "video" ? .video : .image
            let thumbnailUrl = mediaMap["thumbnailUrl"] as? String
            
            let storyMedia = StoryMedia(
                url: mediaUrl,
                type: mediaType,
                thumbnailUrl: thumbnailUrl?.isEmpty == false ? thumbnailUrl : nil
            )
            
            // Parse text overlay (optional) - per schema: textOverlay: { text: "...", position: { x, y } }
            let textOverlayMap = data["textOverlay"] as? [String: Any]
            let textOverlay: TextOverlay? = textOverlayMap.flatMap { map in
                guard let text = map["text"] as? String,
                      let positionMap = map["position"] as? [String: Any],
                      let positionX = positionMap["x"] as? NSNumber,
                      let positionY = positionMap["y"] as? NSNumber else {
                    return nil
                }
                return TextOverlay(
                    text: text,
                    positionX: positionX.floatValue,
                    positionY: positionY.floatValue
                )
            }
            
            // Parse location (optional) - per schema: location: { name: "..." }
            let locationMap = data["location"] as? [String: Any]
            let locationName = locationMap?["name"] as? String
            
            // Parse visibility
            let visibilityStr = (doc.get("visibility") as? String) ?? "public"
            let visibility: Shared.PostVisibility = visibilityStr == "followers" ? .followers : .public_
            
            let viewsCount = (data["viewsCount"] as? Int) ?? 0
            let likesCount = (data["likesCount"] as? Int) ?? 0
            
            // Parse timestamps
            let createdAt: Int64
            if let createdAtTimestamp = doc.get("createdAt") as? Timestamp {
                createdAt = Int64(createdAtTimestamp.dateValue().timeIntervalSince1970 * 1000)
            } else if let created = data["createdAt"] as? Int64 {
                createdAt = created
            } else {
                createdAt = Int64(Date().timeIntervalSince1970 * 1000)
            }
            
            let expiresAt: Int64
            if let expiresAtTimestamp = doc.get("expiresAt") as? Timestamp {
                expiresAt = Int64(expiresAtTimestamp.dateValue().timeIntervalSince1970 * 1000)
            } else if let expired = data["expiresAt"] as? Int64 {
                expiresAt = expired
            } else {
                // Default to 24 hours after createdAt
                expiresAt = createdAt + (24 * 60 * 60 * 1000)
            }
            
            return Story(
                id: id,
                authorId: authorId,
                authorName: authorName,
                authorUsername: authorUsername,
                authorProfileImageUrl: authorProfileImageUrl,
                authorRole: stringToUserRole(authorRoleStr),
                authorVerificationStatus: stringToVerificationStatus(authorVerificationStatusStr),
                media: storyMedia,
                textOverlay: textOverlay,
                locationName: locationName,
                visibility: visibility,
                viewsCount: Int32(viewsCount),
                likesCount: Int32(likesCount),
                isViewedByMe: false, // TODO: Check from views subcollection
                isLikedByMe: false, // TODO: Check from likes subcollection
                createdAt: Int64(createdAt),
                expiresAt: Int64(expiresAt)
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
    
    private static let collectionStories = "stories"
}
