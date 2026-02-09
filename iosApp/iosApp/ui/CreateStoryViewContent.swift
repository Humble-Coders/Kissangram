import SwiftUI
import Shared
import Foundation

// MARK: - Create Story View Content
struct CreateStoryViewContent: View {
    let onBackClick: () -> Void
    let onStoryCreated: () -> Void
    
    // Repositories - using let instead of lazy var to avoid mutating issues
    private let storageRepository = IOSStorageRepository()
    private let storyRepository = FirestoreStoryRepository()
    private let preferencesRepository = IOSPreferencesRepository()
    
    // Computed properties for repositories that depend on others
    private var authRepository: AuthRepository {
        IOSAuthRepository(preferencesRepository: preferencesRepository)
    }
    
    private var userRepository: UserRepository {
        FirestoreUserRepository(authRepository: authRepository)
    }
    
    // Use case
    private var createStoryUseCase: CreateStoryUseCase {
        CreateStoryUseCase(
            storageRepository: storageRepository,
            storyRepository: storyRepository,
            authRepository: authRepository,
            userRepository: userRepository
        )
    }
    
    var body: some View {
        CreateStoryView(
            onBackClick: onBackClick,
            onStoryClick: { storyInput in
                Task {
                    do {
                        try await createStoryUseCase.invoke(input: storyInput)
                        await MainActor.run {
                            onStoryCreated()
                        }
                    } catch {
                        // Handle error - could show an alert
                        print("Failed to create story: \(error.localizedDescription)")
                        // Note: Loading state will be reset when view is dismissed
                    }
                }
            }
        )
    }
}
