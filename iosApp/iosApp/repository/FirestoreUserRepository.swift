import Foundation
import FirebaseFirestore
import os.log
import Shared

private let userRepoLog = Logger(subsystem: "com.kissangram", category: "FirestoreUserRepository")

/// Firestore implementation of UserRepository.
/// Creates and reads user profile at /users/{userId} per FIRESTORE_SCHEMA.md.
final class FirestoreUserRepository: UserRepository {
    // Use named database "kissangram" instead of "(default)"
    private let firestore = Firestore.firestore(database: "kissangram")
    private let authRepository: AuthRepository

    private var usersCollection: CollectionReference {
        firestore.collection(Self.collectionUsers)
    }

    init(authRepository: AuthRepository) {
        self.authRepository = authRepository
    }

    // MARK: - UserRepository

    func createUserProfile(
        userId: String,
        phoneNumber: String,
        name: String,
        role: UserRole,
        language: String,
        verificationDocUrl: String?,
        verificationStatus: VerificationStatus
    ) async throws {
        let username = Self.generateUsername(name: name, userId: userId)
        let searchKeywords = Self.buildSearchKeywords(name: name, username: username)
        let now = Int64(Date().timeIntervalSince1970 * 1000)

        // Check if user document already exists to preserve count fields
        let userDocRef = usersCollection.document(userId)
        let existingDoc: DocumentSnapshot?
        do {
            existingDoc = try await userDocRef.getDocument()
        } catch {
            print("createUserProfile: Error checking existing user, assuming new user: \(error.localizedDescription)")
            existingDoc = nil
        }
        
        let userExists = existingDoc?.exists ?? false
        print("createUserProfile: User exists=\(userExists)")

        // Build data map with base fields
        var data: [String: Any] = [
            Self.fieldId: userId,
            Self.fieldPhoneNumber: phoneNumber,
            Self.fieldName: name,
            Self.fieldUsername: username,
            Self.fieldRole: Self.roleToFirestore(role),
            Self.fieldVerificationStatus: Self.verificationStatusToFirestore(verificationStatus),
            Self.fieldExpertise: [String](),
            Self.fieldLanguage: language,
            Self.fieldUpdatedAt: now,
            Self.fieldLastActiveAt: now,
            Self.fieldSearchKeywords: searchKeywords,
            Self.fieldIsActive: true
        ]
        
        // Only set count fields and createdAt if user doesn't exist
        // This preserves existing follow counts when user logs in again
        if !userExists {
            data[Self.fieldFollowersCount] = 0
            data[Self.fieldFollowingCount] = 0
            data[Self.fieldPostsCount] = 0
            data[Self.fieldCreatedAt] = now
            print("createUserProfile: New user - setting count fields to 0")
        } else {
            print("createUserProfile: Existing user - preserving count fields")
        }
        
        data[Self.fieldProfileImageUrl] = NSNull()
        data[Self.fieldBio] = NSNull()
        data[Self.fieldVerificationDocUrl] = verificationDocUrl ?? NSNull()
        data[Self.fieldVerificationSubmittedAt] = NSNull()
        data[Self.fieldVerifiedAt] = NSNull()
        data[Self.fieldLocation] = NSNull()

        try await usersCollection.document(userId).setData(data, merge: true)
        print("createUserProfile: SUCCESS - User profile \(userExists ? "updated" : "created")")
    }

    func getCurrentUser() async throws -> User? {
        guard let userId = try await authRepository.getCurrentUserId() else { return nil }
        return try await getUser(userId: userId)
    }

    func getFollowers(userId: String, page: Int32, pageSize: Int32) async throws -> [UserInfo] {
        // TODO: Implement get followers
        return []
    }

    func getFollowing(userId: String, page: Int32, pageSize: Int32) async throws -> [UserInfo] {
        // TODO: Implement get following
        return []
    }

    func getUser(userId: String) async throws -> User? {
        let snapshot = try await usersCollection.document(userId).getDocument()
        guard snapshot.exists, let data = snapshot.data() else { return nil }
        return Self.documentToUser(id: userId, data: data)
    }

    func getUserInfo(userId: String) async throws -> UserInfo? {
        guard let user = try await getUser(userId: userId) else { return nil }
        return UserInfo(
            id: user.id,
            name: user.name,
            username: user.username,
            profileImageUrl: user.profileImageUrl,
            role: user.role,
            verificationStatus: user.verificationStatus
        )
    }

    func isUsernameAvailable(username: String) async throws -> KotlinBoolean {
        // TODO: Implement username availability check
        return KotlinBoolean(value: true)
    }

