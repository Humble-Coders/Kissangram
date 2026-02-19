import SwiftUI
import Shared

/**
 * Component that displays either an image or video based on media type.
 * Uses Kingfisher for image caching—no reload when scrolling back.
 *
 * Uses GeometryReader to get the real available width so that images/videos
 * with extreme aspect ratios (panoramas, tall portraits) never push the
 * layout wider than the screen.
 */
struct MediaItemView: View {
    let media: PostMedia
    let isVisible: Bool
    let onTap: () -> Void
    var showFullImage: Bool = false // If true, show full image without fixed height constraint
    
    private var imageUrl: String {
        var url = ensureHttps(media.url)
        
        // Check if Firebase Storage URL (no transformations needed)
        if url.contains("firebasestorage.googleapis.com") || url.contains("firebase.storage") {
            return url // Firebase Storage URLs are used as-is
        }
        
        // Check if Cloudinary URL (apply transformations)
        if url.contains("cloudinary.com") || url.contains("res.cloudinary.com") {
            let parts = url.split(separator: "?", maxSplits: 1)
            let baseUrl = parts.first.map(String.init) ?? url
            return "\(baseUrl)?w_800,h_800,c_limit,q_auto,f_auto"
        }
        
        // Default: return URL as-is
        return url
    }
    
    var body: some View {
        Group {
            if media.type == .image {
                if let url = URL(string: imageUrl) {
                    let placeholderUrl = media.thumbnailUrl.flatMap { URL(string: $0) }
                    if showFullImage {
                        // Full-image mode: use .fit so the whole image is visible,
                        // constrained to the available width.
                        CachedImageView(
                            url: url,
                            contentMode: .fit,
                            placeholderUrl: placeholderUrl,
                            priority: isVisible ? .high : .normal
                        )
                            .frame(maxWidth: .infinity)
                            .contentShape(Rectangle())
                            .onTapGesture { onTap() }
                    } else {
                        // Feed mode: fixed 440pt height, clipped to the real
                        // available width so wide/tall images can't overflow.
                        GeometryReader { geometry in
                            CachedImageView(
                                url: url,
                                placeholderUrl: placeholderUrl,
                                priority: isVisible ? .high : .normal
                            )
                                .frame(width: geometry.size.width, height: 440)
                                .clipped()
                                .contentShape(Rectangle())
                                .onTapGesture { onTap() }
                        }
                        .frame(height: 440)
                    }
                } else {
                    Color.gray.opacity(0.3)
                        .frame(maxWidth: .infinity)
                        .frame(height: showFullImage ? nil : 440)
                }
            } else {
                if showFullImage {
                    // Square container — the AVPlayerLayer uses .resizeAspect
                    // so the full video is always visible (letterboxed or
                    // pillarboxed depending on aspect ratio).
                    FeedVideoPlayer(
                        media: media,
                        onTap: onTap,
                        showFullImage: true,
                        upcomingVideoUrls: []
                    )
                    .aspectRatio(1, contentMode: .fit)
                    .frame(maxWidth: .infinity)
                    .clipped()
                    .compositingGroup()
                } else {
                    // Feed mode: explicit width via GeometryReader so the video
                    // player can't push the layout wider than the screen.
                    GeometryReader { geometry in
                        FeedVideoPlayer(
                            media: media,
                            onTap: onTap,
                            showFullImage: false,
                            upcomingVideoUrls: []
                        )
                        .frame(width: geometry.size.width, height: 440)
                        .clipped()
                        .compositingGroup()
                    }
                    .frame(height: 440)
                }
            }
        }
    }
}
