import SwiftUI
import AVKit
import AVFoundation
import Shared

/**
 * Video player component for feed videos
 * Auto-plays when visible, muted by default
 */
struct FeedVideoPlayer: View {
    let media: PostMedia
    let isVisible: Bool
    let onTap: () -> Void
    
    @State private var player: AVPlayer?
    @State private var isPlaying = false
    @State private var isMuted = true
    @State private var showThumbnail = true
    
    var body: some View {
        ZStack {
            if let player = player {
                VideoPlayer(player: player)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .clipped()
                    .onTapGesture {
                        onTap()
                    }
            }
            
            // Thumbnail overlay (shown when paused or loading)
            if showThumbnail, let thumbnailUrl = media.thumbnailUrl, let url = URL(string: thumbnailUrl) {
                AsyncImage(url: url) { image in
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                } placeholder: {
                    Color.gray.opacity(0.3)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .clipped()
            }
            
            // Play/pause overlay button
            if !isPlaying {
                Button(action: {
                    togglePlayPause()
                }) {
                    ZStack {
                        Circle()
                            .fill(Color.black.opacity(0.6))
                            .frame(width: 56, height: 56)
                        Image(systemName: "play.fill")
                            .font(.system(size: 24))
                            .foregroundColor(.white)
                    }
                }
            }
            
            // Volume control button (top-right)
            VStack {
                HStack {
                    Spacer()
                    Button(action: {
                        isMuted.toggle()
                        player?.isMuted = isMuted
                    }) {
                        ZStack {
                            Circle()
                                .fill(Color.black.opacity(0.5))
                                .frame(width: 40, height: 40)
                            Image(systemName: isMuted ? "speaker.slash.fill" : "speaker.fill")
                                .font(.system(size: 18))
                                .foregroundColor(.white)
                        }
                    }
                    .padding(13)
                }
                Spacer()
            }
        }
        .onAppear {
            setupPlayer()
            if isVisible {
                startPlaying()
            }
        }
        .onDisappear {
            pausePlayer()
        }
        .onChange(of: isVisible) { newValue in
            if newValue {
                startPlaying()
            } else {
                pausePlayer()
            }
        }
    }
    
    private func setupPlayer() {
        guard let url = URL(string: media.url) else { return }
        let newPlayer = AVPlayer(url: url)
        newPlayer.isMuted = isMuted
        newPlayer.actionAtItemEnd = .none
        
        // Loop video
        NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: newPlayer.currentItem,
            queue: .main
        ) { _ in
            newPlayer.seek(to: .zero)
            newPlayer.play()
        }
        
        self.player = newPlayer
    }
    
    private func startPlaying() {
        guard let player = player else { return }
        player.play()
        isPlaying = true
        showThumbnail = false
    }
    
    private func pausePlayer() {
        guard let player = player else { return }
        player.pause()
        isPlaying = false
    }
    
    private func togglePlayPause() {
        guard let player = player else { return }
        if isPlaying {
            pausePlayer()
        } else {
            startPlaying()
        }
    }
}
