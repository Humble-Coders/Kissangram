import SwiftUI
import os.log
import Shared

private let ownPostDetailViewLog = Logger(subsystem: "com.kissangram", category: "OwnPostDetailView")

struct OwnPostDetailView: View {
    let postId: String
    let initialPost: Post?
    let onBackClick: () -> Void
    let onNavigateToProfile: (String) -> Void
    
    @StateObject private var commentsViewModel: CommentsViewModel
    @StateObject private var ownPostViewModel: OwnPostDetailViewModel
    @State private var currentUserId: String?
    @State private var showErrorAlert = false
    @State private var showDeletePostAlert = false
    
    init(postId: String, initialPost: Post? = nil, onBackClick: @escaping () -> Void, onNavigateToProfile: @escaping (String) -> Void) {
        self.postId = postId
        self.initialPost = initialPost
        self.onBackClick = onBackClick
        self.onNavigateToProfile = onNavigateToProfile
        _commentsViewModel = StateObject(wrappedValue: CommentsViewModel(postId: postId, initialPost: initialPost))
        _ownPostViewModel = StateObject(wrappedValue: OwnPostDetailViewModel(postId: postId))
    }
    
    var body: some View {
        mainContent
            .sheet(isPresented: $ownPostViewModel.showDeleteDialog) {
                deletePostSheet
            }
            .sheet(isPresented: $commentsViewModel.showDeleteDialog) {
                deleteCommentSheet
            }
            .onAppear {
                loadCurrentUserId()
            }
            .alert("Error", isPresented: $showErrorAlert, presenting: currentError) { error in
                Button("OK") {
                    // Error will be cleared by ViewModel
                }
            } message: { error in
                Text(error)
            }
            .onChange(of: commentsViewModel.error) { error in
                showErrorAlert = error != nil
            }
            .onChange(of: ownPostViewModel.deleteError) { error in
                showErrorAlert = error != nil
            }
    }
    
