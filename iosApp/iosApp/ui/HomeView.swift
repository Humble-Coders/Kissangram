import SwiftUI
import os.log
import Shared
import AVFoundation
import AVKit

// MARK: - Colors
extension Color {
    static let appBackground = Color(red: 0.973, green: 0.976, blue: 0.945)
    static let primaryGreen = Color(red: 0.176, green: 0.416, blue: 0.310)
    static let accentYellow = Color(red: 1.0, green: 0.718, blue: 0.012)
    static let textPrimary = Color(red: 0.106, green: 0.106, blue: 0.106)
    static let textSecondary = Color(red: 0.420, green: 0.420, blue: 0.420)
    static let expertGreen = Color(red: 0.455, green: 0.765, blue: 0.396)
    static let errorRed = Color(red: 0.737, green: 0.278, blue: 0.286)
}

private let homeViewLog = Logger(subsystem: "com.kissangram", category: "HomeView")

struct HomeView: View {
    @StateObject private var viewModel = HomeViewModel()
    @StateObject private var visibilityTracker = DebouncedVisibilityTracker(batchIntervalMs: 80)
    @State private var hasRestoredScroll = false // Track if we've already restored scroll position
    
    var onNavigateToNotifications: () -> Void = {}
    var onNavigateToMessages: () -> Void = {}
    var onNavigateToProfile: (String) -> Void = { _ in }
    var onNavigateToStory: (String) -> Void = { _ in }
    var onNavigateToCreateStory: () -> Void = {}
    var onNavigateToPostDetail: (String, Post?) -> Void = { _, _ in }
    
    // Refresh trigger: when this changes, refresh the feed (used for tab switches)
    var refreshTrigger: Int = 0
    
