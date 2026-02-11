import SwiftUI
import os.log
import Shared

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
    
    var onNavigateToNotifications: () -> Void = {}
    var onNavigateToMessages: () -> Void = {}
    var onNavigateToProfile: (String) -> Void = { _ in }
    var onNavigateToStory: (String) -> Void = { _ in }
    var onNavigateToCreateStory: () -> Void = {}
    var onNavigateToPostDetail: (String) -> Void = { _ in }
    var onNavigateToComments: (String) -> Void = { _ in }
    
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
                        ScrollView {
                            LazyVStack(spacing: 0) {
                                // Stories Section — manages its own padding internally
                                StoriesSection(
                                    stories: viewModel.stories,
                                    onStoryClick: onNavigateToStory,
                                    onCreateStoryClick: onNavigateToCreateStory
                                )
                                .frame(maxWidth: .infinity)
                                
                                // Spacing between stories and first card
                                Spacer().frame(height: 12)
                                
                                // Post Cards — each card handles its own horizontal padding
                                ForEach(viewModel.posts, id: \.id) { post in
                                    PostCardView(
                                        post: post,
                                        onLikeClick: { viewModel.onLikePost(post.id) },
                                        onCommentClick: { onNavigateToComments(post.id) },
                                        onShareClick: {},
                                        onSaveClick: { viewModel.onSavePost(post.id) },
                                        onAuthorClick: { onNavigateToProfile(post.authorId) },
                                        onPostClick: { onNavigateToPostDetail(post.id) }
                                    )
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
                    }
                }
                .onAppear {
                    homeViewLog.debug("HomeView onAppear: posts=\(viewModel.posts.count) isLoading=\(viewModel.isLoading) error=\(viewModel.error ?? "nil")")
                }
                .onChange(of: viewModel.posts.count) { _ in
                    homeViewLog.debug("HomeView uiState: posts=\(viewModel.posts.count) isLoading=\(viewModel.isLoading) error=\(viewModel.error ?? "nil")")
                }
                .onChange(of: viewModel.isLoading) { _ in
                    homeViewLog.debug("HomeView uiState: posts=\(viewModel.posts.count) isLoading=\(viewModel.isLoading) error=\(viewModel.error ?? "nil")")
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
                        Button(action: onNavigateToNotifications) {
                            Image(systemName: "bell")
                                .font(.system(size: 18))
                                .foregroundColor(.textPrimary)
                        }
                    }
                    
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button(action: onNavigateToMessages) {
                            Image(systemName: "envelope")
                                .font(.system(size: 18))
                                .foregroundColor(.textPrimary)
                        }
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
                       let url = URL(string: story.media.url) {
                        AsyncImage(url: url) { image in
                            image.resizable().aspectRatio(contentMode: .fill)
                        } placeholder: {
                            Color.gray.opacity(0.3)
                        }
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
                    Text("🌾 Wheat")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(.primaryGreen)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.primaryGreen.opacity(0.08))
                        .cornerRadius(16)
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
                    Text("+ Add")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(.primaryGreen)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.primaryGreen.opacity(0.08))
                        .cornerRadius(16)
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
    let onLikeClick: () -> Void
    let onCommentClick: () -> Void
    let onShareClick: () -> Void
    let onSaveClick: () -> Void
    let onAuthorClick: () -> Void
    let onPostClick: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Author Header
            PostAuthorHeader(post: post, onAuthorClick: onAuthorClick)
                .padding(.horizontal, 16)
                .padding(.top, 16)

            // Tags Row
            Spacer().frame(height: 10)
            PostTagsRow(post: post)
                .padding(.horizontal, 16)

            // Image — full width inside card, rounded corners
            if !post.media.isEmpty, let firstMedia = post.media.first {
                Spacer().frame(height: 12)
                PostImageView(media: firstMedia, onClick: onPostClick)
                    .padding(.horizontal, 16) // ← inset from card edges
            }

            // Post Text
            if !post.text.isEmpty {
                Spacer().frame(height: post.media.isEmpty ? 10 : 14)
                PostTextContent(text: post.text, onReadMore: onPostClick)
                    .padding(.horizontal, 16)
            }

            // Action Bar
            Spacer().frame(height: 4)
            PostActionBar(
                post: post,
                onLikeClick: onLikeClick,
                onCommentClick: onCommentClick,
                onShareClick: onShareClick,
                onSaveClick: onSaveClick
            )
            .padding(.horizontal, 8)   // ← slight inset so buttons align nicely
            .padding(.bottom, 8)
        }
        .background(Color.white)              // ← white card background
        .cornerRadius(18)                     // ← rounded corners
        .shadow(color: Color.black.opacity(0.06), radius: 8, x: 0, y: 2) // ← relief shadow
        .padding(.horizontal, 18)             // ← 18pt screen margins (left & right)
        .padding(.vertical, 6)               // ← gap between cards
    }
}

