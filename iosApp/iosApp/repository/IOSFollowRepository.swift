import Foundation
import FirebaseFirestore
import Shared

/// iOS implementation of FollowRepository.
/// Handles follow/unfollow operations using Firestore per FIRESTORE_SCHEMA.md.
/// Includes denormalized user info in subcollections.
final class IOSFollowRepository: FollowRepository {
    
    private let firestore = Firestore.firestore(database: "kissangram")
    private let authRepository: AuthRepository
    private let userRepository: UserRepository
    
    private var usersCollection: CollectionReference {
        firestore.collection("users")
    }
    
    init(authRepository: AuthRepository, userRepository: UserRepository? = nil) {
        self.authRepository = authRepository
        // If userRepository is not provided, create a FirestoreUserRepository
        if let userRepo = userRepository {
            self.userRepository = userRepo
        } else {
            self.userRepository = FirestoreUserRepository(authRepository: authRepository)
        }
    }
    
    func followUser(userId: String) async throws {
        guard let currentUserId = try await authRepository.getCurrentUserId() else {
            throw NSError(domain: "IOSFollowRepository", code: 1, userInfo: [NSLocalizedDescriptionKey: "No authenticated user"])
        }
        
        guard currentUserId != userId else {
            throw NSError(domain: "IOSFollowRepository", code: 2, userInfo: [NSLocalizedDescriptionKey: "Cannot follow yourself"])
        }
        
        // Get current user info for denormalization
        guard let currentUser = try await userRepository.getCurrentUser() else {
            throw NSError(domain: "IOSFollowRepository", code: 3, userInfo: [NSLocalizedDescriptionKey: "Current user not found"])
        }
        
        // Get target user info for denormalization
        guard let targetUser = try await userRepository.getUser(userId: userId) else {
            throw NSError(domain: "IOSFollowRepository", code: 4, userInfo: [NSLocalizedDescriptionKey: "User not found"])
        }
        
        let batch = firestore.batch()
        
        // Add to current user's following list with denormalized target user info
        let currentUserFollowingRef = usersCollection.document(currentUserId).collection("following").document(userId)
        let followingData: [String: Any] = [
            "id": userId,
            "name": targetUser.name,
            "username": targetUser.username,
            "profileImageUrl": targetUser.profileImageUrl ?? "",
            "role": Self.roleToFirestore(targetUser.role),
            "verificationStatus": Self.verificationStatusToFirestore(targetUser.verificationStatus),
            "followedAt": FieldValue.serverTimestamp()
        ]
        batch.setData(followingData, forDocument: currentUserFollowingRef)
        
        // Add to target user's followers list with denormalized current user info
        let targetUserFollowersRef = usersCollection.document(userId).collection("followers").document(currentUserId)
        let followerData: [String: Any] = [
            "id": currentUserId,
            "name": currentUser.name,
            "username": currentUser.username,
            "profileImageUrl": currentUser.profileImageUrl ?? "",
            "role": Self.roleToFirestore(currentUser.role),
            "verificationStatus": Self.verificationStatusToFirestore(currentUser.verificationStatus),
            "followedAt": FieldValue.serverTimestamp()
        ]
        batch.setData(followerData, forDocument: targetUserFollowersRef)
        
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
    
    // MARK: - Helper Methods
    
    private static func roleToFirestore(_ role: UserRole) -> String {
        switch role {
        case .farmer: return "farmer"
        case .expert: return "expert"
        case .agripreneur: return "agripreneur"
        case .inputSeller: return "input_seller"
        case .agriLover: return "agri_lover"
        default: return "farmer"
        }
    }
    
    private static func verificationStatusToFirestore(_ status: VerificationStatus) -> String {
        switch status {
        case .unverified: return "unverified"
        case .pending: return "pending"
        case .verified: return "verified"
        case .rejected: return "rejected"
        default: return "unverified"
        }
    }
}