    var body: some View {
        NavigationStack {
            ZStack {
                Color.appBackground
                
                VStack(spacing: 0) {
                    if viewModel.isLoading && viewModel.posts.isEmpty {
                        VStack {
                            Spacer()
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .primaryGreen))
                            Spacer()
                        }
                    } else if let error = viewModel.error, viewModel.posts.isEmpty {
                        VStack {
                            Spacer()
                            VStack(spacing: 16) {
                                Text(error).foregroundColor(.textSecondary)
                                Button("Retry") {
                                    Task { await viewModel.loadContent() }
                                }
                                .foregroundColor(.white)
                                .padding(.horizontal, 24)
                                .padding(.vertical, 12)
                                .background(Color.primaryGreen)
                                .cornerRadius(8)
                            }
                            Spacer()
                        }
                    } else {
                        ScrollViewReader { proxy in
                            ScrollView {
                                LazyVStack(spacing: 0) {
                                    // Stories Section — manages its own padding internally
                                    StoriesSection(
                                        stories: viewModel.stories,
                                        onStoryClick: onNavigateToStory,
                                        onCreateStoryClick: onNavigateToCreateStory
                                    )
                                    .frame(maxWidth: .infinity)
                                    .id("stories_section")
                                    
                                    // Spacing between stories and first card
                                    Spacer().frame(height: 12)
                                    
                                // Post Cards — full width, no card styling
                                ForEach(Array(viewModel.posts.enumerated()), id: \.element.id) { index, post in
                                    // Add horizontal divider between posts (except before first post)
                                    if index > 0 {
                                        Rectangle()
                                            .fill(Color.black.opacity(0.08))
                                            .frame(height: 0.5)
                                            .padding(.vertical, 2)
                                    }
                                    
                                    PostCardView(
                                            post: post,
                                            isVisible: visibilityTracker.visibleIndices.contains(index),
                                            isOwnPost: post.authorId == viewModel.currentUserId,
                                            isFollowingAuthor: viewModel.authorIdToIsFollowing[post.authorId] == true,
                                            onLikeClick: { viewModel.onLikePost(post.id) },
                                            onCommentClick: { 
                                                // Save scroll state before navigating
                                                viewModel.saveScrollState(postId: post.id)
                                                onNavigateToPostDetail(post.id, post) 
                                            },
                                            onShareClick: {},
                                            onSaveClick: { viewModel.onSavePost(post.id) },
                                            onAuthorClick: { onNavigateToProfile(post.authorId) },
                                            onPostClick: { 
                                                // Save scroll state before navigating
                                                viewModel.saveScrollState(postId: post.id)
                                                onNavigateToPostDetail(post.id, post) 
                                            },
                                            onFollowClick: { viewModel.onFollow(authorId: post.authorId) },
                                            onUnfollowClick: { viewModel.unfollowAndRemovePosts(authorId: post.authorId) }
                                        )
                                        .id(post.id)
                                        .onAppear {
                                            visibilityTracker.markAppeared(index)
                                        }
                                        .onDisappear {
                                            visibilityTracker.markDisappeared(index)
                                        }
                                    }
                                    
                                    if viewModel.isLoadingMore {
                                        ProgressView()
                                            .padding()
                                    }
                                    
                                    if !viewModel.hasMorePosts && !viewModel.posts.isEmpty {
                                        EndOfFeedSection()
                                    }
                                    
                                    // Bottom padding to ensure content doesn't get cut off by bottom nav
                                    Spacer().frame(height: 12)
                                }
                            }
                            .refreshable {
                                await viewModel.refreshFeed()
                            }
                            .scrollContentBackground(.hidden)
                            .onChange(of: viewModel.posts.count) { _ in
                                // Restore scroll position when posts are loaded
                                if let savedPostId = viewModel.savedScrollPostId,
                                   !hasRestoredScroll,
                                   !viewModel.posts.isEmpty,
                                   viewModel.posts.contains(where: { $0.id == savedPostId }) {
                                    // Use a longer delay to ensure the view is fully rendered
                                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                                        withAnimation(.easeInOut(duration: 0.3)) {
                                            proxy.scrollTo(savedPostId, anchor: .top)
                                        }
                                        // Clear saved state after restoring
                                        viewModel.clearScrollState()
                                        hasRestoredScroll = true
                                    }
                                }
                            }
                            .onChange(of: viewModel.savedScrollPostId) { savedPostId in
                                // Also restore when savedScrollPostId changes (e.g., when navigating back)
                                if let savedPostId = savedPostId,
                                   !hasRestoredScroll,
                                   !viewModel.posts.isEmpty,
                                   viewModel.posts.contains(where: { $0.id == savedPostId }) {
                                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                                        withAnimation(.easeInOut(duration: 0.3)) {
                                            proxy.scrollTo(savedPostId, anchor: .top)
                                        }
                                        // Clear saved state after restoring
                                        viewModel.clearScrollState()
                                        hasRestoredScroll = true
                                    }
                                }
                            }
                            .onAppear {
                                // Try to restore scroll position if we have a saved state and posts are already loaded
                                if let savedPostId = viewModel.savedScrollPostId,
                                   !hasRestoredScroll,
                                   !viewModel.posts.isEmpty,
                                   viewModel.posts.contains(where: { $0.id == savedPostId }) {
                                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                                        withAnimation(.easeInOut(duration: 0.3)) {
                                            proxy.scrollTo(savedPostId, anchor: .top)
                                        }
                                        viewModel.clearScrollState()
                                        hasRestoredScroll = true
                                    }
                                } else if viewModel.savedScrollPostId == nil {
                                    // Reset restoration flag if no saved state
                                    hasRestoredScroll = false
                                }
                            }
                            .onDisappear {
                                // Reset restoration flag when navigating away
                                hasRestoredScroll = false
                            }
                        }
                    }
                }
                .onAppear {
                    homeViewLog.debug("HomeView onAppear: posts=\(viewModel.posts.count) isLoading=\(viewModel.isLoading) error=\(viewModel.error ?? "nil")")
                }
                .onChange(of: refreshTrigger) { _ in
                    // Refresh feed when trigger changes (tab switch from bottom bar)
                    // This preserves state when returning from post details (no trigger change)
                    if refreshTrigger > 0 {
                        homeViewLog.debug("HomeView refreshTrigger changed, refreshing feed")
                        Task {
                            await viewModel.refreshFeed()
                        }
                    }
                }
                .onChange(of: viewModel.posts.count) { _ in
                    homeViewLog.debug("HomeView uiState: posts=\(viewModel.posts.count) isLoading=\(viewModel.isLoading) error=\(viewModel.error ?? "nil")")
                }
                .onChange(of: viewModel.isLoading) { _ in
                    homeViewLog.debug("HomeView uiState: posts=\(viewModel.posts.count) isLoading=\(viewModel.isLoading) error=\(viewModel.error ?? "nil")")
                    // Reset restoration flag when refreshing/loading
                    if viewModel.isLoading {
                        hasRestoredScroll = false
                    }
                }
                .onChange(of: viewModel.error) { _ in
                    homeViewLog.debug("HomeView uiState: posts=\(viewModel.posts.count) isLoading=\(viewModel.isLoading) error=\(viewModel.error ?? "nil")")
                }
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .principal) {
                        Text("Kissangram")
                            .font(.custom("Georgia", size: 24))
                            .fontWeight(.bold)
                            .foregroundColor(.primaryGreen)
                    }
                    
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button(action: { /* Disabled - not implemented */ }) {
                            Image(systemName: "bell")
                                .font(.system(size: 18))
                                .foregroundColor(.textSecondary.opacity(0.38))
                        }
                        .disabled(true)
                    }
                    
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button(action: { /* Disabled - not implemented */ }) {
                            Image(systemName: "envelope")
                                .font(.system(size: 18))
                                .foregroundColor(.textSecondary.opacity(0.38))
                        }
                        .disabled(true)
                    }
                }
            }
        }
    }
}
    
    // MARK: - Icon Button With Badge
    struct IconButtonWithBadge: View {
        let systemName: String
        let badgeColor: Color
        let showBadge: Bool
        let action: () -> Void
        
        var body: some View {
            Button(action: action) {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: systemName)
                        .font(.system(size: 18))
                        .foregroundColor(.textPrimary)
                    
                    if showBadge {
                        Circle()
                            .fill(badgeColor)
                            .frame(width: 8, height: 8)
                            .offset(x: 6, y: -6)
                    }
                }
            }
        }
    }
    
    // MARK: - Stories Section
    struct StoriesSection: View {
        let stories: [UserStories]
        let onStoryClick: (String) -> Void
        let onCreateStoryClick: () -> Void
        
        var body: some View {
            VStack(alignment: .leading, spacing: 0) {
                
                // Section Title — padding on its own HStack so it fills full width correctly
                HStack {
                    Text("Today in Nearby Fields")
                        .font(.custom("Georgia", size: 18))
                        .fontWeight(.bold)
                        .foregroundColor(.textPrimary)
                    Spacer()
                }
                .padding(.horizontal, 18)
                .padding(.top, 13)
                .padding(.bottom, 12)
                
                // Horizontal Story List
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 11) {
                        CreateStoryCard(onClick: onCreateStoryClick)
                        ForEach(stories, id: \.userId) { userStory in
                            StoryCard(userStory: userStory) {
                                onStoryClick(userStory.userId)
                            }
                        }
                    }
                    .padding(.leading, 18)
                    .padding(.trailing, 18)
                    .padding(.bottom, 13)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading) // ← force full width
            .background(Color.appBackground)
            
            Divider()
                .background(Color.black.opacity(0.05))
        }
    }

