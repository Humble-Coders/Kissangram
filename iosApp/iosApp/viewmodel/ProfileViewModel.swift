import Foundation
import SwiftUI
import Shared

@MainActor
class ProfileViewModel: ObservableObject {
    @Published var user: User?
    @Published var isLoading = false
    @Published var error: String?

    private let prefs = IOSPreferencesRepository()
    private lazy var authRepo = IOSAuthRepository(preferencesRepository: prefs)
    private lazy var userRepo = FirestoreUserRepository(authRepository: authRepo)

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
            } catch {
                await MainActor.run {
                    self.error = error.localizedDescription
                    self.isLoading = false
                }
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
