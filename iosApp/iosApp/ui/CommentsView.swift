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
                // Scrollable content
                commentsScrollView
                
                // Bottom Input Bar (fixed at bottom, moves with keyboard automatically)
                inputBar
            }
            .background(Color.appBackground)
            .navigationTitle("Post")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: onBackClick) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundColor(.textPrimary)
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { /* Menu */ }) {
                        Image(systemName: "ellipsis")
                            .font(.system(size: 20))
                            .foregroundColor(.textPrimary)
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
    }
    
    private var commentsScrollView: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 0) {
                    postHeaderSection
                    replyIndicator
                    commentsSection(proxy: proxy)
                }
                .padding(.bottom, 20) // Minimal padding for the input bar
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
            // Comments Heading
            Text("Comments (\(viewModel.comments.count))")
                .font(.system(size: 17, weight: .semibold))
                .foregroundColor(.textPrimary)
                .padding(.horizontal, 18)
                .padding(.vertical, 18)
            
            // Comments List
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
        VStack(spacing: 12) {
            Spacer()
            Image(systemName: "bubble.left.and.bubble.right")
                .font(.system(size: 64))
                .foregroundColor(.textSecondary.opacity(0.5))
            Text("No comments yet")
                .font(.system(size: 18, weight: .medium))
                .foregroundColor(.textSecondary)
            Text("Be the first to comment!")
                .font(.system(size: 14))
                .foregroundColor(.textSecondary.opacity(0.7))
            Spacer()
        }
        .frame(maxWidth: .infinity)
        .frame(height: 200)
    }
    
    private var commentsList: some View {
        LazyVStack(spacing: 18) {
            ForEach(Array(viewModel.comments.enumerated()), id: \.element.id) { index, comment in
                CommentRow(
                    comment: comment,
                    currentUserId: currentUserId,
                    onAuthorClick: { onNavigateToProfile(comment.authorId) },
                    onReplyClick: {
                        handleReplyClick(comment: comment)
                    },
                    onDeleteClick: { viewModel.showDeleteConfirmation(comment) }
                )
                .id(comment.id)
                .onAppear {
                    handleCommentAppear(index: index)
                }
            }
            
            // Loading more indicator
            if viewModel.isLoadingMore {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: .primaryGreen))
                    .padding()
            }
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 8)
    }
    
    private var inputBar: some View {
        CommentInputBar(
            text: viewModel.newCommentText,
            replyingTo: viewModel.replyingToComment,
            isPosting: viewModel.isPostingComment,
            onTextChange: { viewModel.onCommentTextChange($0) },
            onPostClick: {
                handlePostClick()
            },
            onCancelReply: {
                viewModel.cancelReply()
            }
        )
    }
    
    private func handleReplyClick(comment: Comment) {
        // Haptic feedback for native iOS feel
        let generator = UIImpactFeedbackGenerator(style: .light)
        generator.impactOccurred()
        
        viewModel.startReply(comment)
    }
    
    private func handlePostClick() {
        // Haptic feedback for native iOS feel
        let generator = UINotificationFeedbackGenerator()
        generator.notificationOccurred(.success)
        
        Task {
            await viewModel.postComment()
        }
    }
    
    private func handleCommentAppear(index: Int) {
        // Load more when scrolling near bottom
        if index >= viewModel.comments.count - 3 &&
           viewModel.hasMoreComments &&
           !viewModel.isLoadingMore {
            Task {
                await viewModel.loadMoreComments()
            }
        }
    }
    
    private func handleCommentsCountChange(newCount: Int, proxy: ScrollViewProxy) {
        // Auto-scroll to newest comment (first in list) after posting
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
private struct PostHeaderSection: View {
    let post: Post
    let onAuthorClick: () -> Void
    let onLikeClick: () -> Bool
    
    // ⚡ INSTAGRAM APPROACH: Local state for instant visual feedback
    // Updates immediately on click, but only if ViewModel accepts the request
    @State private var localLikedState: Bool
    @State private var localLikesCount: Int32
    
    init(post: Post, onAuthorClick: @escaping () -> Void, onLikeClick: @escaping () -> Bool) {
        self.post = post
        self.onAuthorClick = onAuthorClick
        self.onLikeClick = onLikeClick
        // Initialize local state from post
        _localLikedState = State(initialValue: post.isLikedByMe)
        _localLikesCount = State(initialValue: post.likesCount)
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Author Header
            HStack(spacing: 13) {
                Button(action: onAuthorClick) {
                    ZStack {
                        Circle()
                            .fill(LinearGradient(
                                colors: [.primaryGreen, .accentYellow],
                                startPoint: .top, endPoint: .bottom
                            ))
                            .frame(width: 50, height: 50)
                        if let urlString = post.authorProfileImageUrl,
                           let url = URL(string: urlString) {
                            AsyncImage(url: url) { image in
                                image.resizable().aspectRatio(contentMode: .fill)
                            } placeholder: {
                                Text(String(post.authorName.prefix(1)).uppercased())
                                    .font(.system(size: 20, weight: .semibold))
                                    .foregroundColor(.white)
                            }
                            .frame(width: 46, height: 46)
                            .clipShape(Circle())
                        } else {
                            Text(String(post.authorName.prefix(1)).uppercased())
                                .font(.system(size: 20, weight: .semibold))
                                .foregroundColor(.white)
                        }
                    }
                }
                .buttonStyle(PlainButtonStyle())
                
                VStack(alignment: .leading, spacing: 4) {
                    Text(post.authorName)
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(.textPrimary)
                    
                    HStack(spacing: 7) {
                        Image(systemName: "location.fill")
                            .font(.system(size: 16))
                            .foregroundColor(.textSecondary)
                        Text(post.location?.name ?? "Location not set")
                            .font(.system(size: 15))
                            .foregroundColor(.textSecondary)
                    }
                }
                
                Spacer()
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 18)
            
            // Post Media - Use MediaCarousel for multiple media support
            if !post.media.isEmpty {
                MediaCarousel(
                    media: post.media,
                    onMediaClick: { /* TODO: Open media viewer */ },
                    isVisible: true
                )
            }
            
            // Action Bar
            HStack(spacing: 4) {
                ActionButton(
                    icon: localLikedState ? "heart.fill" : "heart",
                    label: "Like (\(localLikesCount))",
                    color: localLikedState ? .errorRed : .textSecondary
                ) {
                    // ⚡ Update local state IMMEDIATELY (before ViewModel call)
                    // This gives instant visual feedback with zero perceived lag
                    let newLikedState = !localLikedState
                    let newLikesCount = newLikedState ? localLikesCount + 1 : localLikesCount - 1
                    
                    // Call ViewModel first to check if it accepts the request
                    let accepted = onLikeClick()
                    
                    // Only update local state if ViewModel accepted the request
                    // This prevents sync issues when rapid clicks are ignored
                    if accepted {
                        localLikedState = newLikedState
                        localLikesCount = newLikesCount
                    }
                    // If not accepted (already processing), local state stays as-is
                    // onChange will sync it with actual post state when request completes
                }
                
                ActionButton(
                    icon: "bubble.right",
                    label: "Comment",
                    color: .textSecondary
                ) {
                    // Already in comments
                }
                
                ActionButton(
                    icon: "square.and.arrow.up",
                    label: "Share",
                    color: .textSecondary
                ) {
                    // TODO
                }
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
            .onChange(of: post.isLikedByMe) { newValue in
                // Sync local state with actual post state when it changes (from ViewModel or refresh)
                localLikedState = newValue
            }
            .onChange(of: post.likesCount) { newValue in
                // Sync local state with actual post state when it changes
                localLikesCount = newValue
            }
            
            // Post Text - Use PostTextContent for voice caption playback support
            if !post.text.isEmpty || post.voiceCaption != nil {
                PostTextContent(
                    text: post.text,
                    voiceCaption: post.voiceCaption,
                    onReadMore: { /* TODO: Expand text */ }
                )
                .padding(.horizontal, 18)
                .padding(.vertical, 18)
            }
            
            // Crop Tags
            if !post.crops.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 9) {
                        ForEach(post.crops, id: \.self) { crop in
                            Text(crop)
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(.textPrimary)
                                .padding(.horizontal, 15)
                                .padding(.vertical, 10)
                                .background(Color.accentYellow.opacity(0.08))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 18)
                                        .stroke(Color.accentYellow.opacity(0.19), lineWidth: 1)
                                )
                                .cornerRadius(18)
                        }
                    }
                    .padding(.horizontal, 18)
                }
                .padding(.bottom, 18)
            }
            
            // Divider
            Rectangle()
                .fill(Color.black.opacity(0.05))
                .frame(height: 1)
        }
        .background(Color.white)
    }
}

