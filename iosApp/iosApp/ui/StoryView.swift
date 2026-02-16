import SwiftUI
import AVKit
import Shared

struct StoryView: View {
    let userId: String
    let onBackClick: () -> Void
    
    @StateObject private var viewModel: StoryViewModel
    @State private var progress: Double = 0.0
    @State private var progressTimer: Timer? = nil
    
    init(userId: String, onBackClick: @escaping () -> Void) {
        self.userId = userId
        self.onBackClick = onBackClick
        _viewModel = StateObject(wrappedValue: StoryViewModel(initialUserId: userId))
    }
    
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            
            if viewModel.isLoading {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
            } else if let error = viewModel.error {
                VStack(spacing: 16) {
                    Text(error)
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                    Button("Go Back") {
                        onBackClick()
                    }
                    .foregroundColor(.white)
                }
                .padding()
            } else if let currentUserStories = viewModel.getCurrentUserStories(),
                      let currentStory = viewModel.getCurrentStory() {
                // Story Content
                StoryContentView(story: currentStory)
                
                // Swipe Gestures (placed before top bar so top bar is on top)
                // Use VStack to exclude top bar area from tap gestures
                VStack(spacing: 0) {
                    // Top spacer to exclude top bar area (approximately 100 points)
                    Color.clear
                        .frame(height: 100)
                        .allowsHitTesting(false)
                    
                    // Tap gesture areas for the rest of the screen
                    GeometryReader { geometry in
                        HStack(spacing: 0) {
                            // Left tap area (previous)
                            Color.clear
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    viewModel.previousStory()
                                }
                                .frame(width: geometry.size.width / 3)
                            
                            // Center (pause/resume)
                            Color.clear
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    viewModel.pauseAutoAdvance()
                                    // Resume after a short delay
                                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                                        viewModel.resumeAutoAdvance()
                                    }
                                }
                                .frame(width: geometry.size.width / 3)
                            
                            // Right tap area (next)
                            Color.clear
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    let hasMore = viewModel.nextStory()
                                    if !hasMore {
                                        // All stories finished, will be handled by onChange
                                    }
                                }
                                .frame(width: geometry.size.width / 3)
                        }
                    }
                }
                
                // Top Bar - positioned with safe area padding (placed last so it's on top and clickable)
                VStack {
                    StoryTopBar(
                        userStories: currentUserStories,
                        currentStoryIndex: viewModel.currentStoryIndex,
                        onBackClick: onBackClick
                    )
                    Spacer()
                }
                .padding(.top) // Respects safe area (notch)
            }
        }
        .onAppear {
            // Start progress tracking
            startProgressTimer()
        }
        .onDisappear {
            progressTimer?.invalidate()
            viewModel.pauseAutoAdvance()
        }
        .onChange(of: viewModel.currentStoryIndex) { _ in
            // Restart timer when story changes
            startProgressTimer()
        }
        .onChange(of: viewModel.currentUserIndex) { _ in
            // Restart timer when user changes
            startProgressTimer()
        }
        .onChange(of: viewModel.allStoriesFinished) { finished in
            if finished {
                onBackClick()
            }
        }
    }
    
    private func startProgressTimer() {
        progressTimer?.invalidate()
        progress = 0.0
        
        progressTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { _ in
            progress += 0.02 // 5 seconds total (100 * 0.02 * 0.05s = 5s)
            if progress >= 1.0 {
                progressTimer?.invalidate()
            }
        }
    }
}

struct StoryContentView: View {
    let story: Story
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                if story.media.type == .image {
                    // Use a layout-neutral container so AsyncImage never drives parent layout
                    // (AsyncImage can report intrinsic size from image dimensions and cause "zoom").
                    Color.clear
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .overlay {
                            AsyncImage(url: URL(string: story.media.url)) { phase in
                                switch phase {
                                case .success(let image):
                                    image
                                        .resizable()
                                        .aspectRatio(contentMode: .fill)
                                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                                case .failure(_), .empty:
                                    Color.black
                                @unknown default:
                                    Color.black
                                }
                            }
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .clipped()
                        }
                } else {
                    // Video
                    StoryVideoPlayer(url: story.media.url)
                }
                
