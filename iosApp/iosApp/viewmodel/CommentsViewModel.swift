import Foundation
import os.log
import Shared
import UIKit

@MainActor
class CommentsViewModel: ObservableObject {
    
    private static let log = Logger(subsystem: "com.kissangram", category: "CommentsViewModel")
    private let prefs = IOSPreferencesRepository()
    private let authRepository: AuthRepository
    private let postRepository: PostRepository
    private let userRepository: UserRepository
    
    private let getCommentsUseCase: GetCommentsUseCase
    private let addCommentUseCase: AddCommentUseCase
    private let deleteCommentUseCase: DeleteCommentUseCase
    private let likePostUseCase: LikePostUseCase
    private let deletePostUseCase: DeletePostUseCase
    
    let postId: String
    
    @Published var post: Post?
    @Published var comments: [Comment] = []
    @Published var isLoading = false
    @Published var isLoadingMore = false
    @Published var error: String?
    @Published var hasMoreComments = true
    @Published var newCommentText = ""
    @Published var isPostingComment = false
    @Published var replyingToComment: Comment?
    @Published var showDeleteDialog = false
    @Published var selectedCommentForDelete: Comment?
    @Published var deleteReason = ""
    @Published var expandedReplies: Set<String> = []
    @Published var repliesByParentId: [String: [Comment]] = [:]
    @Published var loadingRepliesFor: Set<String> = []
    @Published var isListening = false
    @Published var isProcessing = false
    @Published var showDeletePostDialog = false
    @Published var isDeletingPost = false
    @Published var deletePostError: String?
    
    private var currentPage: Int32 = 0
    private let speechRepository = IOSSpeechRepository()
    private let pageSize: Int32 = 20
    private var postsBeingProcessed = Set<String>() // Track ongoing like operations
    
    init(postId: String, initialPost: Post? = nil) {
        self.postId = postId
        self.authRepository = IOSAuthRepository(preferencesRepository: prefs)
        self.userRepository = FirestoreUserRepository(authRepository: authRepository)
        self.postRepository = FirestorePostRepository(authRepository: authRepository, userRepository: userRepository)
        
        self.getCommentsUseCase = GetCommentsUseCase(postRepository: postRepository)
        self.addCommentUseCase = AddCommentUseCase(postRepository: postRepository)
        self.deleteCommentUseCase = DeleteCommentUseCase(postRepository: postRepository)
        self.likePostUseCase = LikePostUseCase(postRepository: postRepository)
        self.deletePostUseCase = DeletePostUseCase(postRepository: postRepository)
        
        // Set initial post if provided
        if let initialPost = initialPost {
            self.post = initialPost
            Self.log.debug("CommentsViewModel init with initial post for postId=\(postId)")
        } else {
            Self.log.debug("CommentsViewModel init for postId=\(postId), will load post")
        }
        
        Task {
            // Only load post if not provided
            if initialPost == nil {
                await loadPost()
            }
            await loadComments()
        }
    }
    
    func getCurrentUserId() async throws -> String? {
        return try await authRepository.getCurrentUserId()
    }
    
    private func loadPost() async {
        do {
            post = try await postRepository.getPost(postId: postId)
        } catch {
            Self.log.error("loadPost: error=\(error.localizedDescription)")
            self.error = "Failed to load post: \(error.localizedDescription)"
        }
    }
    
    func loadComments() async {
        Self.log.debug("loadComments: start")
        isLoading = true
        error = nil
        currentPage = 0
        
        do {
            let loadedComments = try await getCommentsUseCase.invoke(postId: postId, page: 0, pageSize: pageSize)
            Self.log.debug("loadComments: loaded \(loadedComments.count) comments")
            self.comments = loadedComments
            self.isLoading = false
            self.hasMoreComments = loadedComments.count >= Int(pageSize)
        } catch {
            Self.log.error("loadComments: error=\(error.localizedDescription)")
            self.isLoading = false
            self.error = "Failed to load comments: \(error.localizedDescription)"
        }
    }
    
    func loadMoreComments() async {
        guard !isLoadingMore, hasMoreComments else { return }
        
        Self.log.debug("loadMoreComments: loading page \(self.currentPage + 1)")
        isLoadingMore = true
        
        do {
            let nextPage = self.currentPage + 1
            let loadedComments = try await getCommentsUseCase.invoke(postId: postId, page: Int32(nextPage), pageSize: pageSize)
            Self.log.debug("loadMoreComments: loaded \(loadedComments.count) comments")
            
            if !loadedComments.isEmpty {
                self.comments.append(contentsOf: loadedComments)
                self.isLoadingMore = false
                self.hasMoreComments = loadedComments.count >= Int(pageSize)
                self.currentPage = nextPage
            } else {
                self.isLoadingMore = false
                self.hasMoreComments = false
            }
        } catch {
            Self.log.error("loadMoreComments: error=\(error.localizedDescription)")
            self.isLoadingMore = false
            self.error = "Failed to load more comments: \(error.localizedDescription)"
        }
    }
    