// MARK: - Comment Row
private struct CommentRow: View {
    let comment: Comment
    let currentUserId: String?
    let onAuthorClick: () -> Void
    let onReplyClick: () -> Void
    let onDeleteClick: () -> Void
    
    private var isOwnComment: Bool {
        currentUserId != nil && comment.authorId == currentUserId
    }
    
    var body: some View {
        HStack(alignment: .top, spacing: 13) {
            // Profile Picture
            Button(action: onAuthorClick) {
                ZStack {
                    Circle()
                        .fill(LinearGradient(
                            colors: [.primaryGreen, .accentYellow],
                            startPoint: .top, endPoint: .bottom
                        ))
                        .frame(width: 40, height: 40)
                    if let urlString = comment.authorProfileImageUrl,
                       let url = URL(string: urlString) {
                        AsyncImage(url: url) { image in
                            image.resizable().aspectRatio(contentMode: .fill)
                        } placeholder: {
                            Text(String(comment.authorName.prefix(1)).uppercased())
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(.white)
                        }
                        .frame(width: 36, height: 36)
                        .clipShape(Circle())
                    } else {
                        Text(String(comment.authorName.prefix(1)).uppercased())
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(.white)
                    }
                }
            }
            .buttonStyle(PlainButtonStyle())
            
            // Comment Content
            VStack(alignment: .leading, spacing: 6) {
                // Author name and timestamp
                HStack(spacing: 9) {
                    Button(action: onAuthorClick) {
                        Text(comment.authorName)
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(.textPrimary)
                    }
                    .buttonStyle(PlainButtonStyle())
                    
                    Text(formatTimestamp(comment.createdAt))
                        .font(.system(size: 14))
                        .foregroundColor(Color(red: 0.608, green: 0.608, blue: 0.608))
                }
                
                // Comment text
                Text(comment.text)
                    .font(.system(size: 17))
                    .foregroundColor(.textPrimary)
                    .lineSpacing(4)
                    .fixedSize(horizontal: false, vertical: true)
                
                // Action buttons (Reply, Delete if own comment)
                HStack(spacing: 16) {
                    Button(action: onReplyClick) {
                        Text("Reply")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(.textSecondary)
                    }
                    
                    if isOwnComment {
                        Button(action: onDeleteClick) {
                            Text("Delete")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(.errorRed)
                        }
                    }
                }
                .padding(.top, 4)
            }
            
            Spacer(minLength: 0)
        }
    }
}

