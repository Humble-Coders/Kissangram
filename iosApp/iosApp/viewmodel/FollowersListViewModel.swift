import Foundation
import SwiftUI
import Shared

enum FollowersListType {
    case followers
    case following
}

@MainActor
class FollowersListViewModel: ObservableObject {
    @Published var users: [UserInfo] = []
    @Published var isLoading: Bool = false
    @Published var isLoadingMore: Bool = false
    @Published var error: String?
    @Published var hasMore: Bool = true
    @Published var isRefreshing: Bool = false
    
    private let userId: String
    private let type: FollowersListType
    private let pageSize: Int32 = 20
    private var currentPage: Int32 = 0
    
    private let prefs = IOSPreferencesRepository()
    private lazy var authRepo = IOSAuthRepository(preferencesRepository: prefs)
    private lazy var userRepo = FirestoreUserRepository(authRepository: authRepo)
    
    init(userId: String, type: FollowersListType) {
        self.userId = userId
        self.type = type
        Task {
            await loadUsers()
        }
    }
    
    func loadUsers() async {
        isLoading = true
        error = nil
        currentPage = 0
        
        do {
            let fetchedUsers = try await fetchUsers(page: 0)
            users = fetchedUsers
            isLoading = false
            hasMore = fetchedUsers.count >= pageSize
        } catch {
            self.error = error.localizedDescription
            isLoading = false
        }
    }
    
    func loadMore() async {
        guard !isLoadingMore, hasMore else { return }
        
        let nextPage = currentPage + 1
        isLoadingMore = true
        error = nil
        
        do {
            let newUsers = try await fetchUsers(page: nextPage)
            users.append(contentsOf: newUsers)
            isLoadingMore = false
            hasMore = newUsers.count >= pageSize
            currentPage = nextPage
        } catch {
            self.error = error.localizedDescription
            isLoadingMore = false
        }
    }
    
    func refresh() async {
        isRefreshing = true
        error = nil
        currentPage = 0
        
        do {
            let fetchedUsers = try await fetchUsers(page: 0)
            users = fetchedUsers
            isRefreshing = false
            hasMore = fetchedUsers.count >= pageSize
        } catch {
            self.error = error.localizedDescription
            isRefreshing = false
        }
    }
    
    private func fetchUsers(page: Int32) async throws -> [UserInfo] {
        switch type {
        case .followers:
            return try await userRepo.getFollowers(userId: userId, page: page, pageSize: pageSize)
        case .following:
            return try await userRepo.getFollowing(userId: userId, page: page, pageSize: pageSize)
        }
    }
}