    func onCommentTextChange(_ text: String) {
        newCommentText = text
    }
    
    func startSpeechRecognition() async {
        guard !isListening, !isProcessing else { return }
        
        isListening = true
        error = nil
        
        let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
        impactFeedback.impactOccurred()
        
        do {
            if !speechRepository.hasPermission() {
                let granted = try await speechRepository.requestPermission()
                if !granted.boolValue {
                    isListening = false
                    error = "Speech recognition permission is required"
                    return
                }
            }
            
            Task {
                do {
                    try await speechRepository.startListeningWithUpdates { [weak self] recognizedText in
                        guard let self = self else { return }
                        self.newCommentText = recognizedText.trimmingCharacters(in: .whitespaces)
                    }
                } catch {
                    if !error.localizedDescription.contains("cancelled") {
                        self.error = (error as? Error)?.localizedDescription ?? "Speech recognition error"
                    }
                    self.isListening = false
                }
            }
        } catch {
            self.error = (error as? Error)?.localizedDescription ?? "Speech recognition error"
            isListening = false
        }
    }
    
    func stopSpeechRecognition() async {
        guard isListening else { return }
        
        let impactFeedback = UIImpactFeedbackGenerator(style: .light)
        impactFeedback.impactOccurred()
        
        isListening = false
        isProcessing = true
        
        let initialText = speechRepository.stopListeningSync()
        newCommentText = initialText.trimmingCharacters(in: .whitespaces)
        
        try? await Task.sleep(nanoseconds: 3_500_000_000)
        
        let finalText = speechRepository.getAccumulatedText()
        newCommentText = finalText.trimmingCharacters(in: .whitespaces)
        
        isProcessing = false
    }
    
