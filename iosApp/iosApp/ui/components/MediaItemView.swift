import SwiftUI
import Shared

/**
 * Component that displays either an image or video based on media type.
 * Uses Kingfisher for image caching—no reload when scrolling back.
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
                        CachedImageView(
                            url: url,
                            placeholderUrl: placeholderUrl,
                            priority: isVisible ? .high : .normal
                        )
                            .frame(maxWidth: .infinity)
                            .aspectRatio(contentMode: .fit)
                            .contentShape(Rectangle())
                            .onTapGesture { onTap() }
                    } else {
                        CachedImageView(
                            url: url,
                            placeholderUrl: placeholderUrl,
                            priority: isVisible ? .high : .normal
                        )
                            .frame(maxWidth: .infinity)
                            .frame(height: 440) // Fixed height for consistent feed display
                            .clipped()
                            .contentShape(Rectangle())
                            .onTapGesture { onTap() }
                    }
                } else {
                    Color.gray.opacity(0.3)
                        .frame(maxWidth: .infinity)
                        .frame(height: showFullImage ? nil : 440) // Fixed height only for feed
                }
            } else {
                if showFullImage {
                    FeedVideoPlayer(
                        media: media,
                        onTap: onTap,
                        showFullImage: true,
                        upcomingVideoUrls: [] // Can be extended to pass upcoming videos from parent
                    )
                    .frame(maxWidth: .infinity)
                    .aspectRatio(contentMode: .fit)
                    .compositingGroup()
                } else {
                    FeedVideoPlayer(
                        media: media,
                        onTap: onTap,
                        showFullImage: false,
                        upcomingVideoUrls: [] // Can be extended to pass upcoming videos from parent
                    )
                    .frame(maxWidth: .infinity)
                    .frame(height: 440) // Fixed height matching images
                    .clipped()
                    .compositingGroup()
                }
            }
        }
    }
}
