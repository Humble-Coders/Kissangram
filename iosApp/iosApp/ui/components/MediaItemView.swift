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
        if url.contains("cloudinary.com") || url.contains("res.cloudinary.com") {
            let parts = url.split(separator: "?", maxSplits: 1)
            let baseUrl = parts.first.map(String.init) ?? url
            return "\(baseUrl)?w_800,h_800,c_limit,q_auto,f_auto"
        }
        return url
    }
    
    var body: some View {
        Group {
            if media.type == .image {
                if let url = URL(string: imageUrl) {
                    if showFullImage {
                        CachedImageView(url: url)
                            .frame(maxWidth: .infinity)
                            .aspectRatio(contentMode: .fit)
                            .contentShape(Rectangle())
                            .onTapGesture { onTap() }
                    } else {
                        CachedImageView(url: url)
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
                        showFullImage: true
                    )
                    .frame(maxWidth: .infinity)
                    .aspectRatio(contentMode: .fit)
                    .compositingGroup()
                } else {
                    FeedVideoPlayer(
                        media: media,
                        onTap: onTap,
                        showFullImage: false
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
