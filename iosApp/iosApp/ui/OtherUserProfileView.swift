import SwiftUI
import Shared

private let profileBackground = Color(red: 0.984, green: 0.973, blue: 0.941)
private let expertGreen = Color(red: 0.455, green: 0.765, blue: 0.396)

struct OtherUserProfileView: View {
    let userId: String
    var onBackClick: () -> Void = {}
    var onPostClick: (String, Post?) -> Void = { _, _ in }
    var onFollowersClick: () -> Void = {}
    var onFollowingClick: () -> Void = {}
    
    @StateObject private var viewModel = OtherUserProfileViewModel()

    var body: some View {
        NavigationStack {
            ZStack {
                profileBackground

                if viewModel.isLoading {
                    Spacer()
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .primaryGreen))
                    Spacer()
                } else if let error = viewModel.error {
                    Spacer()
                    VStack(spacing: 16) {
                        Text(error)
                            .foregroundColor(.textSecondary)
                            .multilineTextAlignment(.center)
                        Button("Retry") {
                            viewModel.loadUserProfile(userId: userId)
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 12)
                        .background(Color.primaryGreen)
                        .cornerRadius(8)
                    }
                    Spacer()
                } else if let user = viewModel.user {
                    ScrollView {
                        OtherUserProfileContent(
                            user: user,
                            posts: viewModel.posts,
                            isLoadingPosts: viewModel.isLoadingPosts,
                            isFollowing: viewModel.isFollowing,
                            isFollowLoading: viewModel.isFollowLoading,
                            onFollowClick: { viewModel.toggleFollow() },
                            onPostClick: onPostClick,
                            onFollowersClick: onFollowersClick,
                            onFollowingClick: onFollowingClick
                        )
                        .padding(.horizontal, 18)
                        .padding(.top, 24)
                        .padding(.bottom, 20)
                    }
                    .scrollContentBackground(.hidden)
                } else {
                    Spacer()
                    Text("No profile found")
                        .foregroundColor(.textSecondary)
                    Spacer()
                }
            }
            .navigationTitle("Profile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: onBackClick) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundColor(.textPrimary)
                    }
                }
            }
        }
        .task {
            viewModel.loadUserProfile(userId: userId)
        }
    }
}

struct OtherUserProfileContent: View {
    let user: User
    let posts: [Post]
    let isLoadingPosts: Bool
    let isFollowing: Bool
    let isFollowLoading: Bool
    let onFollowClick: () -> Void
    let onPostClick: (String, Post?) -> Void
    var onFollowersClick: () -> Void = {}
    var onFollowingClick: () -> Void = {}

