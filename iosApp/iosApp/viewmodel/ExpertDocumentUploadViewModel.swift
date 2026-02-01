import Foundation
import SwiftUI
import FirebaseStorage
import FirebaseAuth
import Shared
import UniformTypeIdentifiers

@MainActor
class ExpertDocumentUploadViewModel: ObservableObject {
    @Published var uploadedDocumentURL: String?
    @Published var uploadedFileName: String?
    @Published var isUploading: Bool = false
    @Published var uploadProgress: Double = 0.0
    @Published var error: String?
    
    private let storageBucket = "gs://kissangram-19531.firebasestorage.app"
    
    private let prefs = IOSPreferencesRepository()
    private lazy var authRepo = IOSAuthRepository(preferencesRepository: prefs)
    private lazy var userRepo = FirestoreUserRepository(authRepository: authRepo)
    private lazy var createUserProfileUseCase = CreateUserProfileUseCase(
        authRepository: authRepo,
        preferencesRepository: prefs,
        userRepository: userRepo
    )
    
    func uploadDocument(fileURL: URL, userId: String) async {
        await MainActor.run {
            isUploading = true
            uploadProgress = 0.0
            error = nil
        }
        
        do {
            let fileName = fileURL.lastPathComponent
            let fileExtension = fileURL.pathExtension
            
            let storage = Storage.storage(url: storageBucket)
            let documentRef = storage.reference().child("expert-documents/\(userId)/\(UUID().uuidString).\(fileExtension)")
            
            let fileData = try Data(contentsOf: fileURL)
            
            let metadata = StorageMetadata()
            metadata.contentType = getContentType(for: fileExtension)
            
            let uploadTask = documentRef.putData(fileData, metadata: metadata)
            
            uploadTask.observe(StorageTaskStatus.progress) { [weak self] (snapshot: StorageTaskSnapshot) in
                guard let progress = snapshot.progress else { return }
                let percentComplete = Double(progress.completedUnitCount) / Double(progress.totalUnitCount)
                Task { @MainActor in
                    self?.uploadProgress = percentComplete
                }
            }
            
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                var hasResumed = false
                
                uploadTask.observe(StorageTaskStatus.success) { (_: StorageTaskSnapshot) in
                    if !hasResumed {
                        hasResumed = true
                        continuation.resume()
                    }
                }
                
                uploadTask.observe(StorageTaskStatus.failure) { (snapshot: StorageTaskSnapshot) in
                    if !hasResumed, let err = snapshot.error {
                        hasResumed = true
                        continuation.resume(throwing: err)
                    }
                }
            }
            
            let downloadURL = try await documentRef.downloadURL()
            
            await MainActor.run {
                self.uploadedDocumentURL = downloadURL.absoluteString
                self.uploadedFileName = fileName
                self.isUploading = false
                self.uploadProgress = 1.0
            }
        } catch {
            await MainActor.run {
                self.error = error.localizedDescription
                self.isUploading = false
            }
        }
    }
    
    private func getContentType(for fileExtension: String) -> String {
        switch fileExtension.lowercased() {
        case "pdf": return "application/pdf"
        case "jpg", "jpeg": return "image/jpeg"
        case "png": return "image/png"
        case "doc": return "application/msword"
        case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        default: return "application/octet-stream"
        }
    }
    
    func completeSetup(onSuccess: @escaping () -> Void, onError: @escaping (String) -> Void = { _ in }) {
        Task {
            do {
                let docUrl = uploadedDocumentURL
                try await createUserProfileUseCase.invoke(
                    role: .expert,
                    verificationDocUrl: docUrl,
                    verificationStatus: docUrl != nil ? .pending : .unverified
                )
                try await prefs.setAuthCompleted()
                await MainActor.run {
                    error = nil
                    onSuccess()
                }
            } catch {
                await MainActor.run {
                    self.error = error.localizedDescription
                    onError(error.localizedDescription)
                }
            }
        }
    }
    
    func skipVerification(onSuccess: @escaping () -> Void, onError: @escaping (String) -> Void = { _ in }) {
        Task {
            do {
                try await createUserProfileUseCase.invoke(
                    role: .expert,
                    verificationDocUrl: nil,
                    verificationStatus: .unverified
                )
                try await prefs.setAuthCompleted()
                await MainActor.run {
                    error = nil
                    onSuccess()
                }
            } catch {
                await MainActor.run {
                    self.error = error.localizedDescription
                    onError(error.localizedDescription)
                }
            }
        }
    }
}