    func searchUsers(query: String, limit: Int32) async throws -> [UserInfo] {
        let trimmedQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedQuery.isEmpty else {
            return []
        }
        
        let searchTerm = trimmedQuery.lowercased()
        // Allow single character searches for live incremental search (A -> An -> Ansh)
        
        do {
            let querySnapshot = try await usersCollection
                .whereField(Self.fieldSearchKeywords, arrayContains: searchTerm)
                .whereField(Self.fieldIsActive, isEqualTo: true)
                // TODO: Uncomment after creating Firestore composite index
                // Create index in Firebase Console: Collection: users, Fields: searchKeywords (Arrays), isActive (Ascending), followersCount (Descending)
                // Or use the link from the error message when it appears
                // .order(by: Self.fieldFollowersCount, descending: true)
                .limit(to: Int(limit))
                .getDocuments()
            
            return querySnapshot.documents.compactMap { doc in
                let data = doc.data()
                let id = doc.documentID
                guard let name = data[Self.fieldName] as? String else { return nil }
                let username = data[Self.fieldUsername] as? String ?? ""
                let profileImageUrl = data[Self.fieldProfileImageUrl] as? String
                let roleStr = data[Self.fieldRole] as? String ?? "farmer"
                let statusStr = data[Self.fieldVerificationStatus] as? String ?? "unverified"
                
                return UserInfo(
                    id: id,
                    name: name,
                    username: username,
                    profileImageUrl: profileImageUrl,
                    role: Self.firestoreToRole(roleStr),
                    verificationStatus: Self.firestoreToVerificationStatus(statusStr)
                )
            }
        } catch {
            print("Error searching users: \(error.localizedDescription)")
            return []
        }
    }

    func updateProfile(
        name: String?,
        username: String?,
        bio: String?,
        profileImageUrl: String?
    ) async throws {
        guard let userId = try await authRepository.getCurrentUserId() else {
            throw NSError(domain: "FirestoreUserRepository", code: 1, userInfo: [NSLocalizedDescriptionKey: "No authenticated user"])
        }
        var updates: [String: Any] = [Self.fieldUpdatedAt: Int64(Date().timeIntervalSince1970 * 1000)]
        if let name = name { updates[Self.fieldName] = name }
        if let username = username { updates[Self.fieldUsername] = username }
        if let bio = bio { updates[Self.fieldBio] = bio }
        if let profileImageUrl = profileImageUrl { updates[Self.fieldProfileImageUrl] = profileImageUrl }
        if updates.count > 1 {
            try await usersCollection.document(userId).updateData(updates)
        }
    }
    
    func updateFullProfile(
        name: String?,
        bio: String?,
        profileImageUrl: String?,
        role: UserRole?,
        state: String?,
        district: String?,
        village: String?,
        crops: [String]?
    ) async throws {
        guard let userId = try await authRepository.getCurrentUserId() else {
            throw NSError(domain: "FirestoreUserRepository", code: 1, userInfo: [NSLocalizedDescriptionKey: "No authenticated user"])
        }
        userRepoLog.info("updateFullProfile userId=\(userId) profileImageUrl=\(String(describing: profileImageUrl))")
        
        var updates: [String: Any] = [Self.fieldUpdatedAt: Int64(Date().timeIntervalSince1970 * 1000)]
        
        // Basic fields
        if let name = name {
            updates[Self.fieldName] = name
            // Also update search keywords when name changes
            if let user = try await getUser(userId: userId) {
                updates[Self.fieldSearchKeywords] = Self.buildSearchKeywords(name: name, username: user.username)
            }
        }
        if let bio = bio { updates[Self.fieldBio] = bio }
        if let profileImageUrl = profileImageUrl {
            updates[Self.fieldProfileImageUrl] = profileImageUrl
            userRepoLog.info("profileImageUrl included in Firestore updates")
        } else {
            userRepoLog.info("profileImageUrl is nil, NOT included in Firestore updates")
        }
        
        // Role
        if let role = role { updates[Self.fieldRole] = Self.roleToFirestore(role) }
        
        // Location - build location map if at least one location field is provided
        if state != nil || district != nil || village != nil {
            var locationMap: [String: Any] = ["country": "India"]
            if let state = state { locationMap["state"] = state }
            if let district = district { locationMap["district"] = district }
            if let village = village { locationMap["village"] = village }
            updates[Self.fieldLocation] = locationMap
        }
        
        // Crops - stored in expertise field per schema
        if let crops = crops { updates[Self.fieldExpertise] = crops }
        
        userRepoLog.info("updateFullProfile updates.count=\(updates.count) keys=\(updates.keys.joined(separator: ","))")
        if updates.count > 1 {
            try await usersCollection.document(userId).updateData(updates)
            userRepoLog.info("Firestore updateData succeeded for user \(userId)")
        } else {
            userRepoLog.info("Skipped Firestore write (only updatedAt, no other changes)")
        }
    }

