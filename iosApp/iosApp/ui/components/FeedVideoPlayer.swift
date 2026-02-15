import SwiftUI
import AVKit
import AVFoundation
import Shared

/**
 * Video player component for feed videos
 * Auto-plays when visible, muted by default.
 * Player is created once in init (from cache) and reused—avoids re-construction on appear
 * and state-triggered re-renders that cause scroll lag.
 */
struct FeedVideoPlayer: View {
    let media: PostMedia
    let isVisible: Bool
    let onTap: () -> Void
    let player: AVPlayer
    
    @State private var loopObserver: NSObjectProtocol?
    @State private var isPlaying = false
    @State private var isMuted = true
    @State private var showThumbnail = true
    
    init(media: PostMedia, isVisible: Bool, onTap: @escaping () -> Void) {
        self.media = media
        self.isVisible = isVisible
        self.onTap = onTap
        let url = URL(string: ensureHttps(media.url)) ?? URL(fileURLWithPath: "")
        self.player = VideoPlayerCache.shared.player(for: url)
    }
    
    var body: some View {
        ZStack {
            // Video layer — player always exists (from init), no conditional/state-triggered layout
            VideoPlayer(player: player)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .clipped()
                .onTapGesture { onTap() }
            
            // Thumbnail overlay — use opacity instead of conditional to keep stable hierarchy
            Group {
                if let thumbnailUrl = media.thumbnailUrl, let url = URL(string: ensureHttps(thumbnailUrl)) {
                    CachedImageView(url: url)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .clipped()
                }
            }
            .opacity(showThumbnail ? 1 : 0)
            .allowsHitTesting(showThumbnail)
            .animation(nil, value: showThumbnail)
            
            // Play/pause button — use opacity instead of conditional
            Button(action: { togglePlayPause() }) {
                ZStack {
                    Circle()
                        .fill(Color.black.opacity(0.6))
                        .frame(width: 56, height: 56)
                    Image(systemName: "play.fill")
                        .font(.system(size: 24))
                        .foregroundColor(.white)
                }
            }
            .opacity(isPlaying ? 0 : 1)
            .allowsHitTesting(!isPlaying)
            .animation(nil, value: isPlaying)
            
            // Volume control button (top-right)
            VStack {
                HStack {
                    Spacer()
                    Button(action: {
                        isMuted.toggle()
                        player.isMuted = isMuted
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
        .animation(nil, value: isVisible)
        .onAppear {
            player.isMuted = isMuted
            player.actionAtItemEnd = .none
            if let item = player.currentItem {
                loopObserver = NotificationCenter.default.addObserver(
                    forName: .AVPlayerItemDidPlayToEndTime,
                    object: item,
                    queue: .main
                ) { _ in
                    player.seek(to: .zero)
                    player.play()
                }
            }
            if isVisible {
                startPlaying()
            }
        }
        .onDisappear {
            if let obs = loopObserver {
                NotificationCenter.default.removeObserver(obs)
                loopObserver = nil
            }
            pausePlayer()
        }
        .onChange(of: isVisible) { newValue in
            var t = Transaction()
            t.animation = nil
            withTransaction(t) {
                if newValue {
                    startPlaying()
                } else {
                    pausePlayer()
                }
            }
        }
    }
    
    private func startPlaying() {
        player.play()
        isPlaying = true
        showThumbnail = false
    }
    
    private func pausePlayer() {
        player.pause()
        isPlaying = false
        showThumbnail = true
    }
    
    private func togglePlayPause() {
        if isPlaying {
            pausePlayer()
        } else {
            startPlaying()
        }
    }
}