// MARK: - Reply Indicator
private struct ReplyIndicator: View {
    let comment: Comment
    let onCancel: () -> Void
    
    var body: some View {
        HStack {
            HStack(spacing: 6) {
                Image(systemName: "arrowshape.turn.up.left.fill")
                    .font(.system(size: 14))
                    .foregroundColor(.primaryGreen)
                
                Text("Replying to @\(comment.authorUsername)")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.primaryGreen)
            }
            
            Spacer()
            
            Button(action: onCancel) {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 18))
                    .foregroundColor(.primaryGreen.opacity(0.7))
            }
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 12)
        .background(Color.primaryGreen.opacity(0.1))
    }
}

// MARK: - Comment Input Bar
private struct CommentInputBar: View {
    let text: String
    let replyingTo: Comment?
    let isPosting: Bool
    let onTextChange: (String) -> Void
    let onPostClick: () -> Void
    let onCancelReply: () -> Void
    @FocusState private var isTextFieldFocused: Bool
    
    var body: some View {
        VStack(spacing: 0) {
            Rectangle()
                .fill(Color.black.opacity(0.08))
                .frame(height: 1)
            
            HStack(alignment: .center, spacing: 13) {
                // Microphone button (green circular)
                Button(action: { /* TODO: Voice comment */ }) {
                    Circle()
                        .fill(Color.primaryGreen)
                        .frame(width: 45, height: 45)
                        .overlay(
                            Image(systemName: "mic.fill")
                                .font(.system(size: 20))
                                .foregroundColor(.white)
                        )
                }
                .disabled(isPosting)
                
                // Text input - single line
                TextField(
                    replyingTo != nil ? "Reply to @\(replyingTo!.authorUsername)..." : "Add a comment...",
                    text: Binding(
                        get: { text },
                        set: onTextChange
                    )
                )
                .font(.system(size: 17))
                .padding(.horizontal, 18)
                .padding(.vertical, 13)
                .background(Color.appBackground)
                .overlay(
                    RoundedRectangle(cornerRadius: 22)
                        .stroke(Color.primaryGreen.opacity(0.13), lineWidth: 1.5)
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
                    // Auto-focus when entering reply mode
                    if newValue != nil {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                            isTextFieldFocused = true
                        }
                    }
                }
                
                // Send button (grey or green circular)
                Button(action: {
                    onPostClick()
                    isTextFieldFocused = false
                }) {
                    Circle()
                        .fill(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ?
                              Color(red: 0.898, green: 0.898, blue: 0.898) :
                              Color.primaryGreen)
                        .frame(width: 45, height: 45)
                        .overlay(
                            Group {
                                if isPosting {
                                    ProgressView()
                                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                } else {
                                    Image(systemName: "paperplane.fill")
                                        .font(.system(size: 20))
                                        .foregroundColor(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ?
                                                        .textSecondary : .white)
                                }
                            }
                        )
                }
                .disabled(isPosting || text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
        }
        .background(
            Color.white
                .shadow(color: Color.black.opacity(0.08), radius: 8, x: 0, y: -2)
        )
    }
}

// MARK: - Delete Comment Sheet
private struct DeleteCommentSheet: View {
    @Binding var reason: String
    let onReasonChange: (String) -> Void
    let onConfirm: () -> Void
    let onDismiss: () -> Void
    
    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                Text("Please provide a reason for deleting this comment:")
                    .font(.system(size: 16))
                    .foregroundColor(.textPrimary)
                    .padding(.top, 8)
                
                TextEditor(text: Binding(
                    get: { reason },
                    set: onReasonChange
                ))
                .font(.system(size: 16))
                .padding(12)
                .frame(minHeight: 100)
                .background(Color.appBackground)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color.primaryGreen.opacity(0.13), lineWidth: 1)
                )
                .cornerRadius(8)
                
                if reason.isEmpty {
                    Text("Reason is required")
                        .font(.system(size: 14))
                        .foregroundColor(.textSecondary)
                }
                
                Spacer()
            }
            .padding()
            .navigationTitle("Delete Comment")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        onDismiss()
                    }
                    .foregroundColor(.textSecondary)
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Delete") {
                        onConfirm()
                    }
                    .foregroundColor(.errorRed)
                    .fontWeight(.semibold)
                    .disabled(reason.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}


// MARK: - Helper Functions
private func formatTimestamp(_ timestamp: Int64) -> String {
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