    func postComment() async {
        let text = newCommentText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !isPostingComment else { return }
        
        let replyingTo = replyingToComment
        let parentCommentId = replyingTo?.id
        
        Self.log.debug("postComment: posting comment, text=\(text.prefix(50))..., parentCommentId=\(parentCommentId ?? "nil")")
        isPostingComment = true
        error = nil
        
        // Build optimistic comment (before try so it's in scope for catch)
        let currentUser = try? await userRepository.getCurrentUser()
        let currentUserId = (try? await authRepository.getCurrentUserId()) ?? ""
        let optimisticComment = Comment(
                id: "temp_\(Int64(Date().timeIntervalSince1970 * 1000))",
                postId: postId,
                authorId: currentUserId,
                authorName: currentUser?.name ?? "",
                authorUsername: currentUser?.username ?? "",
                authorProfileImageUrl: currentUser?.profileImageUrl,
                authorRole: currentUser?.role ?? UserRole.farmer,
                authorVerificationStatus: currentUser?.verificationStatus ?? VerificationStatus.unverified,
                text: text,
                voiceComment: nil,
                parentCommentId: parentCommentId,
                repliesCount: Int32(0),
                likesCount: Int32(0),
                isLikedByMe: false,
                isExpertAnswer: false,
                isBestAnswer: false,
                createdAt: Int64(Date().timeIntervalSince1970 * 1000)
        )
        
        do {
            if parentCommentId == nil {
                self.comments = [optimisticComment] + comments
            } else {
                var existingReplies = repliesByParentId[parentCommentId!] ?? []
                existingReplies.append(optimisticComment)
                self.repliesByParentId[parentCommentId!] = existingReplies
                self.expandedReplies.insert(parentCommentId!)
                // Update parent's repliesCount in comments list
                if let idx = comments.firstIndex(where: { $0.id == parentCommentId }) {
                    let parent = comments[idx]
                    var updatedCommentsList = comments
                    updatedCommentsList[idx] = Comment(
                        id: parent.id,
                        postId: parent.postId,
                        authorId: parent.authorId,
                        authorName: parent.authorName,
                        authorUsername: parent.authorUsername,
                        authorProfileImageUrl: parent.authorProfileImageUrl,
                        authorRole: parent.authorRole,
                        authorVerificationStatus: parent.authorVerificationStatus,
                        text: parent.text,
                        voiceComment: parent.voiceComment,
                        parentCommentId: parent.parentCommentId,
                        repliesCount: parent.repliesCount + 1,
                        likesCount: parent.likesCount,
                        isLikedByMe: parent.isLikedByMe,
                        isExpertAnswer: parent.isExpertAnswer,
                        isBestAnswer: parent.isBestAnswer,
                        createdAt: parent.createdAt
                    )
                    self.comments = updatedCommentsList
                } else {
                    self.comments = comments
                }
            }
            self.newCommentText = ""
            self.replyingToComment = nil
            
            // Actually post the comment
            let createdComment = try await addCommentUseCase.invoke(postId: postId, text: text, parentCommentId: parentCommentId)
            Self.log.debug("postComment: comment created with id=\(createdComment.id)")
            
            // Replace optimistic comment with real one
            if parentCommentId == nil {
                self.comments = comments.map { $0.id == optimisticComment.id ? createdComment : $0 }
            } else {
                var parentReplies = repliesByParentId[parentCommentId!] ?? []
                if let idx = parentReplies.firstIndex(where: { $0.id == optimisticComment.id }) {
                    parentReplies[idx] = createdComment
                    self.repliesByParentId[parentCommentId!] = parentReplies
                }
            }
            self.isPostingComment = false
        } catch {
            Self.log.error("postComment: error=\(error.localizedDescription)")
            // Revert optimistic update
            if parentCommentId == nil {
                self.comments = comments.filter { $0.id != optimisticComment.id }
            } else {
                var parentReplies = repliesByParentId[parentCommentId!] ?? []
                parentReplies.removeAll { $0.id == optimisticComment.id }
                if parentReplies.isEmpty {
                    repliesByParentId.removeValue(forKey: parentCommentId!)
                } else {
                    repliesByParentId[parentCommentId!] = parentReplies
                }
                if let idx = comments.firstIndex(where: { $0.id == parentCommentId }) {
                    let parent = comments[idx]
                    var list = comments
                    list[idx] = Comment(
                        id: parent.id,
                        postId: parent.postId,
                        authorId: parent.authorId,
                        authorName: parent.authorName,
                        authorUsername: parent.authorUsername,
                        authorProfileImageUrl: parent.authorProfileImageUrl,
                        authorRole: parent.authorRole,
                        authorVerificationStatus: parent.authorVerificationStatus,
                        text: parent.text,
                        voiceComment: parent.voiceComment,
                        parentCommentId: parent.parentCommentId,
                        repliesCount: max(0, parent.repliesCount - 1),
                        likesCount: parent.likesCount,
                        isLikedByMe: parent.isLikedByMe,
                        isExpertAnswer: parent.isExpertAnswer,
                        isBestAnswer: parent.isBestAnswer,
                        createdAt: parent.createdAt
                    )
                    self.comments = list
                }
            }
            self.isPostingComment = false
            self.error = "Failed to post comment: \(error.localizedDescription)"
            self.newCommentText = text
        }
    }
    
    func loadReplies(parentCommentId: String) async {
        guard !loadingRepliesFor.contains(parentCommentId) else { return }
        
        await MainActor.run { loadingRepliesFor.insert(parentCommentId) }
        
        do {
            let replies = try await postRepository.getReplies(postId: postId, parentCommentId: parentCommentId, page: 0, pageSize: 50)
            await MainActor.run {
                let currentReplies = repliesByParentId[parentCommentId] ?? []
                var seen = Set<String>()
                let merged = (currentReplies + replies).filter { seen.insert($0.id).inserted }
                repliesByParentId[parentCommentId] = merged
                expandedReplies.insert(parentCommentId)
                loadingRepliesFor.remove(parentCommentId)
            }
        } catch {
            Self.log.error("loadReplies: error=\(error.localizedDescription)")
            await MainActor.run {
                loadingRepliesFor.remove(parentCommentId)
                self.error = "Failed to load replies: \(error.localizedDescription)"
            }
        }
    }
    
    func toggleReplies(parentCommentId: String) {
        if expandedReplies.contains(parentCommentId) {
            expandedReplies.remove(parentCommentId)
        } else {
            Task { await loadReplies(parentCommentId: parentCommentId) }
        }
    }
    
    func startReply(_ comment: Comment) {
        replyingToComment = comment
    }
    
    func cancelReply() {
        replyingToComment = nil
    }
    
    func showDeleteConfirmation(_ comment: Comment) {
        showDeleteDialog = true
        selectedCommentForDelete = comment
        deleteReason = ""
    }
    
    func dismissDeleteDialog() {
        showDeleteDialog = false
        selectedCommentForDelete = nil
        deleteReason = ""
    }
    