struct PostAuthorHeader: View {
    let post: Post
    let onAuthorClick: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onAuthorClick) {
                HStack(spacing: 11) {
                    ZStack {
                        Circle()
                            .fill(LinearGradient(
                                colors: [.primaryGreen, .accentYellow],
                                startPoint: .top, endPoint: .bottom
                            ))
                            .frame(width: 45, height: 45)
                        if let urlString = post.authorProfileImageUrl,
                           let url = URL(string: urlString) {
                            AsyncImage(url: url) { image in
                                image.resizable().aspectRatio(contentMode: .fill)
                            } placeholder: {
                                Text(String(post.authorName.prefix(1)).uppercased())
                                    .font(.system(size: 20, weight: .semibold))
                                    .foregroundColor(.white)
                            }
                            .frame(width: 41, height: 41)
                            .clipShape(Circle())
                        } else {
                            Text(String(post.authorName.prefix(1)).uppercased())
                                .font(.system(size: 20, weight: .semibold))
                                .foregroundColor(.white)
                        }
                    }

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

            if post.authorRole != .expert {
                Button(action: {}) {
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
            ForEach(Array(post.crops.prefix(2)), id: \.self) { crop in
                Text(crop.capitalized)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(.textPrimary)
                    .lineLimit(1)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(Color.accentYellow.opacity(0.08))
                    .overlay(
                        RoundedRectangle(cornerRadius: 18)
                            .stroke(Color.accentYellow.opacity(0.19), lineWidth: 0.5)
                    )
                    .cornerRadius(18)
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

// Image inside a card — no negative padding needed, just fill card width with rounded corners
struct PostImageView: View {
    let media: PostMedia
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            if let url = URL(string: media.url) {
                AsyncImage(url: url) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    Color.gray.opacity(0.3)
                }
                .frame(maxWidth: .infinity)
                .frame(height: 220)          // ← slightly shorter now that it has card padding
                .clipped()
                .cornerRadius(14)            // ← inner corner radius inside the card
            }
        }
        .buttonStyle(PlainButtonStyle())
        .contentShape(Rectangle())
    }
}

struct PostTextContent: View {
    let text: String
    let onReadMore: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 11) {
            Circle()
                .fill(Color.primaryGreen.opacity(0.1))
                .frame(width: 40, height: 40)
                .overlay(
                    Image(systemName: "text.alignleft")
                        .font(.system(size: 18))
                        .foregroundColor(.primaryGreen)
                )
            VStack(alignment: .leading, spacing: 4) {
                Text(text)
                    .font(.system(size: 17))
                    .foregroundColor(.textPrimary)
                    .lineLimit(3)
                    .lineSpacing(6)
                    .fixedSize(horizontal: false, vertical: true)
                if text.count > 150 {
                    Button("Read more", action: onReadMore)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.primaryGreen)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
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
                label: "Like",
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
                color: .textSecondary,
                action: onShareClick
            )
            Spacer()
            Button(action: onSaveClick) {
                Image(systemName: post.isSavedByMe ? "bookmark.fill" : "bookmark")
                    .font(.system(size: 18))
                    .foregroundColor(post.isSavedByMe ? .primaryGreen : .textSecondary)
                    .padding(.trailing, 8)
            }
        }
        .padding(.top, 4)
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
