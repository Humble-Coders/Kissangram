import SwiftUI
import os.log
import Shared
import Combine
import UIKit

private let commentsViewLog = Logger(subsystem: "com.kissangram", category: "CommentsView")

struct CommentsView: View {
    let postId: String
    let initialPost: Post?
    let onBackClick: () -> Void
    let onNavigateToProfile: (String) -> Void
    
    @StateObject private var viewModel: CommentsViewModel
    @State private var currentUserId: String?
    @State private var showErrorAlert = false
    
    init(postId: String, initialPost: Post? = nil, onBackClick: @escaping () -> Void, onNavigateToProfile: @escaping (String) -> Void) {
        self.postId = postId
        self.initialPost = initialPost
        self.onBackClick = onBackClick
        self.onNavigateToProfile = onNavigateToProfile
        _viewModel = StateObject(wrappedValue: CommentsViewModel(postId: postId, initialPost: initialPost))
    }
    
    var body: some View {
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
                    if let post = viewModel.post, let currentUserId = currentUserId, post.authorId == currentUserId {
                        Button(action: { viewModel.showDeletePostConfirmation() }) {
                            if viewModel.isDeletingPost {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .textPrimary))
                            } else {
                                Image(systemName: "trash")
                                    .font(.system(size: 18, weight: .semibold))
                                    .foregroundColor(.errorRed)
                            }
                        }
                        .disabled(viewModel.isDeletingPost)
                    } else {
                        Button(action: { /* Menu */ }) {
                            Image(systemName: "ellipsis")
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundColor(.textPrimary)
                        }
                    }
                }
            }
        }
        .sheet(isPresented: $viewModel.showDeleteDialog) {
            DeleteCommentSheet(
                reason: $viewModel.deleteReason,
                onReasonChange: { viewModel.onDeleteReasonChange($0) },
                onConfirm: { Task { await viewModel.deleteComment() } },
                onDismiss: { viewModel.dismissDeleteDialog() }
            )
        }
        .sheet(isPresented: $viewModel.showDeletePostDialog) {
            DeletePostSheet(
                isDeleting: viewModel.isDeletingPost,
                onConfirm: {
                    Task {
                        await viewModel.deletePost()
                        if viewModel.deletePostError == nil {
                            onBackClick()
                        }
                    }
                },
                onDismiss: { viewModel.dismissDeletePostDialog() }
            )
        }
        .onAppear {
            Task {
                currentUserId = try? await viewModel.getCurrentUserId()
            }
        }
        .alert("Error", isPresented: $showErrorAlert, presenting: viewModel.error) { error in
            Button("OK") {
                // Error will be cleared by ViewModel
            }
        } message: { error in
            Text(error)
        }
        .onChange(of: viewModel.error) { error in
            showErrorAlert = error != nil
        }
        .onChange(of: viewModel.deletePostError) { error in
            showErrorAlert = error != nil
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
            .onChange(of: viewModel.comments.count) { newCount in
                handleCommentsCountChange(newCount: newCount, proxy: proxy)
            }
        }
    }
    
    @ViewBuilder
    private var postHeaderSection: some View {
        if let post = viewModel.post {
            PostHeaderSection(
                post: post,
                onAuthorClick: { onNavigateToProfile(post.authorId) },
                onLikeClick: { viewModel.onLikePost(postId: post.id) }
            )
            .id("post_header")
        }
    }
    
    @ViewBuilder
    private var replyIndicator: some View {
        if let replyingTo = viewModel.replyingToComment {
            ReplyIndicator(
                comment: replyingTo,
                onCancel: {
                    viewModel.cancelReply()
                }
            )
            .id("reply_indicator")
            .transition(.asymmetric(
                insertion: .move(edge: .top).combined(with: .opacity),
                removal: .move(edge: .top).combined(with: .opacity)
            ))
        }
    }
    
    @ViewBuilder
    private func commentsSection(proxy: ScrollViewProxy) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Comments (\(viewModel.comments.count))")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.textPrimary)
                .padding(.horizontal, 16)
                .padding(.top, 20)
                .padding(.bottom, 12)
            
            if viewModel.isLoading && viewModel.comments.isEmpty {
                loadingView
            } else if viewModel.comments.isEmpty && !viewModel.isLoading {
                emptyStateView
            } else {
                commentsList
            }
        }
    }
    
    private var loadingView: some View {
        VStack {
            Spacer()
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: .primaryGreen))
                .scaleEffect(1.2)
            Spacer()
        }
        .frame(height: 200)
    }
    
    private var emptyStateView: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "bubble.left.and.bubble.right")
                .font(.system(size: 56))
                .foregroundColor(.textSecondary.opacity(0.3))
            Text("No comments yet")
                .font(.system(size: 17, weight: .semibold))
                .foregroundColor(.textPrimary)
            Text("Be the first to comment!")
                .font(.system(size: 15))
                .foregroundColor(.textSecondary)
            Spacer()
        }
        .frame(maxWidth: .infinity)
        .frame(height: 200)
    }
    
    private var commentsList: some View {
        LazyVStack(spacing: 0) {
            ForEach(Array(viewModel.comments.enumerated()), id: \.element.id) { index, comment in
                VStack(alignment: .leading, spacing: 0) {
                    CommentRow(
                        comment: comment,
                        currentUserId: currentUserId,
                        isTopLevel: true,
                        onAuthorClick: { onNavigateToProfile(comment.authorId) },
                        onReplyClick: { handleReplyClick(comment: comment) },
                        onDeleteClick: { viewModel.showDeleteConfirmation(comment) },
                        onViewRepliesClick: {
                            if comment.repliesCount > 0 {
                                viewModel.toggleReplies(parentCommentId: comment.id)
                            }
                        },
                        isRepliesExpanded: viewModel.expandedReplies.contains(comment.id),
                        replies: viewModel.repliesByParentId[comment.id] ?? [],
                        isLoadingReplies: viewModel.loadingRepliesFor.contains(comment.id),
                        currentUserIdForReplies: currentUserId,
                        onAuthorClickForReplies: onNavigateToProfile,
                        onReplyClickForReplies: { handleReplyClick(comment: $0) },
                        onDeleteClickForReplies: { viewModel.showDeleteConfirmation($0) }
                    )
                    
                    if index < viewModel.comments.count - 1 {
                        Rectangle()
                            .fill(Color.black.opacity(0.06))
                            .frame(height: 0.5)
                            .padding(.leading, 69)
                            .padding(.vertical, 12)
                    }
                }
                .id(comment.id)
                .onAppear {
                    handleCommentAppear(index: index)
                }
            }
            
            if viewModel.isLoadingMore {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: .primaryGreen))
                    .padding(.vertical, 20)
            }
        }
        .padding(.horizontal, 16)
    }
    
    private var inputBar: some View {
        CommentInputBar(
            text: viewModel.newCommentText,
            replyingTo: viewModel.replyingToComment,
            isPosting: viewModel.isPostingComment,
            isListening: viewModel.isListening,
            isProcessing: viewModel.isProcessing,
            onTextChange: { viewModel.onCommentTextChange($0) },
            onPostClick: { handlePostClick() },
            onCancelReply: { viewModel.cancelReply() },
            onStartSpeech: { Task { await viewModel.startSpeechRecognition() } },
            onStopSpeech: { Task { await viewModel.stopSpeechRecognition() } }
        )
    }
    
    private func handleReplyClick(comment: Comment) {
        let generator = UIImpactFeedbackGenerator(style: .light)
        generator.impactOccurred()
        viewModel.startReply(comment)
    }
    
    private func handlePostClick() {
        let generator = UINotificationFeedbackGenerator()
        generator.notificationOccurred(.success)
        Task {
            await viewModel.postComment()
        }
    }
    
    private func handleCommentAppear(index: Int) {
        if index >= viewModel.comments.count - 3 &&
           viewModel.hasMoreComments &&
           !viewModel.isLoadingMore {
            Task {
                await viewModel.loadMoreComments()
            }
        }
    }
    
    private func handleCommentsCountChange(newCount: Int, proxy: ScrollViewProxy) {
        if newCount > 0 && !viewModel.isPostingComment {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                if let firstComment = viewModel.comments.first {
                    withAnimation(.easeInOut(duration: 0.3)) {
                        proxy.scrollTo(firstComment.id, anchor: .top)
                    }
                }
            }
        }
    }
}

