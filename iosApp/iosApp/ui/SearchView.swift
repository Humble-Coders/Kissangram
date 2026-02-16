import SwiftUI
import Shared

private let searchBackground = Color(red: 0.984, green: 0.973, blue: 0.941)
private let expertGreen = Color(red: 0.455, green: 0.765, blue: 0.396)

struct SearchView: View {
    @StateObject private var viewModel = SearchViewModel()
    
    var onUserClick: (String) -> Void = { _ in }
    var onPostClick: (String, Post?) -> Void = { _, _ in }
    var onFollowClick: (String) -> Void = { _ in }
    
    var body: some View {
        ZStack {
            searchBackground
            
            VStack(spacing: 0) {
                // Search Bar
                UserSearchBar(
                    query: $viewModel.query,
                    onQueryChange: { viewModel.setQuery($0) },
                    onClearClick: { viewModel.clearSearch() }
                )
                .padding(.horizontal, 18)
                .padding(.vertical, 16)
                
                // Content
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
                            viewModel.setQuery(viewModel.query)
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 12)
                        .background(Color.primaryGreen)
                        .cornerRadius(8)
                    }
                    Spacer()
                } else if viewModel.query.isEmpty {
                    // Show suggestions when query is empty
                    SuggestionsContent(
                        sections: viewModel.suggestionSections,
                        isRefreshing: viewModel.isRefreshingSuggestions,
                        onRefresh: {
                            Task {
                                await viewModel.refreshSuggestions()
                            }
                        },
                        onPostClick: onPostClick,
                        onUserClick: onUserClick,
                        onFollowClick: { userId in
                            Task {
                                await viewModel.followUser(userId: userId)
                            }
                        }
                    )
                } else if viewModel.hasSearched && viewModel.results.isEmpty {
                    // No results
                    Spacer()
                    VStack(spacing: 8) {
                        Text("No users found")
                            .foregroundColor(.textSecondary)
                            .font(.system(size: 16, weight: .medium))
                        
                        Text("Try a different search term")
                            .foregroundColor(.textSecondary.opacity(0.7))
                            .font(.system(size: 14))
                    }
                    Spacer()
                } else {
                    // Results list
                    ScrollView {
                        LazyVStack(spacing: 8) {
                            ForEach(viewModel.results, id: \.id) { user in
                                UserSearchResultItem(
                                    user: user,
                                    onClick: { onUserClick(user.id) }
                                )
                            }
                        }
                        .padding(.horizontal, 18)
                        .padding(.vertical, 8)
                    }
                }
            }
        }
    }
}

struct UserSearchBar: View {
    @Binding var query: String
    var onQueryChange: (String) -> Void
    var onClearClick: () -> Void
    
    var body: some View {
        HStack {
            Image(systemName: "magnifyingglass")
                .foregroundColor(.textSecondary)
                .padding(.leading, 16)
            
            TextField(
                "Search farmers, experts...",
                text: $query
            )
            .textFieldStyle(PlainTextFieldStyle())
            .onChange(of: query) { newValue in
                onQueryChange(newValue)
            }
            .font(.system(size: 16))
            .foregroundColor(.textPrimary)
            
            if !query.isEmpty {
                Button(action: onClearClick) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.textSecondary)
                        .font(.system(size: 20))
                }
                .padding(.trailing, 16)
            }
        }
        .frame(height: 48)
        .background(Color.white)
        .cornerRadius(24)
        .overlay(
            RoundedRectangle(cornerRadius: 24)
                .stroke(
                    query.isEmpty ? Color.black.opacity(0.1) : Color.primaryGreen,
                    lineWidth: 1
                )
        )
    }
}

struct UserSearchResultItem: View {
    let user: UserInfo
    let onClick: () -> Void
    
    var body: some View {
        Button(action: onClick) {
            HStack(spacing: 12) {
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
                        .frame(width: 50, height: 50)
                    
                    if let urlString = user.profileImageUrl, let url = URL(string: ensureHttps(urlString)) {
                        AsyncImage(url: url) { image in
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                        } placeholder: {
                            Text(String(user.name.prefix(1)).uppercased())
                                .font(.system(size: 20, weight: .semibold))
                                .foregroundColor(.white)
                        }
                        .frame(width: 46, height: 46)
                        .clipShape(Circle())
                    } else {
                        Text(String(user.name.prefix(1)).uppercased())
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundColor(.white)
                    }
                }
                
                // User Info
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(user.name)
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(.textPrimary)
                            .lineLimit(1)
                        
                        if user.verificationStatus == .verified {
                            Image(systemName: "checkmark.seal.fill")
                                .font(.system(size: 14))
                                .foregroundColor(Color(red: 0.129, green: 0.588, blue: 0.953))
                        }
                    }
                    
                    Text("@\(user.username)")
                        .font(.system(size: 14))
                        .foregroundColor(.textSecondary)
                        .lineLimit(1)
                    
                    // Role Badge
                    Text(roleLabel(user.role))
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(.primaryGreen)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(expertGreen.opacity(0.15))
                        .cornerRadius(12)
                }
                
