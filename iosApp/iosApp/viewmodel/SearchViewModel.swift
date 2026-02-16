import Foundation
import SwiftUI
import Shared

enum SuggestionSection {
    case userRow([UserInfo])
    case postGrid([Post])
}

@MainActor
class SearchViewModel: ObservableObject {
    @Published var query: String = ""
    @Published var results: [UserInfo] = []
    @Published var suggestedPosts: [Post] = []
    @Published var suggestedUsers: [UserInfo] = []
    @Published var suggestionSections: [SuggestionSection] = []
    @Published var isLoading: Bool = false
    @Published var isRefreshingSuggestions: Bool = false
    @Published var error: String?
    @Published var hasSearched: Bool = false

    private let prefs = IOSPreferencesRepository()
    private lazy var authRepo = IOSAuthRepository(preferencesRepository: prefs)
    private lazy var userRepo = FirestoreUserRepository(authRepository: authRepo)
    private lazy var followRepo = IOSFollowRepository(authRepository: authRepo, userRepository: userRepo)
    private lazy var postRepo = FirestorePostRepository(authRepository: authRepo, userRepository: userRepo)
    private lazy var followUserUseCase = FollowUserUseCase(followRepository: followRepo)
    
    private var searchTask: Task<Void, Never>?
    
    init() {
        // Load suggestions when ViewModel is created
        Task {
            await loadSuggestions()
        }
    }

    func setQuery(_ newQuery: String) {
        query = newQuery
        error = nil
        
        // Cancel previous search
        searchTask?.cancel()
        
        if newQuery.isEmpty {
            results = []
            hasSearched = false
            isLoading = false
            // Reload suggestions when query is cleared
            Task {
                await loadSuggestions()
            }
            return
        }

        // Debounce search by 500ms
        searchTask = Task {
            try? await Task.sleep(nanoseconds: 500_000_000) // 500ms
            if !Task.isCancelled {
                await performSearch(newQuery)
            }
        }
    }

    private func performSearch(_ query: String) async {
        // Allow single character searches for live incremental search
        isLoading = true
        error = nil
        
        do {
            let searchResults = try await userRepo.searchUsers(query: query, limit: 20)
            await MainActor.run {
                self.results = searchResults
                self.isLoading = false
                self.hasSearched = true
            }
        } catch {
            await MainActor.run {
                self.isLoading = false
                self.error = error.localizedDescription
                self.hasSearched = true
            }
        }
    }

    func clearSearch() {
        searchTask?.cancel()
        query = ""
        results = []
        hasSearched = false
        isLoading = false
        error = nil
        Task {
            await loadSuggestions()
        }
    }
    
    private func createSuggestionSections(posts: [Post], users: [UserInfo]) -> [SuggestionSection] {
        var sections: [SuggestionSection] = []
        
        // Shuffle posts and users separately
        let shuffledPosts = posts.shuffled()
        let shuffledUsers = users.shuffled()
        
        // Split posts into chunks of 6 (3 rows of 2-column grid)
        let postChunks = shuffledPosts.chunked(into: 6)
        
        // Split users into chunks of 5-7 users per row
        let userChunks = shuffledUsers.chunked(into: 6)
        
        // Create alternating sections: user rows and post grids
        let maxSections = max(postChunks.count, userChunks.count)
        
        for i in 0..<maxSections {
            // Add user row if available
            if i < userChunks.count && !userChunks[i].isEmpty {
                sections.append(.userRow(userChunks[i]))
            }
            
            // Add post grid if available
            if i < postChunks.count && !postChunks[i].isEmpty {
                sections.append(.postGrid(postChunks[i]))
            }
        }
        
        // Shuffle the order of sections but keep users grouped and posts grouped
        return sections.shuffled()
    }
    
    func loadSuggestions() async {
        do {
            async let postsResult = postRepo.getRandomPosts(limit: 20)
            async let usersResult = userRepo.getSuggestedUsers(limit: 10)
            
            let (posts, users) = try await (postsResult, usersResult)
            let sections = createSuggestionSections(posts: posts, users: users)
            
            await MainActor.run {
                self.suggestedPosts = posts
                self.suggestedUsers = users
                self.suggestionSections = sections
                self.error = nil
            }
        } catch {
            await MainActor.run {
                self.error = error.localizedDescription
            }
        }
    }
    
    func refreshSuggestions() async {
        await MainActor.run {
            self.isRefreshingSuggestions = true
            self.error = nil
        }
        
        do {
            async let postsResult = postRepo.getRandomPosts(limit: 20)
            async let usersResult = userRepo.getSuggestedUsers(limit: 10)
            
            let (posts, users) = try await (postsResult, usersResult)
            let sections = createSuggestionSections(posts: posts, users: users)
            
            await MainActor.run {
                self.suggestedPosts = posts
                self.suggestedUsers = users
                self.suggestionSections = sections
                self.isRefreshingSuggestions = false
                self.error = nil
            }
        } catch {
            await MainActor.run {
                self.isRefreshingSuggestions = false
                self.error = error.localizedDescription
            }
        }
    }
    
    func followUser(userId: String) async {
        do {
            // Follow the user (isCurrentlyFollowing = false since these are suggestions)
            try await followUserUseCase.invoke(userId: userId, isCurrentlyFollowing: false)
            // Remove from suggestions and refresh
            await MainActor.run {
                var updatedUsers = self.suggestedUsers.filter { $0.id != userId }
                let sections = self.createSuggestionSections(posts: self.suggestedPosts, users: updatedUsers)
                self.suggestedUsers = updatedUsers
                self.suggestionSections = sections
                // Optionally reload one more suggestion
                if updatedUsers.count < 10 {
                    Task {
                        if let newUsers = try? await userRepo.getSuggestedUsers(limit: 1), !newUsers.isEmpty {
                            await MainActor.run {
                                let finalUsers = updatedUsers + newUsers
                                let finalSections = self.createSuggestionSections(posts: self.suggestedPosts, users: finalUsers)
                                self.suggestedUsers = finalUsers
                                self.suggestionSections = finalSections
                            }
                        }
                    }
                }
            }
        } catch {
            // Silently fail - user can follow from profile if needed
        }
    }
}

// Helper extension for chunking arrays
extension Array {
    func chunked(into size: Int) -> [[Element]] {
        return stride(from: 0, to: count, by: size).map {
            Array(self[$0..<Swift.min($0 + size, count)])
        }
    }
}