// MARK: - Post Header Section
internal struct PostHeaderSection: View {
    let post: Post
    let onAuthorClick: () -> Void
    let onLikeClick: () -> Bool
    
    @State private var localLikedState: Bool
    @State private var localLikesCount: Int32
    
    init(post: Post, onAuthorClick: @escaping () -> Void, onLikeClick: @escaping () -> Bool) {
        self.post = post
        self.onAuthorClick = onAuthorClick
        self.onLikeClick = onLikeClick
        _localLikedState = State(initialValue: post.isLikedByMe)
        _localLikesCount = State(initialValue: post.likesCount)
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                Button(action: onAuthorClick) {
                    ProfileImageLoader(
                        authorId: post.authorId,
                        authorName: post.authorName,
                        authorProfileImageUrl: post.authorProfileImageUrl,
                        size: 48
                    )
                }
                .buttonStyle(PlainButtonStyle())
                
                VStack(alignment: .leading, spacing: 3) {
                    Text(post.authorName)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.textPrimary)
                    
                    HStack(spacing: 6) {
                        Image(systemName: "location.fill")
                            .font(.system(size: 12))
                            .foregroundColor(.textSecondary)
                        Text(post.location?.name ?? "Location not set")
                            .font(.system(size: 14))
                            .foregroundColor(.textSecondary)
                    }
                }
                
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.top, 16)
            .padding(.bottom, 12)
            
            if !post.media.isEmpty {
                MediaCarousel(
                    media: post.media,
                    onMediaClick: { /* TODO: Open media viewer */ },
                    isVisible: true
                )
                .padding(.horizontal, 16)
            }
            
            HStack(spacing: 0) {
                ActionButton(
                    icon: localLikedState ? "heart.fill" : "heart",
                    label: "\(localLikesCount)",
                    color: localLikedState ? .errorRed : .textSecondary
                ) {
                    let newLikedState = !localLikedState
                    let newLikesCount = newLikedState ? localLikesCount + 1 : localLikesCount - 1
                    let accepted = onLikeClick()
                    if accepted {
                        localLikedState = newLikedState
                        localLikesCount = newLikesCount
                    }
                }
                
                ActionButton(
                    icon: "square.and.arrow.up",
                    label: "Share",
                    color: .textSecondary
                ) {
                    // TODO
                }
                
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .onChange(of: post.isLikedByMe) { newValue in
                localLikedState = newValue
            }
            .onChange(of: post.likesCount) { newValue in
                localLikesCount = newValue
            }
            
            if !post.text.isEmpty || post.voiceCaption != nil {
                PostTextContent(
                    text: post.text,
                    voiceCaption: post.voiceCaption,
                    onReadMore: { /* TODO: Expand text */ }
                )
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            
            if !post.crops.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 9) {
                        ForEach(post.crops, id: \.self) { crop in
                            Text(crop.capitalized)
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(.textPrimary)
                                .lineLimit(1)
                                .padding(.horizontal, 11)
                                .padding(.vertical, 6)
                                .background(Color.accentYellow.opacity(0.08))
                                .cornerRadius(18)
                        }
                    }
                    .padding(.horizontal, 0)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 16)
            }
            
            Rectangle()
                .fill(Color.black.opacity(0.08))
                .frame(height: 8)
        }
    }
}