    func onDeleteReasonChange(_ reason: String) {
        deleteReason = reason
    }
    
    func deleteComment() async {
        guard let comment = selectedCommentForDelete else { return }
        let reason = deleteReason.trimmingCharacters(in: .whitespacesAndNewlines)
        
        guard !reason.isEmpty else {
            error = "Please provide a reason for deletion"
            return
        }
        
        Self.log.debug("deleteComment: deleting commentId=\(comment.id), reason=\(reason.prefix(50))...")
        error = nil
        
        do {
            // Optimistic update: remove comment from list immediately
            let updatedComments = comments.filter { $0.id != comment.id }
            self.comments = updatedComments
            self.showDeleteDialog = false
            self.selectedCommentForDelete = nil
            self.deleteReason = ""
            
            // Actually delete the comment
            try await deleteCommentUseCase.invoke(postId: postId, commentId: comment.id, reason: reason)
            Self.log.debug("deleteComment: comment deleted successfully")
        } catch {
            Self.log.error("deleteComment: error=\(error.localizedDescription)")
            // Revert optimistic update
            self.error = "Failed to delete comment: \(error.localizedDescription)"
            self.comments.append(comment) // Restore comment
        }
    }
    
    /**
     * Like or unlike a post. Returns true if the request was accepted, false if already processing.
     * Uses the same pattern as HomeViewModel for consistency.
     */
    func onLikePost(postId: String) -> Bool {
        // Prevent multiple simultaneous requests for the same post
        if postsBeingProcessed.contains(postId) {
            Self.log.debug("onLikePost: post \(postId) is already being processed, ignoring")
            return false
        }
        
        guard let currentPost = post, currentPost.id == postId else {
            Self.log.warning("onLikePost: post not found or mismatch, postId=\(postId)")
            return false
        }
        
        let newLikedState = !currentPost.isLikedByMe
        let newLikesCount = newLikedState ? currentPost.likesCount + 1 : currentPost.likesCount - 1
        
        // Mark as being processed
        postsBeingProcessed.insert(postId)
        
        // ⚡ INSTAGRAM APPROACH: Update UI IMMEDIATELY (synchronous, main thread)
        // This happens before any async work, so user sees instant feedback
        let updatedPost = Post(
            id: currentPost.id,
            authorId: currentPost.authorId,
            authorName: currentPost.authorName,
            authorUsername: currentPost.authorUsername,
            authorProfileImageUrl: currentPost.authorProfileImageUrl,
            authorRole: currentPost.authorRole,
            authorVerificationStatus: currentPost.authorVerificationStatus,
            type: currentPost.type,
            text: currentPost.text,
            media: currentPost.media,
            voiceCaption: currentPost.voiceCaption,
            crops: currentPost.crops,
            hashtags: currentPost.hashtags,
            location: currentPost.location,
            question: currentPost.question,
            likesCount: newLikesCount,
            commentsCount: currentPost.commentsCount,
            savesCount: currentPost.savesCount,
            isLikedByMe: newLikedState,
            isSavedByMe: currentPost.isSavedByMe,
            createdAt: currentPost.createdAt,
            updatedAt: currentPost.updatedAt
        )
        self.post = updatedPost
        
        // Fire network request in background (non-blocking)
        Task {
            do {
                try await likePostUseCase.invoke(postId: postId, isCurrentlyLiked: currentPost.isLikedByMe)
                Self.log.debug("onLikePost: SUCCESS - postId=\(postId), isLiked=\(newLikedState)")
            } catch {
                Self.log.error("onLikePost: FAILED for postId=\(postId), error=\(error.localizedDescription)")
                // Revert on failure
                self.post = currentPost
            }
            // Always remove from processing set
            postsBeingProcessed.remove(postId)
        }
        return true
    }
    
    func showDeletePostConfirmation() {
        showDeletePostDialog = true
        deletePostError = nil
    }
    
    func dismissDeletePostDialog() {
        showDeletePostDialog = false
        deletePostError = nil
    }
    
    func deletePost() async {
        guard !isDeletingPost else { return }
        
        Self.log.info("deletePost: deleting postId=\(self.postId)")
        isDeletingPost = true
        deletePostError = nil
        
        do {
            try await deletePostUseCase.invoke(postId: postId)
            Self.log.info("deletePost: post deleted successfully")
            isDeletingPost = false
            showDeletePostDialog = false
        } catch {
            Self.log.error("deletePost: failed - \(error.localizedDescription)")
            isDeletingPost = false
            deletePostError = error.localizedDescription
        }
    }
}
 
