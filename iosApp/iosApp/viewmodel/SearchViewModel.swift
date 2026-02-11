import Foundation
import SwiftUI
import Shared

@MainActor
class SearchViewModel: ObservableObject {
    @Published var query: String = ""
    @Published var results: [UserInfo] = []
    @Published var isLoading: Bool = false
    @Published var error: String?
    @Published var hasSearched: Bool = false

    private let prefs = IOSPreferencesRepository()
    private lazy var authRepo = IOSAuthRepository(preferencesRepository: prefs)
    private lazy var userRepo = FirestoreUserRepository(authRepository: authRepo)
    
    private var searchTask: Task<Void, Never>?

    func setQuery(_ newQuery: String) {
        query = newQuery
        error = nil
        
        // Cancel previous search
        searchTask?.cancel()
        
        if newQuery.isEmpty {
            results = []
            hasSearched = false
            isLoading = false
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
    }
}