// MARK: - Comment Row
internal struct CommentRow: View {
    let comment: Comment
    let currentUserId: String?
    let isTopLevel: Bool
    let onAuthorClick: () -> Void
    let onReplyClick: () -> Void
    let onDeleteClick: () -> Void
    var onViewRepliesClick: (() -> Void)? = nil
    var isRepliesExpanded: Bool = false
    var replies: [Comment] = []
    var isLoadingReplies: Bool = false
    var currentUserIdForReplies: String? = nil
    var onAuthorClickForReplies: ((String) -> Void)? = nil
    var onReplyClickForReplies: ((Comment) -> Void)? = nil
    var onDeleteClickForReplies: ((Comment) -> Void)? = nil
    
    private var isOwnComment: Bool {
        currentUserId != nil && comment.authorId == currentUserId
    }
    
    private var horizontalPadding: CGFloat { isTopLevel ? 0 : 53 }
    private var fontSize: CGFloat { isTopLevel ? 15 : 14 }
    private var nameFontSize: CGFloat { isTopLevel ? 15 : 14 }
    private var avatarSize: CGFloat { isTopLevel ? 40 : 32 }
    
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Button(action: onAuthorClick) {
                ProfileImageLoader(
                    authorId: comment.authorId,
                    authorName: comment.authorName,
                    authorProfileImageUrl: comment.authorProfileImageUrl,
                    size: avatarSize
                )
            }
            .buttonStyle(PlainButtonStyle())
            
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    Button(action: onAuthorClick) {
                        Text(comment.authorName)
                            .font(.system(size: nameFontSize, weight: .semibold))
                            .foregroundColor(.textPrimary)
                    }
                    .buttonStyle(PlainButtonStyle())
                    
