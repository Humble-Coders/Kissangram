import SwiftUI
import Shared
import Foundation

private let profileBackground = Color(red: 0.984, green: 0.973, blue: 0.941)
private let expertGreen = Color(red: 0.455, green: 0.765, blue: 0.396)

// MARK: - Logging Helper
private func logEvent(_ location: String, _ message: String, _ data: [String: Any] = [:]) {
    let logData: [String: Any] = [
        "location": location,
        "message": message,
        "data": data,
        "timestamp": Int(Date().timeIntervalSince1970 * 1000)
    ]
    
    // Console log for immediate visibility
    print("🔵 [\(location)] \(message) - Data: \(data)")
    
    // File log for persistence
    if let jsonData = try? JSONSerialization.data(withJSONObject: logData),
       let jsonString = String(data: jsonData, encoding: .utf8) {
        let logLine = jsonString + "\n"
        let logPath = "/Users/rishibhardwaj/AndroidStudioProjects/Kissangram/.cursor/debug.log"
        
        // Ensure directory exists
        let directory = (logPath as NSString).deletingLastPathComponent
        try? FileManager.default.createDirectory(atPath: directory, withIntermediateDirectories: true, attributes: nil)
        
        if let logFile = FileHandle(forWritingAtPath: logPath) {
            logFile.seekToEndOfFile()
            logFile.write(logLine.data(using: .utf8) ?? Data())
            logFile.closeFile()
        } else {
            // Try to create the file if it doesn't exist
            try? logLine.write(toFile: logPath, atomically: true, encoding: .utf8)
        }
    }
}

struct ProfileView: View {
    @StateObject private var viewModel = ProfileViewModel()
    @State private var showMenu = false