                // Text Overlay - use GeometryReader size instead of UIScreen.main.bounds for better performance
                if let overlay = story.textOverlay {
                    Text(overlay.text)
                        .foregroundColor(.white)
                        .font(.system(size: 24, weight: .bold))
                        .position(
                            x: CGFloat(overlay.positionX) * geometry.size.width,
                            y: CGFloat(overlay.positionY) * geometry.size.height
                        )
                }
                
                // Location Badge
                if let location = story.locationName {
                    VStack {
                        Spacer()
                        HStack {
                            Text(location)
                                .foregroundColor(.white)
                                .font(.system(size: 14))
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(Color.black.opacity(0.6))
                                .cornerRadius(8)
                            Spacer()
                        }
                        .padding(16)
                    }
                }
            }
        }
    }
}

struct StoryVideoPlayer: View {
    let url: String
    @State private var player: AVPlayer?
    
    var body: some View {
        Group {
            if let player = player {
                VideoPlayer(player: player)
                    .onAppear {
                        player.play()
                    }
                    .onDisappear {
                        player.pause()
                    }
            } else {
                Color.black
            }
        }
        .onAppear {
            if let videoURL = URL(string: url) {
                player = AVPlayer(url: videoURL)
                player?.isMuted = false
            }
        }
        .onDisappear {
            player?.pause()
            player = nil
        }
    }
}

struct StoryTopBar: View {
    let userStories: UserStories
    let currentStoryIndex: Int
    let onBackClick: () -> Void
    
    var body: some View {
        VStack(spacing: 12) {
            // Progress Bars
            HStack(spacing: 4) {
                ForEach(0..<userStories.stories.count, id: \.self) { index in
                    StoryProgressBar(
                        isActive: index == currentStoryIndex,
                        isCompleted: index < currentStoryIndex
                    )
                }
            }
            .padding(.horizontal, 4)
            .padding(.top, 8)
            
            // Author Header
            HStack {
                // Profile Image
                AsyncImage(url: userStories.userProfileImageUrl.flatMap { URL(string: $0) }) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                    case .failure(_), .empty:
                        Circle()
                            .fill(Color.gray.opacity(0.3))
                            .overlay(
                                Text(String(userStories.userName.prefix(1)))
                                    .foregroundColor(.white)
                            )
                    @unknown default:
                        Circle()
                            .fill(Color.gray.opacity(0.3))
                    }
                }
                .frame(width: 40, height: 40)
                .clipShape(Circle())
                
                // Author Name
                VStack(alignment: .leading, spacing: 2) {
                    Text(userStories.userName)
                        .foregroundColor(.white)
                        .font(.system(size: 16, weight: .bold))
                    Text("\(currentStoryIndex + 1) / \(userStories.stories.count)")
                        .foregroundColor(.white.opacity(0.7))
                        .font(.system(size: 12))
                }
                
                Spacer()
                
                // Close Button - ensure it's clickable
                Button(action: onBackClick) {
                    Image(systemName: "xmark")
                        .foregroundColor(.white)
                        .font(.system(size: 18, weight: .semibold))
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(PlainButtonStyle())
            }
            .padding(.horizontal, 16)
        }
    }
}

struct StoryProgressBar: View {
    let isActive: Bool
    let isCompleted: Bool
    @State private var progress: Double = 0.0
    @State private var timer: Timer? = nil
    
    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                // Background
                Rectangle()
                    .fill(Color.white.opacity(0.3))
                    .frame(height: 4)
                    .cornerRadius(2)
                
                // Progress
                if isActive || isCompleted {
                    Rectangle()
                        .fill(Color.white)
                        .frame(width: geometry.size.width * (isCompleted ? 1.0 : progress), height: 4)
                        .cornerRadius(2)
                }
            }
        }
        .frame(height: 4)
        .onAppear {
            if isActive {
                startProgress()
            } else if isCompleted {
                progress = 1.0
            } else {
                progress = 0.0
            }
        }
        .onChange(of: isActive) { newValue in
            if newValue {
                startProgress()
            } else {
                timer?.invalidate()
            }
        }
    }
    
    private func startProgress() {
        timer?.invalidate()
        progress = 0.0
        
        timer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { _ in
            progress += 0.02 // 5 seconds total
            if progress >= 1.0 {
                timer?.invalidate()
            }
        }
    }
}

