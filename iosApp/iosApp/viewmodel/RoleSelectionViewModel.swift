import Foundation
import SwiftUI
import Shared

@MainActor
class RoleSelectionViewModel: ObservableObject {
    @Published var selectedRole: UserRole?
    @Published var isLoading: Bool = false
    @Published var error: String?
    
    private let prefs = IOSPreferencesRepository()
    private lazy var authRepo = IOSAuthRepository(preferencesRepository: prefs)
    private lazy var userRepo = FirestoreUserRepository(authRepository: authRepo)
    private lazy var createUserProfileUseCase = CreateUserProfileUseCase(
        authRepository: authRepo,
        preferencesRepository: prefs,
        userRepository: userRepo
    )
    
    init() {
        self.selectedRole = nil
    }
    
    func selectRole(_ role: UserRole) {
        selectedRole = role
    }
    
    func saveRole(onSuccess: @escaping () -> Void, onError: @escaping (String) -> Void) {
        guard let currentRole = selectedRole else {
            onError("Please select a role")
            return
        }
        
        isLoading = true
        error = nil
        let role = currentRole
        
        Task {
            do {
                if role != .expert {
                    try await createUserProfileUseCase.invoke(
                        role: role,
                        verificationDocUrl: nil,
                        verificationStatus: .unverified
                    )
                    try await prefs.setAuthCompleted()
                }
                await MainActor.run {
                    isLoading = false
                    onSuccess()
                }
            } catch {
                await MainActor.run {
                    isLoading = false
                    self.error = error.localizedDescription
                    onError(error.localizedDescription)
                }
            }
        }
    }
}