    var onBackClick: () -> Void = {}
    var onEditProfile: () -> Void = {}
    var onSignOut: () -> Void = {}
    var onPostClick: (String, Post?) -> Void = { _, _ in }
    var onFollowersClick: () -> Void = {}
    var onFollowingClick: () -> Void = {}
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
                            onPostClick: { postId, post in
                                logEvent("ProfileView:75", "onPostClick received", [
                                    "postId": postId,
                                    "hasPost": post != nil,
                                    "postsCount": viewModel.posts.count
                                ])
                                print("🔵 [ProfileView:85] About to call onPostClick closure - postId: \(postId)")
                                onPostClick(postId, post)
                                print("🔵 [ProfileView:87] onPostClick closure call completed")
                            },
                            onFollowersClick: onFollowersClick,
                            onFollowingClick: onFollowingClick
                        )
                        .padding(.horizontal, 18)
                        .padding(.top, 24)
                        .padding(.bottom, 20)
                    }
                    .scrollContentBackground(.hidden)
                    .onAppear {
                        logEvent("ProfileView:90", "ProfileView appeared with posts", [
                            "postsCount": viewModel.posts.count,
                            "userId": user.id
                        ])
                    }
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
                Button(action: {
                    print("🔵 [ProfileView] Followers button tapped")
                    onFollowersClick()
                }) {
                    StatItem(count: Int(user.followersCount), label: "Followers")
                }
                .buttonStyle(PlainButtonStyle())
                .contentShape(Rectangle())
                Spacer()
                Button(action: {
                    print("🔵 [ProfileView] Following button tapped")
                    onFollowingClick()
                }) {
                    StatItem(count: Int(user.followingCount), label: "Following")
                }
                .buttonStyle(PlainButtonStyle())
                .contentShape(Rectangle())
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
                    // Grid matching SearchView implementation but with 3 columns
                    GeometryReader { geometry in
                        let spacing: CGFloat = 8
                        let itemWidth = (geometry.size.width - (2 * spacing)) / 3
                        
                        VStack(spacing: spacing) {
                            ForEach(Array(posts.chunked(into: 3).enumerated()), id: \.offset) { _, rowPosts in
                                HStack(spacing: spacing) {
                                    ForEach(rowPosts, id: \.id) { post in
                                        ProfilePostItem(
                                            post: post,
                                            onClick: {
                                                logEvent("ProfileContent:256", "onPostClick received in ProfileContent", [
                                                    "postId": post.id,
                                                    "hasPost": true,
                                                    "postsCount": posts.count
                                                ])
                                                onPostClick(post.id, post)
                                            }
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
    
    private func transformThumbnailUrl(_ url: String) -> String {
        // Transform Cloudinary URL for thumbnail (similar to Android)
        // ensureHttps required for iOS App Transport Security
        let secureUrl = ensureHttps(url)
        if secureUrl.contains("cloudinary.com") || secureUrl.contains("res.cloudinary.com") {
            let parts = secureUrl.split(separator: "?", maxSplits: 1)
            let baseUrl = parts.first.map(String.init) ?? secureUrl
            return "\(baseUrl)?w_300,h_300,c_fill,q_auto,f_auto"
        }
        return secureUrl
    }
    
    private func calculateGridHeight(postCount: Int) -> CGFloat {
        let spacing: CGFloat = 8
        let rows = ceil(Double(postCount) / 3.0)
        let screenWidth = UIScreen.main.bounds.width
        let horizontalPadding: CGFloat = 36 // 18 * 2 for ProfileContent padding
        let availableWidth = screenWidth - horizontalPadding
        let itemWidth = (availableWidth - (2 * spacing)) / 3
        
        return CGFloat(rows) * itemWidth + CGFloat(max(0, rows - 1)) * spacing
    }
}

struct ProfilePostItem: View {
    let post: Post
    let onClick: () -> Void
    
    var body: some View {
        Button(action: onClick) {
            GeometryReader { geometry in
                ZStack {
                    // Post media thumbnail
                    Group {
                        if let firstMedia = post.media.first {
                            let imageUrl: String? = {
                                if firstMedia.type == .video {
                                    if let thumbnailUrl = firstMedia.thumbnailUrl {
                                        return transformThumbnailUrl(thumbnailUrl)
                                    } else {
                                        // Generate thumbnail from video URL
                                        return generateVideoThumbnailUrl(firstMedia.url)
                                    }
                                } else {
                                    return transformThumbnailUrl(firstMedia.url)
                                }
                            }()
                            
                            if let urlString = imageUrl, let url = URL(string: urlString) {
                                AsyncImage(url: url) { image in
                                    image
                                        .resizable()
                                        .scaledToFill()
                                        .frame(width: geometry.size.width, height: geometry.size.height)
                                        .clipped()
                                } placeholder: {
                                    Rectangle()
                                        .fill(Color(red: 0.898, green: 0.902, blue: 0.859))
                                        .frame(width: geometry.size.width, height: geometry.size.height)
                                        .overlay(
                                            Image(systemName: "photo")
                                                .foregroundColor(.textSecondary.opacity(0.5))
                                                .font(.system(size: 20))
                                        )
                                }
                            } else {
                                Rectangle()
                                    .fill(Color(red: 0.898, green: 0.902, blue: 0.859))
                                    .frame(width: geometry.size.width, height: geometry.size.height)
                                    .overlay(
                                        Image(systemName: "photo")
                                            .foregroundColor(.textSecondary.opacity(0.5))
                                            .font(.system(size: 20))
                                    )
                            }
                        } else {
                            // No media - placeholder
                            Rectangle()
                                .fill(Color(red: 0.898, green: 0.902, blue: 0.859))
                                .frame(width: geometry.size.width, height: geometry.size.height)
                                .overlay(
                                    Group {
                                        if !post.text.isEmpty {
                                            Text(String(post.text.prefix(1)).uppercased())
                                                .font(.system(size: 20))
                                                .foregroundColor(.textSecondary)
                                        } else {
                                            Image(systemName: "photo")
                                                .foregroundColor(.textSecondary.opacity(0.5))
                                                .font(.system(size: 20))
                                        }
                                    }
                                )
                        }
                    }
                    
                    // Video play icon overlay if video
                    if let firstMedia = post.media.first, firstMedia.type == .video {
                        Image(systemName: "play.fill")
                            .font(.system(size: 20))
                            .foregroundColor(.white)
                            .padding(8)
                            .background(Color.black.opacity(0.5))
                            .clipShape(Circle())
                    }
                }
                .cornerRadius(12)
                .clipped()
            }
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    private func transformThumbnailUrl(_ url: String) -> String {
        // Transform Cloudinary URL for thumbnail (similar to Android)
        // ensureHttps required for iOS App Transport Security
        let secureUrl = ensureHttps(url)
        if secureUrl.contains("cloudinary.com") || secureUrl.contains("res.cloudinary.com") {
            let parts = secureUrl.split(separator: "?", maxSplits: 1)
            let baseUrl = parts.first.map(String.init) ?? secureUrl
            return "\(baseUrl)?w_300,h_300,c_fill,q_auto,f_auto"
        }
        return secureUrl
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
    let onPostClick: (String, Post?) -> Void
    
    var body: some View {
        if posts.isEmpty {
            Text("No posts yet")
                .font(.system(size: 15))
                .foregroundColor(.textSecondary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 32)
        } else {
            // Use VStack with HStacks instead of LazyVGrid to avoid nested scrolling issues
            // This matches the Android implementation
            VStack(spacing: 8) {
                ForEach(Array(posts.chunked(into: 3).enumerated()), id: \.offset) { _, rowPosts in
                    HStack(spacing: 8) {
                        ForEach(rowPosts, id: \.id) { post in
                            PostThumbnailItem(
                                post: post,
                                onClick: {
                                    onPostClick(post.id, post)
                                }
                            )
                            .frame(maxWidth: .infinity)
                        }
                        // Fill remaining space if row has less than 3 items
                        ForEach(0..<(3 - rowPosts.count), id: \.self) { _ in
                            Spacer()
                                .frame(maxWidth: .infinity)
                        }
                    }
                }
            }
        }
    }
}

struct PostThumbnailItem: View {
    let post: Post
    let onClick: () -> Void
    
    var body: some View {
        Button(action: {
            onClick()
        }) {
            ZStack {
                // ... existing media rendering code ...
            }
            .aspectRatio(1, contentMode: .fill)  // Changed from .fit to .fill
            .cornerRadius(12)
            .clipped()  // Add clipped() to prevent overflow
            .buttonStyle(PlainButtonStyle())
        }
    }

    
    private func transformThumbnailUrl(_ url: String) -> String {
        // Transform Cloudinary URL for thumbnail (similar to Android)
        // ensureHttps required for iOS App Transport Security
        let secureUrl = ensureHttps(url)
        if secureUrl.contains("cloudinary.com") || secureUrl.contains("res.cloudinary.com") {
            let parts = secureUrl.split(separator: "?", maxSplits: 1)
            let baseUrl = parts.first.map(String.init) ?? secureUrl
            return "\(baseUrl)?w_300,h_300,c_fill,q_auto,f_auto"
        }
        return secureUrl
    }
}
    

