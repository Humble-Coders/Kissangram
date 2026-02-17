import SwiftUI
import AVFoundation
import Shared

/**
 * Video player component for feed videos.
 * No autoplay—user taps the play button to start (YouTube-style).
 * Uses AVPlayerLayer for lightweight teardown.
 */
struct FeedVideoPlayer: View {
    let media: PostMedia
    let onTap: () -> Void
    let player: AVPlayer
    var showFullImage: Bool = false
    var upcomingVideoUrls: [URL] = [] // URLs of next videos to preload
    
    @State private var loopObserver: NSObjectProtocol?
    @State private var isPlaying = false
    @State private var isMuted = true
    @State private var showThumbnail = true
    
    init(media: PostMedia, onTap: @escaping () -> Void, showFullImage: Bool = false, upcomingVideoUrls: [URL] = []) {
        self.media = media
        self.onTap = onTap
        self.showFullImage = showFullImage
        self.upcomingVideoUrls = upcomingVideoUrls
        let url = URL(string: ensureHttps(media.url)) ?? URL(fileURLWithPath: "")
        self.player = VideoPlayerCache.shared.player(for: url)
    }
    
    var body: some View {
        ZStack {
            // AVPlayerLayer—lightweight, cheap teardown when cell scrolls out
            AVPlayerLayerView(
                player: player,
                videoGravity: showFullImage ? .resizeAspect : .resizeAspectFill
            )
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
            
            // YouTube-style play/pause button — always visible, user taps to play
            Button(action: { togglePlayPause() }) {
                ZStack {
                    Circle()
                        .fill(Color.black.opacity(0.6))
                        .frame(width: 56, height: 56)
                    Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                        .font(.system(size: 24))
                        .foregroundColor(.white)
                }
            }
            
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
        .frame(maxWidth: .infinity)
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
        }
        .onDisappear {
            if let obs = loopObserver {
                NotificationCenter.default.removeObserver(obs)
                loopObserver = nil
            }
        }
    }
    
    private func startPlaying() {
        player.play()
        isPlaying = true
        showThumbnail = false
        
        // Preload next videos when current video starts playing
        if !upcomingVideoUrls.isEmpty {
            VideoPlayerCache.shared.preloadVideos(urls: upcomingVideoUrls)
        }
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
