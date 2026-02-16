import Foundation
import os.log
import Shared

@MainActor
class HomeViewModel: ObservableObject {

    private static let log = Logger(subsystem: "com.kissangram", category: "HomeViewModel")
    private let prefs = IOSPreferencesRepository()
    private let authRepository: AuthRepository
    private let followRepository: FollowRepository
    private let feedRepository: FeedRepository
    private let postRepository: PostRepository
    private let storyRepository: StoryRepository

    private let getHomeFeedUseCase: GetHomeFeedUseCase
    private let followUserUseCase: FollowUserUseCase
    private let getStoryBarUseCase: GetStoryBarUseCase
    private let likePostUseCase: LikePostUseCase
    private let savePostUseCase: SavePostUseCase

    @Published var stories: [UserStories] = []
    @Published var posts: [Post] = []
    @Published var authorIdToIsFollowing: [String: Bool] = [:]
    @Published var currentUserId: String?
    @Published var isLoading = false
    @Published var isRefreshing = false
    @Published var isLoadingMore = false
    @Published var hasMorePosts = true
    @Published var error: String?

    private var currentPage: Int32 = 0
    
    // Track posts currently being processed to prevent race conditions
    private var postsBeingProcessed = Set<String>()

    init() {
        self.authRepository = IOSAuthRepository(preferencesRepository: prefs)
        let userRepository = FirestoreUserRepository(authRepository: authRepository)
        self.followRepository = IOSFollowRepository(authRepository: authRepository, userRepository: userRepository)
        self.feedRepository = FirestoreFeedRepository(authRepository: authRepository)
        self.postRepository = FirestorePostRepository(authRepository: authRepository, userRepository: userRepository)
        self.storyRepository = FirestoreStoryRepository(authRepository: authRepository, followRepository: followRepository)

        self.getHomeFeedUseCase = GetHomeFeedUseCase(feedRepository: feedRepository)
        self.getStoryBarUseCase = GetStoryBarUseCase(storyRepository: storyRepository)
        self.followUserUseCase = FollowUserUseCase(followRepository: followRepository)
        self.likePostUseCase = LikePostUseCase(postRepository: postRepository)
        self.savePostUseCase = SavePostUseCase(postRepository: postRepository)

        Self.log.debug("HomeViewModel init, calling loadContent")
        Task {
            await loadContent()
        }
    }
    
    func loadContent() async {
        isLoading = true
        error = nil
        Self.log.debug("loadContent: start")
        do {
            async let storiesResult = storyRepository.getStoryBar()
            async let postsResult = feedRepository.getHomeFeed(page: 0, pageSize: 20, forceRefresh: false)
            
            let (loadedStories, loadedPosts) = try await (storiesResult, postsResult)
            
            Self.log.debug("loadContent: stories=\(loadedStories.count) posts=\(loadedPosts.count) firstPostId=\(loadedPosts.first?.id ?? "nil")")
            let userId = try? await authRepository.getCurrentUserId()
            var followingMap: [String: Bool] = [:]
            for authorId in Set(loadedPosts.map { $0.authorId }).filter({ $0 != userId }) {
                if let isFollowing = try? await followRepository.isFollowing(userId: authorId) {
                    followingMap[authorId] = isFollowing.boolValue
                }
            }
            self.stories = loadedStories
            self.posts = loadedPosts
            self.authorIdToIsFollowing = followingMap
            self.currentUserId = userId
            self.currentPage = 0
            self.isLoading = false
            Self.log.debug("loadContent: success")
        } catch {
            Self.log.error("loadContent: error=\(error.localizedDescription)")
            self.isLoading = false
            self.error = error.localizedDescription
        }
    }
    
    func refreshFeed() async {
        isRefreshing = true
        Self.log.debug("refreshFeed: start")
        do {
            async let storiesResult = storyRepository.getStoryBar()
            async let postsResult = feedRepository.getHomeFeed(page: 0, pageSize: 20, forceRefresh: true)
            
            let (loadedStories, loadedPosts) = try await (storiesResult, postsResult)
            Self.log.debug("refreshFeed: stories=\(loadedStories.count) posts=\(loadedPosts.count)")
            let userId = try? await authRepository.getCurrentUserId()
            var followingMap: [String: Bool] = [:]
            for authorId in Set(loadedPosts.map { $0.authorId }).filter({ $0 != userId }) {
                if let isFollowing = try? await followRepository.isFollowing(userId: authorId) {
                    followingMap[authorId] = isFollowing.boolValue
                }
            }
            self.stories = loadedStories
            self.posts = loadedPosts
            self.authorIdToIsFollowing = followingMap
            self.currentUserId = userId
            self.currentPage = 0
            self.isRefreshing = false
        } catch {
            Self.log.error("refreshFeed: error=\(error.localizedDescription)")
            self.isRefreshing = false
        }
    }
    
    func loadMorePosts() async {
        guard !isLoadingMore && hasMorePosts else { return }
        isLoadingMore = true
        let page = currentPage + 1
        Self.log.debug("loadMorePosts: currentPage=\(self.currentPage) nextPage=\(page)")
        do {
            let newPosts = try await feedRepository.getHomeFeed(page: page, pageSize: 20, forceRefresh: false)
            Self.log.debug("loadMorePosts: newPosts=\(newPosts.count)")
            if !newPosts.isEmpty {
                let userId = try? await authRepository.getCurrentUserId()
                var newFollowingMap = self.authorIdToIsFollowing
                for authorId in Set(newPosts.map { $0.authorId }).filter({ $0 != userId && !newFollowingMap.keys.contains($0) }) {
                    if let isFollowing = try? await followRepository.isFollowing(userId: authorId) {
                        newFollowingMap[authorId] = isFollowing.boolValue
                    }
                }
                self.posts.append(contentsOf: newPosts)
                self.authorIdToIsFollowing = newFollowingMap
                self.currentPage += 1
            } else {
                self.hasMorePosts = false
            }
            self.isLoadingMore = false
        } catch {
            Self.log.error("loadMorePosts: error=\(error.localizedDescription)")
            self.isLoadingMore = false
        }
    }
    