                    Text(formatTimestamp(comment.createdAt))
                        .font(.system(size: 13))
                        .foregroundColor(.textSecondary.opacity(0.8))
                }
                
                Text(comment.text)
                    .font(.system(size: fontSize))
                    .foregroundColor(.textPrimary)
                    .lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 2)
                
                HStack(spacing: 20) {
                    Button(action: onReplyClick) {
                        Text("Reply")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(.textSecondary)
                    }
                    
                    if isTopLevel, comment.repliesCount > 0, let onView = onViewRepliesClick {
                        Button(action: onView) {
                            if isLoadingReplies {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .textSecondary))
                                    .scaleEffect(0.75)
                            } else {
                                HStack(spacing: 4) {
                                    Text(isRepliesExpanded ? "Hide replies" : "View \(comment.repliesCount) \(comment.repliesCount == 1 ? "reply" : "replies")")
                                        .font(.system(size: 14, weight: .medium))
                                        .foregroundColor(.textSecondary)
                                    Image(systemName: isRepliesExpanded ? "chevron.up" : "chevron.down")
                                        .font(.system(size: 11, weight: .semibold))
                                        .foregroundColor(.textSecondary)
                                }
                            }
                        }
                        .disabled(isLoadingReplies)
                    }
                    
                    if isOwnComment {
                        Button(action: onDeleteClick) {
                            Text("Delete")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(.errorRed)
                        }
                    }
                }
                .padding(.top, 6)
                
                if isTopLevel, isRepliesExpanded, !replies.isEmpty,
                   let onAuthor = onAuthorClickForReplies,
                   let onReply = onReplyClickForReplies,
                   let onDelete = onDeleteClickForReplies {
                    VStack(alignment: .leading, spacing: 0) {
                        ForEach(replies, id: \.id) { reply in
                            CommentRow(
                                comment: reply,
                                currentUserId: currentUserIdForReplies,
                                isTopLevel: false,
                                onAuthorClick: { onAuthor(reply.authorId) },
                                onReplyClick: { onReply(reply) },
                                onDeleteClick: { onDelete(reply) }
                            )
                            .padding(.top, 12)
                        }
                    }
                    .padding(.top, 8)
                }
            }
            
            Spacer(minLength: 0)
        }
        .padding(.horizontal, horizontalPadding)
        .padding(.vertical, 12)
    }
}

// MARK: - Reply Indicator
internal struct ReplyIndicator: View {
    let comment: Comment
    let onCancel: () -> Void
    
    var body: some View {
        HStack(spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "arrowshape.turn.up.left.fill")
                    .font(.system(size: 13))
                    .foregroundColor(.primaryGreen)
                
                Text("Replying to @\(comment.authorUsername)")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.textPrimary)
            }
            
            Spacer()
            
            Button(action: onCancel) {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 20))
                    .foregroundColor(.textSecondary)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color.primaryGreen.opacity(0.08))
    }
}

// MARK: - Comment Input Bar
internal struct CommentInputBar: View {
    let text: String
    let replyingTo: Comment?
    let isPosting: Bool
    let isListening: Bool
    let isProcessing: Bool
    let onTextChange: (String) -> Void
    let onPostClick: () -> Void
    let onCancelReply: () -> Void
    let onStartSpeech: () -> Void
    let onStopSpeech: () -> Void
    @FocusState private var isTextFieldFocused: Bool
    
