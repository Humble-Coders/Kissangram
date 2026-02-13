import Foundation
import SwiftUI
import Shared

@MainActor
class ProfileViewModel: ObservableObject {
    @Published var user: User?
    @Published var isLoading = false
    @Published var error: String?
    @Published var posts: [Post] = []
    @Published var isLoadingPosts = false

    private let prefs = IOSPreferencesRepository()
    private lazy var authRepo = IOSAuthRepository(preferencesRepository: prefs)
    private lazy var userRepo = FirestoreUserRepository(authRepository: authRepo)
    private lazy var postRepo = FirestorePostRepository(authRepository: authRepo, userRepository: userRepo)

    func loadProfile() {
        isLoading = true
        error = nil
        Task {
            do {
                let profile = try await userRepo.getCurrentUser()
                await MainActor.run {
                    self.user = profile
                    self.isLoading = false
                }
                // Load posts after profile is loaded
                if let userId = profile?.id {
                    await loadUserPosts(userId: userId)
                }
            } catch {
                await MainActor.run {
                    self.error = error.localizedDescription
                    self.isLoading = false
                }
            }
        }
    }
    
    func loadUserPosts(userId: String) async {
        await MainActor.run {
            isLoadingPosts = true
        }
        do {
            let posts = try await postRepo.getPostsByUser(userId: userId, page: 0, pageSize: 30)
            await MainActor.run {
                self.posts = posts
                self.isLoadingPosts = false
            }
        } catch {
            await MainActor.run {
                self.error = error.localizedDescription
                self.isLoadingPosts = false
            }
        }
    }

    func signOut(onSignedOut: @escaping () -> Void) {
        Task {
            do {
                try await authRepo.signOut()
                await MainActor.run { onSignedOut() }
            } catch {
                await MainActor.run { self.error = error.localizedDescription }
            }
        }
    }
}
