import Foundation
import FirebaseFirestore
import Shared

/// Firestore implementation of StoryRepository.
/// Handles story creation and retrieval per FIRESTORE_SCHEMA.md.
final class FirestoreStoryRepository: StoryRepository {
    private let firestore = Firestore.firestore(database: "kissangram")
    
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
        // TODO: Implement story bar (query stories from followed users, group by author)
        // Return empty so home screen shows real feed without dummy story data
        return []
    }

    func getStoriesForUser(userId: String) async throws -> [Story] {
        throw NSError(domain: "FirestoreStoryRepository", code: 501, userInfo: [NSLocalizedDescriptionKey: "Not yet implemented"])
    }

    func getMyStories() async throws -> [Story] {
        throw NSError(domain: "FirestoreStoryRepository", code: 501, userInfo: [NSLocalizedDescriptionKey: "Not yet implemented"])
    }
    func markStoryAsViewed(storyId: String) async throws {
        // TODO: Implement mark story as viewed
        throw NSError(domain: "FirestoreStoryRepository", code: 501, userInfo: [NSLocalizedDescriptionKey: "Not yet implemented"])
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