struct StoryCard: View {
    let userStory: UserStories
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 0) {
                ZStack(alignment: .topLeading) {
                    if let story = userStory.stories.first,
                       let url = URL(string: ensureHttps(story.media.url)) {
                        CachedImageView(url: url)
                            .frame(width: 120, height: 126)
                            .clipped()
                    } else {
                        Color.gray.opacity(0.3).frame(width: 120, height: 126)
                    }

                    LinearGradient(
                        colors: [Color.black.opacity(0.4), Color.clear, Color.black.opacity(0.6)],
                        startPoint: .top, endPoint: .bottom
                    )
                    .frame(width: 120, height: 126)

                    HStack {
                        ZStack {
                            Circle()
                                .fill(LinearGradient(
                                    colors: [.primaryGreen, .accentYellow],
                                    startPoint: .top, endPoint: .bottom
                                ))
                                .frame(width: 32, height: 32)
                            Circle().fill(Color.white).frame(width: 28, height: 28)
                            Text(String(userStory.userName.prefix(1)))
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(.primaryGreen)
                        }
                        Spacer()
                        Text("Today")
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundColor(.textPrimary)
                            .padding(.horizontal, 7)
                            .padding(.vertical, 6)
                            .background(Color.accentYellow.opacity(0.9))
                            .cornerRadius(4)
                    }
                    .padding(9)
                }
                .frame(width: 120, height: 126)
                .clipShape(UnevenRoundedRectangle(topLeadingRadius: 18, topTrailingRadius: 18))

                VStack(spacing: 8) {
                    Text(userStory.userName)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.textPrimary)
                        .lineLimit(1)
                }
                .frame(width: 120, height: 67)
            }
            .background(Color.white)
            .cornerRadius(18)
            .shadow(color: .black.opacity(0.06), radius: 4, y: 2)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

