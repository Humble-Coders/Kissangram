import SwiftUI
import Shared

private let searchBackground = Color(red: 0.984, green: 0.973, blue: 0.941)
private let expertGreen = Color(red: 0.455, green: 0.765, blue: 0.396)

struct SearchView: View {
    @StateObject private var viewModel = SearchViewModel()
    
    var onUserClick: (String) -> Void = { _ in }

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
                    // Empty state
                    Spacer()
                    VStack(spacing: 8) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 64))
                            .foregroundColor(.textSecondary.opacity(0.5))
                        Text("Search for farmers, experts, and more")
                            .foregroundColor(.textSecondary)
                            .font(.system(size: 16))
                    }
                    Spacer()
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
