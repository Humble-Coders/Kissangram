import SwiftUI
import Shared

private let profileBackground = Color(red: 0.984, green: 0.973, blue: 0.941)
private let expertGreen = Color(red: 0.455, green: 0.765, blue: 0.396)

struct ProfileView: View {
    @StateObject private var viewModel = ProfileViewModel()
    @State private var showMenu = false

    var onBackClick: () -> Void = {}
    var onEditProfile: () -> Void = {}
    var onSignOut: () -> Void = {}
    var onPostClick: (String) -> Void = { _ in }
    var reloadKey: Int = 0 // Key that changes to trigger reload after save

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
                            viewModel.loadProfile()
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
                        ProfileContent(
                            user: user,
                            posts: viewModel.posts,
                            isLoadingPosts: viewModel.isLoadingPosts,
                            onEditProfile: onEditProfile,
                            onPostClick: onPostClick
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
                ToolbarItem(placement: .navigationBarTrailing) {
                    Menu {
                        Button(role: .destructive, action: {
                            viewModel.signOut { onSignOut() }
                        }) {
                            Text("Sign out")
                        }
                    } label: {
                        Image(systemName: "ellipsis")
                            .font(.system(size: 20))
                            .foregroundColor(.textPrimary)
                    }
                }
            }
        }
        .task(id: reloadKey) {
            // Load when reloadKey changes (first display or after save)
            // This is similar to LaunchedEffect(reloadKey) in Android
            viewModel.loadProfile()
        }
    }
}

struct ProfileContent: View {
    let user: User
    let posts: [Post]
    let isLoadingPosts: Bool
    let onEditProfile: () -> Void
    let onPostClick: (String) -> Void

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

                    if let urlString = user.profileImageUrl, let url = URL(string: urlString) {
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

            Button(action: onEditProfile) {
                Text("Edit Profile")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(Color.primaryGreen)
                    .cornerRadius(24)
            }

            // Stats
            HStack {
                Spacer()
                StatItem(count: Int(user.postsCount), label: "Posts")
                Spacer()
                StatItem(count: Int(user.followersCount), label: "Followers")
                Spacer()
                StatItem(count: Int(user.followingCount), label: "Following")
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
                } else {
                    PostThumbnailGrid(posts: posts, onPostClick: onPostClick)
                }
            }

        }
    }
}

struct StatItem: View {
    let count: Int
    let label: String

    var body: some View {
        VStack(spacing: 4) {
            Text("\(count)")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.textPrimary)
            Text(label)
                .font(.system(size: 12))
                .foregroundColor(.textSecondary)
        }
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

struct VerificationStatusBadge: View {
    let verificationStatus: VerificationStatus
    
    private let verifiedBlue = Color(red: 0.129, green: 0.588, blue: 0.953)
    private let pendingOrange = Color(red: 1.0, green: 0.596, blue: 0.0)
    private let rejectedRed = Color(red: 0.737, green: 0.278, blue: 0.286)
    
    var body: some View {
        let (label, color, iconName) = statusInfo
        
        HStack(spacing: 6) {
            Image(systemName: iconName)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(color)
            Text(label)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(color)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(color.opacity(0.15))
        .cornerRadius(16)
    }
    
    private var statusInfo: (String, Color, String) {
        switch verificationStatus {
        case .verified:
            return ("Verified", verifiedBlue, "checkmark.seal.fill")
        case .pending:
            return ("Pending", pendingOrange, "clock.fill")
        case .rejected:
            return ("Rejected", rejectedRed, "xmark.circle.fill")
        case .unverified:
            return ("Unverified", Color.textSecondary, "questionmark.circle")
        default:
            return ("Unverified", Color.textSecondary, "questionmark.circle")
        }
    }
}

struct PostThumbnailGrid: View {
    let posts: [Post]
    let onPostClick: (String) -> Void
    
    private let columns = [
        GridItem(.flexible(), spacing: 8),
        GridItem(.flexible(), spacing: 8),
        GridItem(.flexible(), spacing: 8)
    ]
    
    var body: some View {
        if posts.isEmpty {
            Text("No posts yet")
                .font(.system(size: 15))
                .foregroundColor(.textSecondary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 32)
        } else {
            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(posts, id: \.id) { post in
                    PostThumbnailItem(post: post, onClick: { onPostClick(post.id) })
                }
            }
        }
    }
}

struct PostThumbnailItem: View {
    let post: Post
    let onClick: () -> Void
    
    var body: some View {
        ZStack {
            let firstMedia = post.media.first
            
            if let media = firstMedia {
                if media.type == .image {
                    AsyncImage(url: URL(string: transformThumbnailUrl(media.url))) { phase in
                        switch phase {
                        case .success(let image):
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                        case .failure(_), .empty:
                            Color(red: 0.898, green: 0.902, blue: 0.859)
                        @unknown default:
                            Color(red: 0.898, green: 0.902, blue: 0.859)
                        }
                    }
                } else {
                    // Video
                    ZStack {
                        if let thumbnailUrl = media.thumbnailUrl {
                            AsyncImage(url: URL(string: transformThumbnailUrl(thumbnailUrl))) { phase in
                                switch phase {
                                case .success(let image):
                                    image
                                        .resizable()
                                        .aspectRatio(contentMode: .fill)
                                case .failure(_), .empty:
                                    Color.black.opacity(0.3)
                                @unknown default:
                                    Color.black.opacity(0.3)
                                }
                            }
                        } else {
                            Color.black.opacity(0.3)
                        }
                        
                        // Play icon overlay
                        Image(systemName: "play.fill")
                            .font(.system(size: 24))
                            .foregroundColor(.white)
                    }
                }
            } else {
                // No media - placeholder
                ZStack {
                    Color(red: 0.898, green: 0.902, blue: 0.859)
                    if !post.text.isEmpty {
                        Text(String(post.text.prefix(1)).uppercased())
                            .font(.system(size: 24))
                            .foregroundColor(.textSecondary)
                    } else {
                        Image(systemName: "photo")
                            .font(.system(size: 24))
                            .foregroundColor(.textSecondary)
                    }
                }
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .cornerRadius(12)
        .onTapGesture {
            onClick()
        }
    }
    
    private func transformThumbnailUrl(_ url: String) -> String {
        // Transform Cloudinary URL for thumbnail (similar to Android)
        if url.contains("cloudinary.com") || url.contains("res.cloudinary.com") {
            let parts = url.split(separator: "?", maxSplits: 1)
            let baseUrl = parts.first.map(String.init) ?? url
            return "\(baseUrl)?w_300,h_300,c_fill,q_auto,f_auto"
        }
        return url
    }
}

#Preview {
    ProfileView()
}