struct CreateStoryCard: View {
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            VStack(spacing: 0) {
                ZStack {
                    LinearGradient(
                        colors: [.primaryGreen, .accentYellow],
                        startPoint: .top, endPoint: .bottom
                    )
                    .frame(width: 120, height: 126)
                    Image(systemName: "plus")
                        .font(.system(size: 48, weight: .medium))
                        .foregroundColor(.white)
                }
                .clipShape(UnevenRoundedRectangle(topLeadingRadius: 18, topTrailingRadius: 18))

                VStack(spacing: 8) {
                    Text("Your Story")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.textPrimary)
                        .lineLimit(1)
                }
                .frame(width: 120, height: 67)
            }
            .background(Color.white)
            .cornerRadius(18)
            .shadow(color: .black.opacity(0.06), radius: 4, y: 2)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// MARK: - Post Card
// Each PostCardView is a self-contained white card with 18pt horizontal margin,
// 6pt vertical gap, rounded corners and shadow — matching EditProfileView card style.
struct PostCardView: View {
    let post: Post
    let isVisible: Bool
    let isOwnPost: Bool
    let isFollowingAuthor: Bool
    let onLikeClick: () -> Bool
    let onCommentClick: () -> Void
    let onShareClick: () -> Void
    let onSaveClick: () -> Void
    let onAuthorClick: () -> Void
    let onPostClick: () -> Void
    let onFollowClick: () -> Void
    let onUnfollowClick: () -> Void
    
    @State private var localLikedState: Bool
    @State private var localLikesCount: Int32
    
    init(
        post: Post,
        isVisible: Bool,
        isOwnPost: Bool = false,
        isFollowingAuthor: Bool = false,
        onLikeClick: @escaping () -> Bool,
        onCommentClick: @escaping () -> Void,
        onShareClick: @escaping () -> Void,
        onSaveClick: @escaping () -> Void,
        onAuthorClick: @escaping () -> Void,
        onPostClick: @escaping () -> Void,
        onFollowClick: @escaping () -> Void = {},
        onUnfollowClick: @escaping () -> Void = {}
    ) {
        self.post = post
        self.isVisible = isVisible
        self.isOwnPost = isOwnPost
        self.isFollowingAuthor = isFollowingAuthor
        self.onLikeClick = onLikeClick
        self.onCommentClick = onCommentClick
        self.onShareClick = onShareClick
        self.onSaveClick = onSaveClick
        self.onAuthorClick = onAuthorClick
        self.onPostClick = onPostClick
        self.onFollowClick = onFollowClick
        self.onUnfollowClick = onUnfollowClick
        _localLikedState = State(initialValue: post.isLikedByMe)
        _localLikesCount = State(initialValue: post.likesCount)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Author Header
            PostAuthorHeader(
                post: post,
                isOwnPost: isOwnPost,
                isFollowingAuthor: isFollowingAuthor,
                onAuthorClick: onAuthorClick,
                onFollowClick: onFollowClick,
                onUnfollowClick: onUnfollowClick
            )
                .padding(.horizontal, 16)
                .padding(.top, 12)

            // Tags Row
            Spacer().frame(height: 6)
            PostTagsRow(post: post)
                .padding(.horizontal, 16)

            // Media — full width, no padding
            if !post.media.isEmpty {
                Spacer().frame(height: 8)
                MediaCarousel(
                    media: post.media,
                    onMediaClick: onPostClick,
                    isVisible: isVisible
                )
            }

            // Post Text
            if !post.text.isEmpty || post.voiceCaption != nil {
                Spacer().frame(height: post.media.isEmpty ? 6 : 8)
                PostTextContent(
                    text: post.text,
                    voiceCaption: post.voiceCaption,
                    onReadMore: onPostClick
                )
                    .padding(.horizontal, 16)
            }

            // Action Bar - use local state for instant feedback
            Spacer().frame(height: 2)
            PostActionBar(
                post: Post(
                    id: post.id,
                    authorId: post.authorId,
                    authorName: post.authorName,
                    authorUsername: post.authorUsername,
                    authorProfileImageUrl: post.authorProfileImageUrl,
                    authorRole: post.authorRole,
                    authorVerificationStatus: post.authorVerificationStatus,
                    type: post.type,
                    text: post.text,
                    media: post.media,
                    voiceCaption: post.voiceCaption,
                    crops: post.crops,
                    hashtags: post.hashtags,
                    location: post.location,
                    question: post.question,
                    likesCount: localLikesCount,
                    commentsCount: post.commentsCount,
                    savesCount: post.savesCount,
                    isLikedByMe: localLikedState,
                    isSavedByMe: post.isSavedByMe,
                    createdAt: post.createdAt,
                    updatedAt: post.updatedAt
                ),
                onLikeClick: {
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
                },
                onCommentClick: onCommentClick,
                onShareClick: onShareClick,
                onSaveClick: onSaveClick
            )
            .padding(.horizontal, 8)   // ← slight inset so buttons align nicely
            .padding(.bottom, 8)
        }
        .onChange(of: post.isLikedByMe) { newValue in
            // Sync local state with actual post state when it changes (from ViewModel or refresh)
            localLikedState = newValue
        }
        .onChange(of: post.likesCount) { newValue in
            // Sync local state with actual post state when it changes
            localLikesCount = newValue
        }
    }
}