    var body: some View {
        VStack(alignment: .leading, spacing: 27) {
            VStack(spacing: 16) {
                // Avatar
                ZStack {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [.primaryGreen, .accentYellow],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                        .frame(width: 120, height: 120)

                    if let urlString = user.profileImageUrl, let url = URL(string: ensureHttps(urlString)) {
                        AsyncImage(url: url) { image in
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                        } placeholder: {
                            Text(String(user.name.prefix(1)).uppercased())
                                .font(.system(size: 48, weight: .semibold))
                                .foregroundColor(.white)
                        }
                        .frame(width: 114, height: 114)
                        .clipShape(Circle())
                    } else {
                        Text(String(user.name.prefix(1)).uppercased())
                            .font(.system(size: 48, weight: .semibold))
                            .foregroundColor(.white)
                    }
                }

                Text(user.name)
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(.textPrimary)
                    .lineLimit(1)
                
                Text("@\(user.username)")
                    .font(.system(size: 16, weight: .regular))
                    .foregroundColor(.textSecondary)
                
                HStack(spacing: 8) {
                    // Role Badge
                    Text(roleLabel(user.role))
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.primaryGreen)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(expertGreen.opacity(0.15))
                        .cornerRadius(16)
                    
                    // Verification Status Badge (if not UNVERIFIED)
                    if user.verificationStatus != .unverified {
                        VerificationStatusBadge(verificationStatus: user.verificationStatus)
                    }
                }

                if let loc = user.location {
                    HStack(spacing: 6) {
                        Image(systemName: "location.fill")
                            .font(.system(size: 14))
                            .foregroundColor(.textSecondary)
                        Text([loc.village, loc.district, loc.state, loc.country].compactMap { $0 }.joined(separator: ", "))
                            .font(.system(size: 14))
                            .foregroundColor(.textSecondary)
                    }
                }
            }
            .frame(maxWidth: .infinity)

            // Follow/Following Button
            if isFollowing {
                // Following button (outlined)
                Button(action: onFollowClick) {
                    if isFollowLoading {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .primaryGreen))
                    } else {
                        Text("Following")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(.primaryGreen)
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(Color.clear)
                .overlay(
                    RoundedRectangle(cornerRadius: 24)
                        .stroke(
                            LinearGradient(
                                colors: [.primaryGreen, .accentYellow],
                                startPoint: .leading,
                                endPoint: .trailing
                            ),
                            lineWidth: 2
                        )
                )
                .disabled(isFollowLoading)
            } else {
                // Follow button (filled)
                Button(action: onFollowClick) {
                    if isFollowLoading {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    } else {
                        Text("Follow")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(.white)
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(Color.primaryGreen)
                .cornerRadius(24)
                .disabled(isFollowLoading)
            }

            // Stats
            HStack {
                Spacer()
                StatItem(count: Int(user.postsCount), label: "Posts")
                Spacer()
                Button(action: onFollowersClick) {
                    StatItem(count: Int(user.followersCount), label: "Followers")
                }
                .buttonStyle(PlainButtonStyle())
                Spacer()
                Button(action: onFollowingClick) {
                    StatItem(count: Int(user.followingCount), label: "Following")
                }
                .buttonStyle(PlainButtonStyle())
                Spacer()
                StatItem(count: 0, label: "Groups")
                Spacer()
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("About")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.textPrimary)

                Text(user.bio ?? "No bio yet.")
                    .font(.system(size: 15))
                    .foregroundColor(.textSecondary)
                    .lineSpacing(4)
            }

            VStack(alignment: .leading, spacing: 12) {
                Text("Posts")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.textPrimary)

                if isLoadingPosts {
                    HStack {
                        Spacer()
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .primaryGreen))
                        Spacer()
                    }
                    .padding(.vertical, 32)
                } else if posts.isEmpty {
                    Text("No posts yet")
                        .font(.system(size: 15))
                        .foregroundColor(.textSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 32)
                } else {
                    // Grid matching ProfileView implementation with 3 columns
                    GeometryReader { geometry in
                        let spacing: CGFloat = 8
                        let itemWidth = (geometry.size.width - (2 * spacing)) / 3
                        
                        VStack(spacing: spacing) {
                            ForEach(Array(posts.chunked(into: 3).enumerated()), id: \.offset) { _, rowPosts in
                                HStack(spacing: spacing) {
                                    ForEach(rowPosts, id: \.id) { post in
                                        ProfilePostItem(
                                            post: post,
                                            onClick: { onPostClick(post.id, post) }
                                        )
                                        .frame(width: itemWidth, height: itemWidth)
                                    }
                                    
                                    // Fill remaining space if row has less than 3 items
                                    ForEach(0..<(3 - rowPosts.count), id: \.self) { _ in
                                        Color.clear
                                            .frame(width: itemWidth, height: itemWidth)
                                    }
                                }
                            }
                        }
                    }
                    .frame(height: calculateGridHeight(postCount: posts.count))
                }
            }
        }
    }
    
    private func calculateGridHeight(postCount: Int) -> CGFloat {
        let spacing: CGFloat = 8
        let rows = ceil(Double(postCount) / 3.0)
        let screenWidth = UIScreen.main.bounds.width
        let horizontalPadding: CGFloat = 36 // 18 * 2 for OtherUserProfileContent padding
        let availableWidth = screenWidth - horizontalPadding
        let itemWidth = (availableWidth - (2 * spacing)) / 3
        
        return CGFloat(rows) * itemWidth + CGFloat(max(0, rows - 1)) * spacing
    }
}

private func roleLabel(_ role: UserRole) -> String {
    switch role {
    case .farmer: return "Farmer"
    case .expert: return "Expert"
    case .agripreneur: return "Agripreneur"
    case .inputSeller: return "Input Seller"
    case .agriLover: return "Agri Lover"
    default: return "Farmer"
    }
}

#Preview {
    OtherUserProfileView(userId: "test123")
}