    var body: some View {
        VStack(spacing: 0) {
            Rectangle()
                .fill(Color.black.opacity(0.06))
                .frame(height: 0.5)
            
            HStack(alignment: .center, spacing: 12) {
                Circle()
                    .fill(isListening ? Color.errorRed : Color.primaryGreen)
                    .frame(width: 44, height: 44)
                    .overlay(
                        Group {
                            if isProcessing {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                    .scaleEffect(0.85)
                            } else {
                                Image(systemName: "mic.fill")
                                    .font(.system(size: 18))
                                    .foregroundColor(.white)
                            }
                        }
                    )
                    .contentShape(Circle())
                    .onLongPressGesture(minimumDuration: 0.1, pressing: { pressing in
                        if pressing {
                            if !isPosting && !isProcessing { onStartSpeech() }
                        } else {
                            onStopSpeech()
                        }
                    }, perform: {})
                    .allowsHitTesting(!isPosting)
                
                TextField(
                    replyingTo != nil ? "Reply to @\(replyingTo!.authorUsername)..." : "Add a comment...",
                    text: Binding(
                        get: { text },
                        set: onTextChange
                    )
                )
                .font(.system(size: 15))
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(Color(red: 0.97, green: 0.97, blue: 0.97))
                .overlay(
                    RoundedRectangle(cornerRadius: 22)
                        .stroke(Color.black.opacity(0.08), lineWidth: 1)
                )
                .cornerRadius(22)
                .focused($isTextFieldFocused)
                .disabled(isPosting)
                .submitLabel(.send)
                .onSubmit {
                    if !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        onPostClick()
                    }
                }
                .onChange(of: replyingTo) { newValue in
                    if newValue != nil {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                            isTextFieldFocused = true
                        }
                    }
                }
                
                Button(action: {
                    onPostClick()
                    isTextFieldFocused = false
                }) {
                    Circle()
                        .fill(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ?
                              Color(red: 0.93, green: 0.93, blue: 0.93) :
                              Color.primaryGreen)
                        .frame(width: 44, height: 44)
                        .overlay(
                            Group {
                                if isPosting {
                                    ProgressView()
                                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                        .scaleEffect(0.85)
                                } else {
                                    Image(systemName: "paperplane.fill")
                                        .font(.system(size: 17))
                                        .foregroundColor(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ?
                                                        .textSecondary.opacity(0.6) : .white)
                                }
                            }
                        )
                }
                .disabled(isPosting || text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
        .background(
            Color.white
                .shadow(color: Color.black.opacity(0.06), radius: 10, x: 0, y: -2)
        )
    }
}

// MARK: - Delete Comment Sheet
internal struct DeleteCommentSheet: View {
    @Binding var reason: String
    let onReasonChange: (String) -> Void
    let onConfirm: () -> Void
    let onDismiss: () -> Void
    
    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 20) {
                Text("Please provide a reason for deleting this comment:")
                    .font(.system(size: 15))
                    .foregroundColor(.textPrimary)
                    .padding(.top, 4)
                
                TextEditor(text: Binding(
                    get: { reason },
                    set: onReasonChange
                ))
                .font(.system(size: 15))
                .padding(14)
                .frame(minHeight: 120)
                .background(Color(red: 0.97, green: 0.97, blue: 0.97))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.black.opacity(0.08), lineWidth: 1)
                )
                .cornerRadius(12)
                
                if reason.isEmpty {
                    Text("Reason is required")
                        .font(.system(size: 13))
                        .foregroundColor(.textSecondary)
                        .padding(.top, -12)
                }
                
                Spacer()
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .navigationTitle("Delete Comment")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        onDismiss()
                    }
                    .font(.system(size: 16))
                    .foregroundColor(.textSecondary)
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Delete") {
                        onConfirm()
                    }
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.errorRed)
                    .disabled(reason.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}

// MARK: - Delete Post Sheet
internal struct DeletePostSheet: View {
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

// MARK: - Helper Functions
internal func formatTimestamp(_ timestamp: Int64) -> String {
    let now = Date()
    let date = Date(timeIntervalSince1970: Double(timestamp) / 1000.0)
    let diff = now.timeIntervalSince(date)
    
    if diff < 60 {
        return "Just now"
    } else if diff < 3600 {
        let minutes = Int(diff / 60)
        return "\(minutes) \(minutes == 1 ? "min" : "mins") ago"
    } else if diff < 86400 {
        let hours = Int(diff / 3600)
        return "\(hours) \(hours == 1 ? "hr" : "hrs") ago"
    } else if diff < 604800 {
        let days = Int(diff / 86400)
        return "\(days) \(days == 1 ? "day" : "days") ago"
    } else {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d"
        return formatter.string(from: date)
    }
}
