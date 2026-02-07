import Foundation
import FirebaseFirestore
import Shared

/// iOS implementation of FollowRepository.
/// Handles follow/unfollow operations using Firestore.
final class IOSFollowRepository: FollowRepository {
    
    private let firestore = Firestore.firestore(database: "kissangram")
    private let authRepository: AuthRepository
    
    private var usersCollection: CollectionReference {
        firestore.collection("users")
    }
    
    init(authRepository: AuthRepository) {
        self.authRepository = authRepository
    }
    
    func followUser(userId: String) async throws {
        guard let currentUserId = try await authRepository.getCurrentUserId() else {
            throw NSError(domain: "IOSFollowRepository", code: 1, userInfo: [NSLocalizedDescriptionKey: "No authenticated user"])
        }
        
        guard currentUserId != userId else {
            throw NSError(domain: "IOSFollowRepository", code: 2, userInfo: [NSLocalizedDescriptionKey: "Cannot follow yourself"])
        }
        
        let batch = firestore.batch()
        
        // Add to current user's following list
        let currentUserFollowingRef = usersCollection.document(currentUserId).collection("following").document(userId)
        batch.setData(["userId": userId, "followedAt": FieldValue.serverTimestamp()], forDocument: currentUserFollowingRef)
        
        // Add to target user's followers list
        let targetUserFollowersRef = usersCollection.document(userId).collection("followers").document(currentUserId)
        batch.setData(["userId": currentUserId, "followedAt": FieldValue.serverTimestamp()], forDocument: targetUserFollowersRef)
        
        // Update follower/following counts
        let currentUserRef = usersCollection.document(currentUserId)
        batch.updateData(["followingCount": FieldValue.increment(Int64(1))], forDocument: currentUserRef)
        
        let targetUserRef = usersCollection.document(userId)
        batch.updateData(["followersCount": FieldValue.increment(Int64(1))], forDocument: targetUserRef)
        
        try await batch.commit()
    }
    
    func unfollowUser(userId: String) async throws {
        guard let currentUserId = try await authRepository.getCurrentUserId() else {
            throw NSError(domain: "IOSFollowRepository", code: 1, userInfo: [NSLocalizedDescriptionKey: "No authenticated user"])
        }
        
        guard currentUserId != userId else {
            throw NSError(domain: "IOSFollowRepository", code: 2, userInfo: [NSLocalizedDescriptionKey: "Cannot unfollow yourself"])
        }
        
        let batch = firestore.batch()
        
        // Remove from current user's following list
        let currentUserFollowingRef = usersCollection.document(currentUserId).collection("following").document(userId)
        batch.deleteDocument(currentUserFollowingRef)
        
        // Remove from target user's followers list
        let targetUserFollowersRef = usersCollection.document(userId).collection("followers").document(currentUserId)
        batch.deleteDocument(targetUserFollowersRef)
        
        // Update follower/following counts
        let currentUserRef = usersCollection.document(currentUserId)
        batch.updateData(["followingCount": FieldValue.increment(Int64(-1))], forDocument: currentUserRef)
        
        let targetUserRef = usersCollection.document(userId)
        batch.updateData(["followersCount": FieldValue.increment(Int64(-1))], forDocument: targetUserRef)
        
        try await batch.commit()
    }
    
    func isFollowing(userId: String) async throws -> KotlinBoolean {
        guard let currentUserId = try await authRepository.getCurrentUserId() else {
            return KotlinBoolean(value: false)
        }
        
        guard currentUserId != userId else {
            return KotlinBoolean(value: false)
        }
        
        let followingRef = usersCollection.document(currentUserId).collection("following").document(userId)
        let doc = try await followingRef.getDocument()
        
        return KotlinBoolean(value: doc.exists)
    }
    
    func isFollowedBy(userId: String) async throws -> KotlinBoolean {
        guard let currentUserId = try await authRepository.getCurrentUserId() else {
            return KotlinBoolean(value: false)
        }
        
        guard currentUserId != userId else {
            return KotlinBoolean(value: false)
        }
        
        let followersRef = usersCollection.document(currentUserId).collection("followers").document(userId)
        let doc = try await followersRef.getDocument()
        
        return KotlinBoolean(value: doc.exists)
    }
}
