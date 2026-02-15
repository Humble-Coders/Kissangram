import Foundation
import SwiftUI
import Shared

@MainActor
class OtherUserProfileViewModel: ObservableObject {
    @Published var user: User?
    @Published var posts: [Post] = []
    @Published var isLoading = false
    @Published var isLoadingPosts = false
    @Published var isFollowLoading = false
    @Published var isFollowing = false
    @Published var error: String?

    private let prefs = IOSPreferencesRepository()
    private lazy var authRepo = IOSAuthRepository(preferencesRepository: prefs)
    private lazy var userRepo = FirestoreUserRepository(authRepository: authRepo)
    private lazy var postRepo = FirestorePostRepository(authRepository: authRepo, userRepository: userRepo)
    private lazy var followRepo = IOSFollowRepository(authRepository: authRepo, userRepository: userRepo)
    private lazy var followUserUseCase = FollowUserUseCase(followRepository: followRepo)

    func loadUserProfile(userId: String) {
        isLoading = true
        error = nil
        
        Task {
            do {
                // Load user profile
                guard let loadedUser = try await userRepo.getUser(userId: userId) else {
                    await MainActor.run {
                        self.isLoading = false
                        self.error = "User not found"
                    }
                    return
                }

                // Check if current user is following this user
                let followingStatus = try await followRepo.isFollowing(userId: userId)
                
                await MainActor.run {
                    self.user = loadedUser
                    self.isFollowing = followingStatus.boolValue
                    self.isLoading = false
                }
                
                // Load user's posts
                await loadPosts(userId: userId)
            } catch {
                await MainActor.run {
                    self.isLoading = false
                    self.error = error.localizedDescription
                }
            }
        }
    }

    func toggleFollow() {
        guard let userId = user?.id else { return }
        let isCurrentlyFollowing = isFollowing

        isFollowLoading = true
        error = nil

        // Optimistic update
        let currentUser = user
        let newFollowingState = !isCurrentlyFollowing
        let newFollowersCount = if newFollowingState {
            (currentUser?.followersCount ?? 0) + 1
        } else {
            max(0, (currentUser?.followersCount ?? 0) - 1)
        }

        isFollowing = newFollowingState
        if var updatedUser = currentUser {
            updatedUser = User(
                id: updatedUser.id,
                phoneNumber: updatedUser.phoneNumber,
                name: updatedUser.name,
                username: updatedUser.username,
                profileImageUrl: updatedUser.profileImageUrl,
                bio: updatedUser.bio,
                role: updatedUser.role,
                verificationStatus: updatedUser.verificationStatus,
                location: updatedUser.location,
                expertise: updatedUser.expertise,
                followersCount: Int32(newFollowersCount),
                followingCount: updatedUser.followingCount,
                postsCount: updatedUser.postsCount,
                language: updatedUser.language,
                createdAt: updatedUser.createdAt,
                lastActiveAt: updatedUser.lastActiveAt
            )
            user = updatedUser
        }

        Task {
            do {
                // Perform actual follow/unfollow
                try await followUserUseCase.invoke(userId: userId, isCurrentlyFollowing: isCurrentlyFollowing)

                // Reload user to get updated counts
                if let updatedUser = try await userRepo.getUser(userId: userId) {
                    let followingStatus = try await followRepo.isFollowing(userId: userId)
                    
                    await MainActor.run {
                        self.user = updatedUser
                        self.isFollowing = followingStatus.boolValue
                        self.isFollowLoading = false
                    }
                }
            } catch {
                // Rollback optimistic update on error
                await MainActor.run {
                    self.isFollowing = isCurrentlyFollowing
                    self.isFollowLoading = false
                    self.error = "Failed to \(isCurrentlyFollowing ? "unfollow" : "follow") user"
                }
            }
        }
    }
    
    private func loadPosts(userId: String) async {
        await MainActor.run { isLoadingPosts = true }
        do {
            let loadedPosts = try await postRepo.getPostsByUser(userId: userId, page: 0, pageSize: 30)
            await MainActor.run {
                self.posts = loadedPosts
                self.isLoadingPosts = false
            }
        } catch {
            await MainActor.run {
                self.posts = []
                self.isLoadingPosts = false
            }
        }
    }
}