                Spacer()
            }
            .padding(12)
            .background(Color.white)
            .cornerRadius(12)
            .shadow(color: Color.black.opacity(0.05), radius: 2, x: 0, y: 1)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

struct SuggestionsContent: View {
    let sections: [SuggestionSection]
    let isRefreshing: Bool
    let onRefresh: () -> Void
    let onPostClick: (String, Post?) -> Void
    let onUserClick: (String) -> Void
    let onFollowClick: (String) -> Void
    
    var body: some View {
        ScrollView {
            if !sections.isEmpty {
                LazyVStack(spacing: 16) {
                    ForEach(Array(sections.enumerated()), id: \.offset) { index, section in
                        switch section {
                        case .userRow(let users):
                            SuggestedUsersRow(
                                users: users,
                                onUserClick: onUserClick,
                                onFollowClick: onFollowClick
                            )
                        case .postGrid(let posts):
                            SuggestedPostsGrid(
                                posts: posts,
                                onPostClick: onPostClick
                            )
                        }
                    }
                }
                .padding(.horizontal, 18)
                .padding(.vertical, 8)
            } else {
                // Empty state if no suggestions
                VStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 64))
                        .foregroundColor(.textSecondary.opacity(0.5))
                    
                    Text("No suggestions available")
                        .foregroundColor(.textSecondary)
                        .font(.system(size: 16))
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 32)
            }
        }
        .refreshable {
            onRefresh()
        }
    }
}

struct SuggestedUsersRow: View {
    let users: [UserInfo]
    let onUserClick: (String) -> Void
    let onFollowClick: (String) -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("People to Follow")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.textPrimary)
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(users, id: \.id) { user in
                        SuggestedUserCard(
                            user: user,
                            onClick: { onUserClick(user.id) },
                            onFollowClick: { onFollowClick(user.id) }
                        )
                    }
                }
            }
        }
    }
}

struct SuggestedPostsGrid: View {
    let posts: [Post]
    let onPostClick: (String, Post?) -> Void
    
    var body: some View {
        GeometryReader { geometry in
            let spacing: CGFloat = 8
            let itemWidth = (geometry.size.width - spacing) / 2
            
            VStack(spacing: spacing) {
                ForEach(Array(posts.chunked(into: 2).enumerated()), id: \.offset) { _, rowPosts in
                    HStack(spacing: spacing) {
                        ForEach(rowPosts, id: \.id) { post in
                            SuggestedPostItem(
                                post: post,
                                onClick: { onPostClick(post.id, post) }
                            )
                            .frame(width: itemWidth, height: itemWidth)
                        }
                        
                        // Fill remaining space if row has less than 2 items
                        if rowPosts.count < 2 {
                            Color.clear
                                .frame(width: itemWidth, height: itemWidth)
                        }
                    }
                }
            }
        }
        .frame(height: calculateGridHeight(postCount: posts.count))
    }
    
    private func calculateGridHeight(postCount: Int) -> CGFloat {
        let spacing: CGFloat = 8
        let rows = ceil(Double(postCount) / 2.0)
        let screenWidth = UIScreen.main.bounds.width
        let horizontalPadding: CGFloat = 36
        let availableWidth = screenWidth - horizontalPadding
        let itemWidth = (availableWidth - spacing) / 2
        
        return CGFloat(rows) * itemWidth + CGFloat(max(0, rows - 1)) * spacing
    }
}

struct SuggestedPostItem: View {
    let post: Post
    let onClick: () -> Void
    
    var body: some View {
        Button(action: onClick) {
            GeometryReader { geometry in
                ZStack(alignment: .bottomTrailing) {
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
                                        .fill(Color.primaryGreen.opacity(0.1))
                                        .frame(width: geometry.size.width, height: geometry.size.height)
                                        .overlay(
                                            Image(systemName: "photo")
                                                .foregroundColor(.textSecondary.opacity(0.5))
                                                .font(.system(size: 24))
                                        )
                                }
                            } else {
                                Rectangle()
                                    .fill(Color.primaryGreen.opacity(0.1))
                                    .frame(width: geometry.size.width, height: geometry.size.height)
                                    .overlay(
                                        Image(systemName: "photo")
                                            .foregroundColor(.textSecondary.opacity(0.5))
                                            .font(.system(size: 24))
                                    )
                            }
                        } else {
                            Rectangle()
                                .fill(Color.primaryGreen.opacity(0.1))
                                .frame(width: geometry.size.width, height: geometry.size.height)
                                .overlay(
                                    Image(systemName: "photo")
                                        .foregroundColor(.textSecondary.opacity(0.5))
                                        .font(.system(size: 24))
                                )
                        }
                    }
                    
