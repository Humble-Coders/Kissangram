import Foundation
import os.log
import Shared

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
    
    private var currentPage: Int32 = 0
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
    
    func postComment() async {
        let text = newCommentText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !isPostingComment else { return }
        
        let replyingTo = replyingToComment
        let parentCommentId = replyingTo?.id
        
        Self.log.debug("postComment: posting comment, text=\(text.prefix(50))..., parentCommentId=\(parentCommentId ?? "nil")")
        isPostingComment = true
        error = nil
        
        do {
            // Get current user for optimistic update
            let currentUser = try await userRepository.getCurrentUser()
            let currentUserId = try await authRepository.getCurrentUserId() ?? ""
            
            // Optimistic update: add comment to list immediately
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
            
            let updatedComments: [Comment]
            if parentCommentId == nil {
                // Top-level comment: add at the beginning
                updatedComments = [optimisticComment] + comments
            } else {
                // Reply: for now, just add to list (we'll handle nested display later)
                updatedComments = comments + [optimisticComment]
            }
            
            self.comments = updatedComments
            self.newCommentText = ""
            self.replyingToComment = nil
            
            // Actually post the comment
            let createdComment = try await addCommentUseCase.invoke(postId: postId, text: text, parentCommentId: parentCommentId)
            Self.log.debug("postComment: comment created with id=\(createdComment.id)")
            
            // Replace optimistic comment with real one
            let finalComments = updatedComments.map { comment in
                comment.id == optimisticComment.id ? createdComment : comment
            }
            
            self.comments = finalComments
            self.isPostingComment = false
        } catch {
            Self.log.error("postComment: error=\(error.localizedDescription)")
            // Revert optimistic update
            self.isPostingComment = false
            self.error = "Failed to post comment: \(error.localizedDescription)"
            self.newCommentText = text // Restore text so user can retry
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
}
 