    // MARK: - Helpers

    private static func generateUsername(name: String, userId: String) -> String {
        let base = String(name.trimmingCharacters(in: .whitespaces).lowercased()
            .prefix(20))
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
            .joined(separator: "_")
        let suffix = String(userId.suffix(6))
        return "\(base)_\(suffix)"
    }

    private static func buildSearchKeywords(name: String, username: String) -> [String] {
        let words = name.trimmingCharacters(in: .whitespaces).lowercased()
            .split(separator: " ")
            .map(String.init)
            .filter { $0.count > 1 }
        return Array(Set(words + [username]))
    }

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

    private static func verificationStatusToFirestore(_ s: VerificationStatus) -> String {
        switch s {
        case .pending: return "pending"
        case .verified: return "verified"
        case .rejected: return "rejected"
        default: return "unverified"
        }
    }

    private static func documentToUser(id: String, data: [String: Any]) -> User {
        let name = data[Self.fieldName] as? String ?? ""
        let username = data[Self.fieldUsername] as? String ?? ""
        let phoneNumber = data[Self.fieldPhoneNumber] as? String ?? ""
        let roleStr = data[Self.fieldRole] as? String ?? "farmer"
        let statusStr = data[Self.fieldVerificationStatus] as? String ?? "unverified"
        let expertise = (data[Self.fieldExpertise] as? [String]) ?? []
        let followersCount = (data[Self.fieldFollowersCount] as? Int64).map(Int.init) ?? 0
        let followingCount = (data[Self.fieldFollowingCount] as? Int64).map(Int.init) ?? 0
        let postsCount = (data[Self.fieldPostsCount] as? Int64).map(Int.init) ?? 0
        let language = data[Self.fieldLanguage] as? String ?? "en"
        let createdAt = (data[Self.fieldCreatedAt] as? Int64) ?? 0
        let lastActiveAt = data[Self.fieldLastActiveAt] as? Int64
        
        // Parse location map (only names, no geoPoint)
        var userLocation: UserLocation? = nil
        if let locationMap = data[Self.fieldLocation] as? [String: Any] {
            userLocation = UserLocation(
                district: locationMap["district"] as? String,
                state: locationMap["state"] as? String,
                country: locationMap["country"] as? String,
                village: locationMap["village"] as? String
            )
        }

        return User(
            id: id,
            phoneNumber: phoneNumber,
            name: name,
            username: username,
            profileImageUrl: data[Self.fieldProfileImageUrl] as? String,
            bio: data[Self.fieldBio] as? String,
            role: firestoreToRole(roleStr),
            verificationStatus: firestoreToVerificationStatus(statusStr),
            location: userLocation,
            expertise: expertise,
            followersCount: Int32(followersCount),
            followingCount: Int32(followingCount),
            postsCount: Int32(postsCount),
            language: language,
            createdAt: createdAt,
            lastActiveAt: lastActiveAt.map { KotlinLong(value: $0) }
        )
    }

    private static func firestoreToRole(_ s: String) -> UserRole {
        switch s {
        case "expert": return .expert
        case "agripreneur": return .agripreneur
        case "input_seller": return .inputSeller
        case "agri_lover": return .agriLover
        default: return .farmer
        }
    }

    private static func firestoreToVerificationStatus(_ s: String) -> VerificationStatus {
        switch s {
        case "pending": return .pending
        case "verified": return .verified
        case "rejected": return .rejected
        default: return .unverified
        }
    }

    private static let collectionUsers = "users"
    private static let fieldId = "id"
    private static let fieldPhoneNumber = "phoneNumber"
    private static let fieldName = "name"
    private static let fieldUsername = "username"
    private static let fieldProfileImageUrl = "profileImageUrl"
    private static let fieldBio = "bio"
    private static let fieldRole = "role"
    private static let fieldVerificationStatus = "verificationStatus"
    private static let fieldVerificationDocUrl = "verificationDocUrl"
    private static let fieldVerificationSubmittedAt = "verificationSubmittedAt"
    private static let fieldVerifiedAt = "verifiedAt"
    private static let fieldLocation = "location"
    private static let fieldExpertise = "expertise"
    private static let fieldFollowersCount = "followersCount"
    private static let fieldFollowingCount = "followingCount"
    private static let fieldPostsCount = "postsCount"
    private static let fieldLanguage = "language"
    private static let fieldCreatedAt = "createdAt"
    private static let fieldUpdatedAt = "updatedAt"
    private static let fieldLastActiveAt = "lastActiveAt"
    private static let fieldSearchKeywords = "searchKeywords"
    private static let fieldIsActive = "isActive"
}
