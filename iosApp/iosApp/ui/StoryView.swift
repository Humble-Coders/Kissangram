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
                
                // Top Bar
                VStack {
                    StoryTopBar(
                        userStories: currentUserStories,
                        currentStoryIndex: viewModel.currentStoryIndex,
                        onBackClick: onBackClick
                    )
                    Spacer()
                }
                
                // Bottom Actions
                VStack {
                    Spacer()
                    StoryBottomActions(story: currentStory)
                        .padding(.horizontal, 16)
                        .padding(.bottom, 16)
                }
                
                // Swipe Gestures
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
                                viewModel.nextStory()
                            }
                            .frame(width: geometry.size.width / 3)
                    }
                }
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
        ZStack {
            if story.media.type == .image {
                AsyncImage(url: URL(string: story.media.url)) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                    case .failure(_), .empty:
                        Color.black
                    @unknown default:
                        Color.black
                    }
                }
            } else {
                // Video
                StoryVideoPlayer(url: story.media.url)
            }
            
            // Text Overlay
            if let overlay = story.textOverlay {
                Text(overlay.text)
                    .foregroundColor(.white)
                    .font(.system(size: 24, weight: .bold))
                    .position(
                        x: CGFloat(overlay.positionX) * UIScreen.main.bounds.width,
                        y: CGFloat(overlay.positionY) * UIScreen.main.bounds.height
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
        .ignoresSafeArea()
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
                
                // Close Button
                Button(action: onBackClick) {
                    Image(systemName: "xmark")
                        .foregroundColor(.white)
                        .font(.system(size: 18))
                        .frame(width: 32, height: 32)
                }
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

struct StoryBottomActions: View {
    let story: Story
    
    var body: some View {
        HStack {
            // Like Button
            Button(action: { /* TODO: Implement like */ }) {
                Image(systemName: story.isLikedByMe ? "heart.fill" : "heart")
                    .foregroundColor(story.isLikedByMe ? .red : .white)
                    .font(.system(size: 24))
            }
            
            Spacer()
            
            // View Count
            Text("\(story.viewsCount) views")
                .foregroundColor(.white.opacity(0.8))
                .font(.system(size: 14))
        }
    }
}