    /**
     * Handles like/unlike action for a post.
     * @return true if the request was accepted and processed, false if it was ignored (e.g., already processing)
     */
    func onLikePost(_ postId: String) -> Bool {
        // Prevent multiple simultaneous requests for the same post
        guard !postsBeingProcessed.contains(postId) else {
            Self.log.debug("onLikePost: post \(postId) is already being processed, ignoring")
            return false
        }
        
        guard let index = posts.firstIndex(where: { $0.id == postId }) else { return false }
        
        let post = posts[index]
        let newLikedState = !post.isLikedByMe
        let newLikesCount = newLikedState ? post.likesCount + 1 : post.likesCount - 1
        
        // Mark as being processed
        postsBeingProcessed.insert(postId)
        
        // ⚡ INSTAGRAM APPROACH: Update UI IMMEDIATELY (synchronous, main thread)
        // Since we're @MainActor, this happens synchronously before any async work
        // User sees instant feedback with zero perceived lag
        posts[index] = Post(
            id: post.id,
            authorId: post.authorId,
            authorName: post.authorName,
            authorUsername: post.authorUsername,
            authorProfileImageUrl: post.authorProfileImageUrl,
            authorRole: post.authorRole,
            authorVerificationStatus: post.authorVerificationStatus,
            type: post.type,
            text: post.text,
            media: post.media,
            voiceCaption: post.voiceCaption,
            crops: post.crops,
            hashtags: post.hashtags,
            location: post.location,
            question: post.question,
            likesCount: newLikesCount,
            commentsCount: post.commentsCount,
            savesCount: post.savesCount,
            isLikedByMe: newLikedState,
            isSavedByMe: post.isSavedByMe,
            createdAt: post.createdAt,
            updatedAt: post.updatedAt
        )
        
        Task {
            defer {
                // Always remove from processing set
                postsBeingProcessed.remove(postId)
            }
            
            do {
                try await likePostUseCase.invoke(postId: postId, isCurrentlyLiked: post.isLikedByMe)
            } catch {
                Self.log.error("onLikePost: failed for postId=\(postId), error=\(error.localizedDescription)")
                // Revert on failure
                self.posts[index] = post
            }
        }
        
        return true
    }
    
    func onSavePost(_ postId: String) {
        // Prevent multiple simultaneous requests for the same post
        let saveKey = "save_\(postId)"
        guard !postsBeingProcessed.contains(saveKey) else {
            Self.log.debug("onSavePost: post \(postId) is already being processed, ignoring")
            return
        }
        
        guard let index = posts.firstIndex(where: { $0.id == postId }) else { return }
        
        let post = posts[index]
        let newSavedState = !post.isSavedByMe
        
        // Mark as being processed
        postsBeingProcessed.insert(saveKey)
        
        // ⚡ INSTAGRAM APPROACH: Update UI IMMEDIATELY (synchronous, main thread)
        // Since we're @MainActor, this happens synchronously before any async work
        // User sees instant feedback with zero perceived lag
        posts[index] = Post(
            id: post.id,
            authorId: post.authorId,
            authorName: post.authorName,
            authorUsername: post.authorUsername,
            authorProfileImageUrl: post.authorProfileImageUrl,
            authorRole: post.authorRole,
            authorVerificationStatus: post.authorVerificationStatus,
            type: post.type,
            text: post.text,
            media: post.media,
            voiceCaption: post.voiceCaption,
            crops: post.crops,
            hashtags: post.hashtags,
            location: post.location,
            question: post.question,
            likesCount: post.likesCount,
            commentsCount: post.commentsCount,
            savesCount: post.savesCount,
            isLikedByMe: post.isLikedByMe,
            isSavedByMe: newSavedState,
            createdAt: post.createdAt,
            updatedAt: post.updatedAt
        )
        
        Task {
            defer {
                // Always remove from processing set
                postsBeingProcessed.remove(saveKey)
            }
            
            do {
                try await savePostUseCase.invoke(postId: postId, isCurrentlySaved: post.isSavedByMe)
            } catch {
                Self.log.error("onSavePost: failed for postId=\(postId), error=\(error.localizedDescription)")
                // Revert on failure
                self.posts[index] = post
            }
        }
    }

    func onFollow(authorId: String) {
        Task {
            do {
                try await followUserUseCase.invoke(userId: authorId, isCurrentlyFollowing: false)
                await MainActor.run {
                    authorIdToIsFollowing[authorId] = true
                }
            } catch {
                Self.log.error("onFollow: failed for authorId=\(authorId)")
            }
        }
    }

    func unfollowAndRemovePosts(authorId: String) {
        Task {
            do {
                try await followUserUseCase.invoke(userId: authorId, isCurrentlyFollowing: true)
                await MainActor.run {
                    posts = posts.filter { $0.authorId != authorId }
                    authorIdToIsFollowing[authorId] = false
                }
            } catch {
                Self.log.error("unfollowAndRemovePosts: failed for authorId=\(authorId)")
            }
        }
    }
}