struct PostAuthorHeader: View {
    let post: Post
    let isOwnPost: Bool
    let isFollowingAuthor: Bool
    let onAuthorClick: () -> Void
    let onFollowClick: () -> Void
    let onUnfollowClick: () -> Void

    @State private var showUnfollowMenu = false

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onAuthorClick) {
                HStack(spacing: 11) {
                    ProfileImageLoader(
                        authorId: post.authorId,
                        authorName: post.authorName,
                        authorProfileImageUrl: post.authorProfileImageUrl,
                        size: 45
                    )

                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 7) {
                            Text(post.authorName)
                                .font(.system(size: 17, weight: .semibold))
                                .foregroundColor(.textPrimary)
                                .lineLimit(1)
                                .fixedSize(horizontal: false, vertical: true)
                            if post.authorVerificationStatus == .verified {
                                Image(systemName: "checkmark.circle.fill")
                                    .font(.system(size: 16))
                                    .foregroundColor(.expertGreen)
                            }
                        }
                        HStack(spacing: 7) {
                            Image(systemName: "person.fill")
                                .font(.system(size: 11))
                                .foregroundColor(.textSecondary)
                            Text(roleText(for: post))
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(.textSecondary)
                                .lineLimit(1)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
            }
            .buttonStyle(PlainButtonStyle())

            Spacer(minLength: 8)

            if !isOwnPost && post.authorRole != .expert {
                if isFollowingAuthor {
                    Menu {
                        Button(role: .destructive, action: {
                            onUnfollowClick()
                        }) {
                            Text("Unfollow")
                        }
                    } label: {
                        Image(systemName: "ellipsis")
                            .font(.system(size: 20, weight: .medium))
                            .foregroundColor(.textPrimary)
                            .frame(width: 44, height: 44)
                    }
                } else {
                    Button(action: onFollowClick) {
                        Text("+ Follow")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 19)
                            .padding(.vertical, 12)
                            .background(Color.primaryGreen)
                            .cornerRadius(25)
                    }
                }
            }
        }
    }

    private func roleText(for post: Post) -> String {
        switch post.authorRole {
        case .expert:      return "Agricultural Expert"
        case .farmer:      return post.location?.name ?? "Farmer"
        case .agripreneur: return "Agripreneur"
        case .inputSeller: return "Input Seller"
        case .agriLover:   return "Agri Lover"
        default:           return "Farmer"
        }
    }
}

struct PostTagsRow: View {
    let post: Post