                    // Likes overlay
                    HStack(spacing: 4) {
                        Image(systemName: "heart.fill")
                            .font(.system(size: 12))
                            .foregroundColor(.white)
                        
                        Text("\(post.likesCount)")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.white)
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.black.opacity(0.6))
                    .cornerRadius(16)
                    .padding(8)
                }
                .cornerRadius(12)
                .clipped()
            }
            .shadow(color: Color.black.opacity(0.1), radius: 2, x: 0, y: 1)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

struct SuggestedUserCard: View {
    let user: UserInfo
    let onClick: () -> Void
    let onFollowClick: () -> Void
    
    var body: some View {
        VStack(spacing: 8) {
            // Avatar (clickable to navigate)
            Button(action: onClick) {
                ZStack {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [.primaryGreen, .accentYellow],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                        .frame(width: 60, height: 60)
                    
                    if let urlString = user.profileImageUrl, let url = URL(string: ensureHttps(urlString)) {
                        AsyncImage(url: url) { image in
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                        } placeholder: {
                            Text(String(user.name.prefix(1)).uppercased())
                                .font(.system(size: 24, weight: .semibold))
                                .foregroundColor(.white)
                        }
                        .frame(width: 56, height: 56)
                        .clipShape(Circle())
                    } else {
                        Text(String(user.name.prefix(1)).uppercased())
                            .font(.system(size: 24, weight: .semibold))
                            .foregroundColor(.white)
                    }
                }
            }
            .buttonStyle(PlainButtonStyle())
            
            // User Info
            VStack(spacing: 4) {
                Button(action: onClick) {
                    HStack(spacing: 4) {
                        Text(user.name)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.textPrimary)
                            .lineLimit(1)
                        
                        if user.verificationStatus == .verified {
                            Image(systemName: "checkmark.seal.fill")
                                .font(.system(size: 12))
                                .foregroundColor(Color(red: 0.129, green: 0.588, blue: 0.953))
                        }
                    }
                }
                .buttonStyle(PlainButtonStyle())
                
                Text("@\(user.username)")
                    .font(.system(size: 12))
                    .foregroundColor(.textSecondary)
                    .lineLimit(1)
                
                // Follow Button
                Button(action: {
                    onFollowClick()
                    onClick()
                }) {
                    Text("Follow")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                        .background(Color.primaryGreen)
                        .cornerRadius(8)
                }
            }
        }
        .frame(width: 160)
        .padding(12)
        .background(Color.white)
        .cornerRadius(12)
        .shadow(color: Color.black.opacity(0.05), radius: 2, x: 0, y: 1)
    }
}

struct SuggestedUserGridItem: View {
    let user: UserInfo
    let onClick: () -> Void
    let onFollowClick: () -> Void
    
    var body: some View {
        Button(action: onClick) {
            ZStack {
                // Background gradient
                Rectangle()
                    .fill(
                        LinearGradient(
                            colors: [
                                Color.primaryGreen.opacity(0.1),
                                Color.accentYellow.opacity(0.1)
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                
                VStack(spacing: 8) {
                    Spacer()
                    
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
                            .frame(width: 60, height: 60)
                        
                        if let urlString = user.profileImageUrl, let url = URL(string: ensureHttps(urlString)) {
                            AsyncImage(url: url) { image in
                                image
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                            } placeholder: {
                                Text(String(user.name.prefix(1)).uppercased())
                                    .font(.system(size: 20, weight: .semibold))
                                    .foregroundColor(.white)
                            }
                            .frame(width: 56, height: 56)
                            .clipShape(Circle())
                        } else {
                            Text(String(user.name.prefix(1)).uppercased())
                                .font(.system(size: 20, weight: .semibold))
                                .foregroundColor(.white)
                        }
                    }
                    
                    // User Info
                    VStack(spacing: 4) {
                        HStack(spacing: 4) {
                            Text(user.name)
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(.textPrimary)
                                .lineLimit(1)
                            
                            if user.verificationStatus == .verified {
                                Image(systemName: "checkmark.seal.fill")
                                    .font(.system(size: 10))
                                    .foregroundColor(Color(red: 0.129, green: 0.588, blue: 0.953))
                            }
                        }
                        
                        Text("@\(user.username)")
                            .font(.system(size: 10))
                            .foregroundColor(.textSecondary)
                            .lineLimit(1)
                        
                        // Follow Button
                        Button(action: {
                            onFollowClick()
                            onClick()
                        }) {
                            Text("Follow")
                                .font(.system(size: 10, weight: .medium))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 4)
                                .background(Color.primaryGreen)
                                .cornerRadius(6)
                        }
                    }
                    .padding(.horizontal, 8)
                    
                    Spacer()
                }
                .padding(.vertical, 12)
            }
            .aspectRatio(1, contentMode: .fit)
            .cornerRadius(12)
            .shadow(color: Color.black.opacity(0.1), radius: 2, x: 0, y: 1)
        }
        .buttonStyle(PlainButtonStyle())
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
    SearchView()
}
