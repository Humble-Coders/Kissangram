import Foundation
import os.log
import Shared

@MainActor
class StoryViewModel: ObservableObject {
    
    private static let log = Logger(subsystem: "com.kissangram", category: "StoryViewModel")
    private let prefs = IOSPreferencesRepository()
    private let authRepository: AuthRepository
    private let storyRepository: StoryRepository
    private let getStoryBarUseCase: GetStoryBarUseCase
    
    @Published var userStories: [UserStories] = []
    @Published var currentUserIndex: Int = 0
    @Published var currentStoryIndex: Int = 0
    @Published var isLoading: Bool = false
    @Published var error: String? = nil
    @Published var allStoriesFinished: Bool = false
    
    private var autoAdvanceTask: Task<Void, Never>? = nil
    private let STORY_DURATION_MS: UInt64 = 5_000_000_000 // 5 seconds in nanoseconds
    
    init(initialUserId: String) {
        self.authRepository = IOSAuthRepository(preferencesRepository: prefs)
        let userRepository = FirestoreUserRepository(authRepository: authRepository)
        let followRepository = IOSFollowRepository(authRepository: authRepository, userRepository: userRepository)
        self.storyRepository = FirestoreStoryRepository(authRepository: authRepository, followRepository: followRepository)
        self.getStoryBarUseCase = GetStoryBarUseCase(storyRepository: storyRepository)
        
        Task {
            await loadStories(initialUserId: initialUserId)
        }
    }
    
    func loadStories(initialUserId: String) async {
        isLoading = true
        error = nil
        Self.log.debug("loadStories: start, initialUserId=\(initialUserId)")
        
        do {
            let allUserStories = try await storyRepository.getStoryBar()
            Self.log.debug("loadStories: loaded \(allUserStories.count) user stories")
            
            if allUserStories.isEmpty {
                isLoading = false
                error = "No stories available"
                return
            }
            
            // Find initial user index
            let initialIndex = allUserStories.firstIndex { $0.userId == initialUserId } ?? 0
            
            self.userStories = allUserStories
            self.currentUserIndex = initialIndex
            self.currentStoryIndex = 0
            self.isLoading = false
            
            // Start auto-advance for first story
            startAutoAdvance()
            await markCurrentStoryAsViewed()
        } catch {
            Self.log.error("loadStories: failed: \(error.localizedDescription)")
            isLoading = false
            self.error = error.localizedDescription
        }
    }
    
    func nextStory() -> Bool {
        guard let currentUserStories = getCurrentUserStories() else { return false }
        
        if currentStoryIndex < currentUserStories.stories.count - 1 {
            // Move to next story in current user
            currentStoryIndex += 1
            startAutoAdvance()
            Task {
                await markCurrentStoryAsViewed()
            }
            return true
        } else {
            // Move to next user
            return nextUser()
        }
    }
    
    func previousStory() {
        guard let currentUserStories = getCurrentUserStories() else { return }
        
        if currentStoryIndex > 0 {
            // Move to previous story in current user
            currentStoryIndex -= 1
            startAutoAdvance()
            Task {
                await markCurrentStoryAsViewed()
            }
        } else {
            // Move to previous user
            previousUser()
        }
    }
    
    func nextUser() -> Bool {
        if currentUserIndex < userStories.count - 1 {
            currentUserIndex += 1
            currentStoryIndex = 0
            startAutoAdvance()
            Task {
                await markCurrentStoryAsViewed()
            }
            return true
        } else {
            // All stories finished
            allStoriesFinished = true
            return false
        }
    }
    
    func previousUser() {
        if currentUserIndex > 0 {
            let previousUserStories = userStories[currentUserIndex - 1]
            currentUserIndex -= 1
            currentStoryIndex = previousUserStories.stories.count - 1 // Start at last story
            startAutoAdvance()
            Task {
                await markCurrentStoryAsViewed()
            }
        }
    }
    
    func pauseAutoAdvance() {
        autoAdvanceTask?.cancel()
        autoAdvanceTask = nil
    }
    
    func resumeAutoAdvance() {
        startAutoAdvance()
    }
    
    private func startAutoAdvance() {
        autoAdvanceTask?.cancel()
        autoAdvanceTask = Task {
            try? await Task.sleep(nanoseconds: STORY_DURATION_MS)
            if !Task.isCancelled {
                let hasMore = nextStory()
                if !hasMore {
                    allStoriesFinished = true
                }
            }
        }
    }
    
    private func markCurrentStoryAsViewed() async {
        guard let currentStory = getCurrentStory() else { return }
        
        if !currentStory.isViewedByMe {
            do {
                try await storyRepository.markStoryAsViewed(storyId: currentStory.id)
                // Update local state
                if let userIndex = userStories.firstIndex(where: { $0.userId == getCurrentUserStories()?.userId }),
                   let storyIndex = userStories[userIndex].stories.firstIndex(where: { $0.id == currentStory.id }) {
                    var updatedStories = userStories[userIndex].stories
                    updatedStories[storyIndex] = Story(
                        id: currentStory.id,
                        authorId: currentStory.authorId,
                        authorName: currentStory.authorName,
                        authorUsername: currentStory.authorUsername,
                        authorProfileImageUrl: currentStory.authorProfileImageUrl,
                        authorRole: currentStory.authorRole,
                        authorVerificationStatus: currentStory.authorVerificationStatus,
                        media: currentStory.media,
                        textOverlay: currentStory.textOverlay,
                        locationName: currentStory.locationName,
                        visibility: currentStory.visibility,
                        viewsCount: currentStory.viewsCount,
                        likesCount: currentStory.likesCount,
                        isViewedByMe: true,
                        isLikedByMe: currentStory.isLikedByMe,
                        createdAt: currentStory.createdAt,
                        expiresAt: currentStory.expiresAt
                    )
                    userStories[userIndex] = UserStories(
                        userId: userStories[userIndex].userId,
                        userName: userStories[userIndex].userName,
                        userProfileImageUrl: userStories[userIndex].userProfileImageUrl,
                        userRole: userStories[userIndex].userRole,
                        userVerificationStatus: userStories[userIndex].userVerificationStatus,
                        stories: updatedStories,
                        hasUnviewedStories: updatedStories.contains { !$0.isViewedByMe },
                        latestStoryTime: userStories[userIndex].latestStoryTime
                    )
                }
            } catch {
                Self.log.error("markCurrentStoryAsViewed: failed: \(error.localizedDescription)")
            }
        }
    }
    
    func getCurrentUserStories() -> UserStories? {
        guard currentUserIndex < userStories.count else { return nil }
        return userStories[currentUserIndex]
    }
    
    func getCurrentStory() -> Story? {
        guard let userStories = getCurrentUserStories(),
              currentStoryIndex < userStories.stories.count else { return nil }
        return userStories.stories[currentStoryIndex]
    }
    
    deinit {
        autoAdvanceTask?.cancel()
    }
}