    var body: some View {
        HStack(spacing: 7) {
            if post.authorRole == .expert {
                TagChip(
                    text: "Expert advice",
                    backgroundColor: Color.expertGreen.opacity(0.08),
                    textColor: .expertGreen,
                    dotColor: .expertGreen
                )
            } else if post.location != nil {
                TagChip(
                    text: "Nearby farmer",
                    backgroundColor: Color.accentYellow.opacity(0.08),
                    textColor: .accentYellow,
                    dotColor: .accentYellow
                )
            }
            
            // Crop tags - use ScrollView for horizontal scrolling (matching Android LazyRow)
            if !post.crops.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 7) {
                        ForEach(Array(post.crops), id: \.self) { crop in
                            Text(crop.capitalized)
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(.textPrimary)
                                .lineLimit(1)
                                .padding(.horizontal, 11)
                                .padding(.vertical, 6)
                                .background(Color.accentYellow.opacity(0.08))
                                .cornerRadius(18)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }
}

struct TagChip: View {
    let text: String
    let backgroundColor: Color
    let textColor: Color
    let dotColor: Color

    var body: some View {
        HStack(spacing: 4) {
            Circle().fill(dotColor).frame(width: 7, height: 7)
            Text(text)
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(textColor)
                .lineLimit(1)
        }
        .padding(.horizontal, 11)
        .padding(.vertical, 6)
        .background(backgroundColor)
        .cornerRadius(18)
    }
}

struct PostTextContent: View {
    let text: String
    let voiceCaption: VoiceContent?
    let onReadMore: () -> Void
    
    @State private var isExpanded = false
    private var shouldShowReadMore: Bool {
        text.count > 150
    }
    
    @State private var isPlaying = false
    @State private var playbackProgress = 0
    @State private var audioPlayer: AVPlayer?
    @State private var timeObserver: Any?
    @State private var endTimeObserver: NSObjectProtocol?

    var body: some View {
        HStack(alignment: .top, spacing: 11) {
            // Left icon/button - show play button if voiceCaption exists, otherwise text icon
            if let voiceCaption = voiceCaption {
                // Voice caption play button
                VStack(alignment: .center, spacing: 4) {
                    Button(action: togglePlayback) {
                        Circle()
                            .fill(isPlaying ? Color(red: 1.0, green: 0.42, blue: 0.42) : Color.primaryGreen)
                            .frame(width: 40, height: 40)
                            .overlay(
                                Image(systemName: isPlaying ? "stop.fill" : "speaker.wave.2.fill")
                                    .font(.system(size: 18))
                                    .foregroundColor(.white)
                            )
                    }
                    
                }
                .frame(width: 40)
            } else {
                // Text icon (when no voice caption)
                Circle()
                    .fill(Color.primaryGreen.opacity(0.1))
                    .frame(width: 40, height: 40)
                    .overlay(
                        Image(systemName: "text.alignleft")
                            .font(.system(size: 18))
                            .foregroundColor(.primaryGreen)
                    )
            }
            
            // Text caption (right side) - aligned with icon center
            // Icon is 40pt, so center is at 20pt. Text font size is 17pt, so we add padding to align first line center
            if !text.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    Text(text)
                        .font(.system(size: 17))
                        .foregroundColor(.textPrimary)
                        .lineLimit(isExpanded ? nil : 3)
                        .lineSpacing(6)
                        .fixedSize(horizontal: false, vertical: true)
                    if shouldShowReadMore {
                        Button(action: {
                            isExpanded.toggle()
                            if isExpanded {
                                onReadMore() // Call callback when expanded
                            }
                        }) {
                            Text(isExpanded ? "Show less" : "Read more")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(.primaryGreen)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, 11.5) // (40pt icon height - 17pt text height) / 2 ≈ 11.5pt to center text with icon
            } else {
                // If no text but has voice caption, take up space
                Spacer()
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .onDisappear {
            stopPlayback()
        }
    }
    
    private func togglePlayback() {
        if isPlaying {
            stopPlayback()
        } else {
            startPlayback()
        }
    }
    
    private func startPlayback() {
        guard let voiceCaption = voiceCaption else { return }
        
        // Stop any existing playback
        stopPlayback()
        
        guard let url = URL(string: ensureHttps(voiceCaption.url)) else {
            print("Invalid voice caption URL: \(voiceCaption.url)")
            return
        }
        
        do {
            // Configure audio session
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
            try AVAudioSession.sharedInstance().setActive(true)
            
            // Create AVPlayer for remote URLs (AVAudioPlayer doesn't support remote URLs)
            let player = AVPlayer(url: url)
            
            // Observe time for progress updates
            let interval = CMTime(seconds: 0.1, preferredTimescale: CMTimeScale(NSEC_PER_SEC))
            let observer = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { time in
                let currentSeconds = Int(CMTimeGetSeconds(time))
                playbackProgress = currentSeconds
                
                if currentSeconds >= voiceCaption.durationSeconds {
                    stopPlayback()
                }
            }
            timeObserver = observer
            
            // Observe when playback finishes
            let endObserver = NotificationCenter.default.addObserver(
                forName: .AVPlayerItemDidPlayToEndTime,
                object: player.currentItem,
                queue: .main
            ) { _ in
                stopPlayback()
            }
            endTimeObserver = endObserver
            
            player.play()
            audioPlayer = player
            isPlaying = true
            playbackProgress = 0
            
        } catch {
            print("Failed to play audio: \(error.localizedDescription)")
            stopPlayback()
        }
    }
    
    private func stopPlayback() {
        // Remove time observer
        if let observer = timeObserver {
            audioPlayer?.removeTimeObserver(observer)
            timeObserver = nil
        }
        
        // Remove notification observer
        if let observer = endTimeObserver {
            NotificationCenter.default.removeObserver(observer)
            endTimeObserver = nil
        }
        
        // Stop player
        audioPlayer?.pause()
        audioPlayer?.replaceCurrentItem(with: nil)
        audioPlayer = nil
        
        isPlaying = false
        playbackProgress = 0
        
        // Deactivate audio session
        try? AVAudioSession.sharedInstance().setActive(false)
    }
}

struct PostActionBar: View {
    let post: Post
    let onLikeClick: () -> Void
    let onCommentClick: () -> Void
    let onShareClick: () -> Void
    let onSaveClick: () -> Void

    var body: some View {
        HStack {
            ActionButton(
                icon: post.isLikedByMe ? "heart.fill" : "heart",
                label: "\(post.likesCount)",
                color: post.isLikedByMe ? .errorRed : .textSecondary,
                action: onLikeClick
            )
            ActionButton(
                icon: "bubble.right",
                label: "Comment",
                color: .textSecondary,
                action: onCommentClick
            )
            ActionButton(
                icon: "square.and.arrow.up",
                label: "Share",
                color: .textSecondary.opacity(0.38),
                action: { /* Disabled - not implemented */ }
            )
            .disabled(true)
            Spacer()
            Button(action: onSaveClick) {
                Image(systemName: post.isSavedByMe ? "bookmark.fill" : "bookmark")
                    .font(.system(size: 18))
                    .foregroundColor(post.isSavedByMe ? .primaryGreen : .textSecondary)
                    .padding(.trailing, 8)
            }
        }
        .padding(.top, 2)
        .padding(.bottom, 4)
    }
}

struct ActionButton: View {
    let icon: String
    let label: String
    let color: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 7) {
                Image(systemName: icon).font(.system(size: 18))
                Text(label)
                    .font(.system(size: 15, weight: .semibold))
                    .lineLimit(1)
            }
            .foregroundColor(color)
            .padding(.horizontal, 10)
            .padding(.vertical, 10)
        }
    }
}

// MARK: - End of Feed
struct EndOfFeedSection: View {
    var body: some View {
        VStack(spacing: 24) {
            Circle()
                .fill(Color(red: 0.898, green: 0.902, blue: 0.859).opacity(0.5))
                .frame(width: 72, height: 72)
                .overlay(
                    Image(systemName: "checkmark.circle")
                        .font(.system(size: 36))
                        .foregroundColor(.textSecondary.opacity(0.5))
                )
            Text("You're all caught up! 🌾")
                .font(.system(size: 17))
                .foregroundColor(.textSecondary)
            Text("Check back later for more updates")
                .font(.system(size: 15))
                .foregroundColor(.textSecondary)
        }
        .padding(36)
    }
}

#Preview("Home Feed - iPhone 15 Pro") {
    HomeView()
        .previewDevice("iPhone 15 Pro")
}

#Preview("Home Feed - iPhone SE") {
    HomeView()
        .previewDevice("iPhone SE (3rd generation)")
}

#Preview("Home Feed - Dark Mode") {
    HomeView()
        .preferredColorScheme(.dark)
        .previewDevice("iPhone 15 Pro")
}
