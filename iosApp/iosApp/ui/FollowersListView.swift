import SwiftUI
import Shared

private let followersListBackground = Color(red: 0.984, green: 0.973, blue: 0.941)

struct FollowersListView: View {
    let userId: String
    let type: FollowersListType
    var onBackClick: () -> Void = {}
    var onUserClick: (String) -> Void = { _ in }
    
    @StateObject private var viewModel: FollowersListViewModel
    
    init(userId: String, type: FollowersListType, onBackClick: @escaping () -> Void = {}, onUserClick: @escaping (String) -> Void = { _ in }) {
        self.userId = userId
        self.type = type
        self.onBackClick = onBackClick
        self.onUserClick = onUserClick
        _viewModel = StateObject(wrappedValue: FollowersListViewModel(userId: userId, type: type))
    }
    
    var body: some View {
        NavigationStack {
            ZStack {
                followersListBackground
                
                VStack(spacing: 0) {
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
                                Task {
                                    await viewModel.loadUsers()
                                }
                            }
                            .foregroundColor(.white)
                            .padding(.horizontal, 24)
                            .padding(.vertical, 12)
                            .background(Color.primaryGreen)
                            .cornerRadius(8)
                        }
                        Spacer()
                    } else if viewModel.users.isEmpty {
                        Spacer()
                        VStack(spacing: 8) {
                            Text(type == .followers ? "No followers yet" : "Not following anyone yet")
                                .foregroundColor(.textSecondary)
                                .font(.system(size: 16, weight: .medium))
                            
                            Text(type == .followers ? "When someone follows you, they'll appear here" : "Start following people to see them here")
                                .foregroundColor(.textSecondary.opacity(0.7))
                                .font(.system(size: 14))
                        }
                        Spacer()
                    } else {
                        ScrollView {
                            LazyVStack(spacing: 8) {
                                ForEach(Array(viewModel.users.enumerated()), id: \.element.id) { index, user in
                                    UserSearchResultItem(
                                        user: user,
                                        onClick: { onUserClick(user.id) }
                                    )
                                    .onAppear {
                                        // Load more when reaching near the end
                                        if index >= viewModel.users.count - 3 && viewModel.hasMore && !viewModel.isLoadingMore {
                                            Task {
                                                await viewModel.loadMore()
                                            }
                                        }
                                    }
                                }
                                
                                // Loading more indicator
                                if viewModel.isLoadingMore {
                                    ProgressView()
                                        .padding()
                                }
                            }
                            .padding(.horizontal, 18)
                            .padding(.vertical, 8)
                        }
                        .refreshable {
                            await viewModel.refresh()
                        }
                    }
                }
            }
            .navigationTitle(type == .followers ? "Followers" : "Following")
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
    }
}