    private var mainContent: some View {
        NavigationStack {
            VStack(spacing: 0) {
                commentsScrollView
                inputBar
            }
            .background(Color.appBackground)
            .navigationTitle("Post")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: onBackClick) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(.textPrimary)
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    deleteButton
                }
            }
        }
    }
    
    private var deleteButton: some View {
        Button(action: { ownPostViewModel.showDeleteConfirmation() }) {
            if ownPostViewModel.isDeletingPost {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: .textPrimary))
            } else {
                Image(systemName: "trash")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(.errorRed)
            }
        }
        .disabled(ownPostViewModel.isDeletingPost)
    }
    
    private var deletePostSheet: some View {
        DeletePostSheet(
            isDeleting: ownPostViewModel.isDeletingPost,
            onConfirm: {
                Task {
                    await ownPostViewModel.deletePost()
                    if ownPostViewModel.deleteError == nil {
                        onBackClick()
                    }
                }
            },
            onDismiss: { ownPostViewModel.dismissDeleteDialog() }
        )
    }
    
    private var deleteCommentSheet: some View {
        DeleteCommentSheet(
            reason: $commentsViewModel.deleteReason,
            onReasonChange: { commentsViewModel.onDeleteReasonChange($0) },
            onConfirm: { Task { await commentsViewModel.deleteComment() } },
            onDismiss: { commentsViewModel.dismissDeleteDialog() }
        )
    }
    
    private var currentError: String? {
        commentsViewModel.error ?? ownPostViewModel.deleteError
    }
    
    private func loadCurrentUserId() {
        Task {
            currentUserId = try? await commentsViewModel.getCurrentUserId()
        }
    }
    
    private var commentsScrollView: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 0) {
                    postHeaderSection
                    replyIndicator
                    commentsSection(proxy: proxy)
                }
                .padding(.bottom, 20)
            }
            .scrollDismissesKeyboard(.interactively)
            .onChange(of: commentsViewModel.comments.count) { newCount in
                handleCommentsCountChange(newCount: newCount, proxy: proxy)
            }
        }
    }
    
    @ViewBuilder
    private var postHeaderSection: some View {
        if let post = commentsViewModel.post {
            PostHeaderSection(
                post: post,
                onAuthorClick: { onNavigateToProfile(post.authorId) },
                onLikeClick: { commentsViewModel.onLikePost(postId: post.id) }
            )
            .id("post_header")
        }
    }
    
    @ViewBuilder
    private var replyIndicator: some View {
        if let replyingTo = commentsViewModel.replyingToComment {
            ReplyIndicator(
                comment: replyingTo,
                onCancel: { commentsViewModel.cancelReply() }
            )
        }
    }
    
    private func commentsSection(proxy: ScrollViewProxy) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            // Comments Heading
            Text("Comments (\(commentsViewModel.comments.count))")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.textPrimary)
                .padding(.horizontal, 16)
                .padding(.top, 20)
                .padding(.bottom, 12)
            
            if commentsViewModel.isLoading && commentsViewModel.comments.isEmpty {
                Spacer()
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: .primaryGreen))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 60)
            } else if commentsViewModel.comments.isEmpty && !commentsViewModel.isLoading {
                VStack(spacing: 16) {
                    Image(systemName: "bubble.left.and.bubble.right")
                        .font(.system(size: 56))
                        .foregroundColor(.textSecondary.opacity(0.3))
                    Text("No comments yet")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(.textPrimary)
                    Text("Be the first to comment!")
                        .font(.system(size: 15))
                        .foregroundColor(.textSecondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 60)
            } else {
                ForEach(commentsViewModel.comments, id: \.id) { comment in
                    CommentRow(
                        comment: comment,
                        currentUserId: currentUserId,
                        isTopLevel: true,
                        onAuthorClick: { onNavigateToProfile(comment.authorId) },
                        onReplyClick: { commentsViewModel.startReply(comment) },
                        onDeleteClick: { commentsViewModel.showDeleteConfirmation(comment) },
                        onViewRepliesClick: {
                            if comment.repliesCount > 0 {
                                commentsViewModel.toggleReplies(parentCommentId: comment.id)
                            }
                        },
                        isRepliesExpanded: commentsViewModel.expandedReplies.contains(comment.id),
                        replies: commentsViewModel.repliesByParentId[comment.id] ?? [],
                        isLoadingReplies: commentsViewModel.loadingRepliesFor.contains(comment.id),
                        currentUserIdForReplies: currentUserId,
                        onAuthorClickForReplies: onNavigateToProfile,
                        onReplyClickForReplies: { commentsViewModel.startReply($0) },
                        onDeleteClickForReplies: { commentsViewModel.showDeleteConfirmation($0) }
                    )
                }
                
                if commentsViewModel.isLoadingMore {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .primaryGreen))
                        .frame(maxWidth: .infinity)
                        .padding(16)
                }
            }
        }
    }
    
    private var inputBar: some View {
        CommentInputBar(
            text: commentsViewModel.newCommentText,
            replyingTo: commentsViewModel.replyingToComment,
            isPosting: commentsViewModel.isPostingComment,
            isListening: commentsViewModel.isListening,
            isProcessing: commentsViewModel.isProcessing,
            onTextChange: { commentsViewModel.onCommentTextChange($0) },
            onPostClick: { Task { await commentsViewModel.postComment() } },
            onCancelReply: { commentsViewModel.cancelReply() },
            onStartSpeech: { Task { await commentsViewModel.startSpeechRecognition() } },
            onStopSpeech: { Task { await commentsViewModel.stopSpeechRecognition() } }
        )
    }
    
    private func handleCommentsCountChange(newCount: Int, proxy: ScrollViewProxy) {
        if newCount > 0 && !commentsViewModel.isPostingComment {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                withAnimation {
                    proxy.scrollTo("post_header", anchor: .top)
                }
            }
        }
    }
}

struct DeletePostSheet: View {
    let isDeleting: Bool
    let onConfirm: () -> Void
    let onDismiss: () -> Void
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                Text("Are you sure you want to delete this post? This action cannot be undone.")
                    .font(.system(size: 16))
                    .foregroundColor(.textPrimary)
                    .multilineTextAlignment(.center)
                    .padding()
                
                Spacer()
            }
            .padding()
            .navigationTitle("Delete Post")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        onDismiss()
                    }
                    .disabled(isDeleting)
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Delete") {
                        onConfirm()
                    }
                    .foregroundColor(.errorRed)
                    .fontWeight(.semibold)
                    .disabled(isDeleting)
                }
            }
        }
        .presentationDetents([.height(200)])
    }
}
