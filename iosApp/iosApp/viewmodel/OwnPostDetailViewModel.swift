import Foundation
import os.log
import Shared

@MainActor
class OwnPostDetailViewModel: ObservableObject {
    
    private static let log = Logger(subsystem: "com.kissangram", category: "OwnPostDetailViewModel")
    private let prefs = IOSPreferencesRepository()
    private let authRepository: AuthRepository
    private let postRepository: PostRepository
    private let deletePostUseCase: DeletePostUseCase
    
    let postId: String
    
    @Published var showDeleteDialog = false
    @Published var isDeletingPost = false
    @Published var deleteError: String?
    
    init(postId: String) {
        self.postId = postId
        self.authRepository = IOSAuthRepository(preferencesRepository: prefs)
        let userRepository = FirestoreUserRepository(authRepository: authRepository)
        self.postRepository = FirestorePostRepository(authRepository: authRepository, userRepository: userRepository)
        self.deletePostUseCase = DeletePostUseCase(postRepository: postRepository)
    }
    
    func showDeleteConfirmation() {
        showDeleteDialog = true
        deleteError = nil
    }
    
    func dismissDeleteDialog() {
        showDeleteDialog = false
        deleteError = nil
    }
    
    func deletePost() async {
        guard !isDeletingPost else { return }
        
        Self.log.info("deletePost: deleting postId=\(self.postId)")
        isDeletingPost = true
        deleteError = nil
        
        do {
            try await deletePostUseCase.invoke(postId: postId)
            Self.log.info("deletePost: post deleted successfully")
            isDeletingPost = false
            showDeleteDialog = false
        } catch {
            Self.log.error("deletePost: failed - \(error.localizedDescription)")
            isDeletingPost = false
            deleteError = error.localizedDescription
        }
    }
}
