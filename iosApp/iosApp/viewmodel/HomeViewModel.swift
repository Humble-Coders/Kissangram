import Foundation
import os.log
import Shared

@MainActor
class HomeViewModel: ObservableObject {

    private static let log = Logger(subsystem: "com.kissangram", category: "HomeViewModel")
    private let prefs = IOSPreferencesRepository()
    private let authRepository: AuthRepository
    private let feedRepository: FeedRepository
    private let postRepository: PostRepository
    private let storyRepository: StoryRepository

    private let getHomeFeedUseCase: GetHomeFeedUseCase
    private let getStoryBarUseCase: GetStoryBarUseCase
    private let likePostUseCase: LikePostUseCase
    private let savePostUseCase: SavePostUseCase

    @Published var stories: [UserStories] = []
    @Published var posts: [Post] = []
    @Published var isLoading = false
    @Published var isRefreshing = false
    @Published var isLoadingMore = false
    @Published var hasMorePosts = true
    @Published var error: String?

    private var currentPage: Int32 = 0

    init() {
        self.authRepository = IOSAuthRepository(preferencesRepository: prefs)
        self.feedRepository = FirestoreFeedRepository(authRepository: authRepository)
        self.postRepository = MockPostRepository()
        self.storyRepository = FirestoreStoryRepository()

        self.getHomeFeedUseCase = GetHomeFeedUseCase(feedRepository: feedRepository)
        self.getStoryBarUseCase = GetStoryBarUseCase(storyRepository: storyRepository)
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
            async let postsResult = feedRepository.getHomeFeed(page: 0, pageSize: 20)
            
            let (loadedStories, loadedPosts) = try await (storiesResult, postsResult)
            
            Self.log.debug("loadContent: stories=\(loadedStories.count) posts=\(loadedPosts.count) firstPostId=\(loadedPosts.first?.id ?? "nil")")
            self.stories = loadedStories
            self.posts = loadedPosts
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
            async let postsResult = feedRepository.getHomeFeed(page: 0, pageSize: 20)
            
            let (loadedStories, loadedPosts) = try await (storiesResult, postsResult)
            Self.log.debug("refreshFeed: stories=\(loadedStories.count) posts=\(loadedPosts.count)")
            self.stories = loadedStories
            self.posts = loadedPosts
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
            let newPosts = try await feedRepository.getHomeFeed(page: page, pageSize: 20)
            Self.log.debug("loadMorePosts: newPosts=\(newPosts.count)")
            if !newPosts.isEmpty {
                self.posts.append(contentsOf: newPosts)
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
    
    func onLikePost(_ postId: String) {
        guard let index = posts.firstIndex(where: { $0.id == postId }) else { return }
        
        let post = posts[index]
        let newLikedState = !post.isLikedByMe
        let newLikesCount = newLikedState ? post.likesCount + 1 : post.likesCount - 1
        
        // Optimistic update
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
            do {
                try await likePostUseCase.invoke(postId: postId, isCurrentlyLiked: post.isLikedByMe)
            } catch {
                // Revert on failure
                self.posts[index] = post
            }
        }
    }
    
    func onSavePost(_ postId: String) {
        guard let index = posts.firstIndex(where: { $0.id == postId }) else { return }
        
        let post = posts[index]
        let newSavedState = !post.isSavedByMe
        
        // Optimistic update
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
            do {
                try await savePostUseCase.invoke(postId: postId, isCurrentlySaved: post.isSavedByMe)
            } catch {
                // Revert on failure
                self.posts[index] = post
            }
        }
    }
}
