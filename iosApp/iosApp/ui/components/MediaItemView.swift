import SwiftUI
import Shared

/**
 * Component that displays either an image or video based on media type
 */
struct MediaItemView: View {
    let media: PostMedia
    let isVisible: Bool
    let onTap: () -> Void
    
    private var imageUrl: String {
        // Transform Cloudinary URL for feed display
        // Note: CloudinaryUrlTransformer is Kotlin, so we do the transformation here
        var url = media.url
        
        // Convert HTTP to HTTPS (required for Android, good practice for iOS)
        if url.hasPrefix("http://") {
            url = url.replacingOccurrences(of: "http://", with: "https://")
        }
        
        if url.contains("cloudinary.com") || url.contains("res.cloudinary.com") {
            let parts = url.split(separator: "?", maxSplits: 1)
            let baseUrl = parts.first.map(String.init) ?? url
            let transformed = "\(baseUrl)?w_800,h_800,c_limit,q_auto,f_auto"
            return transformed
        }
        return url
    }
    
    var body: some View {
        Group {
            if media.type == .image {
                if let url = URL(string: imageUrl) {
                    AsyncImage(url: url) { phase in
                        switch phase {
                        case .empty:
                            Color.gray.opacity(0.3)
                                .frame(maxWidth: .infinity)
                                .frame(height: 220)
                        case .success(let image):
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .frame(maxWidth: .infinity)
                                .frame(height: 220)
                                .clipped()
                                .cornerRadius(14)
                        case .failure(let error):
                            VStack {
                                Text("Failed to load")
                                    .foregroundColor(.red)
                                    .font(.caption)
                                Text(url.absoluteString)
                                    .font(.caption2)
                                    .foregroundColor(.gray)
                                    .lineLimit(2)
                            }
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .background(Color.gray.opacity(0.3))
                            .frame(height: 220)
                        @unknown default:
                            Color.gray.opacity(0.3)
                                .frame(maxWidth: .infinity)
                                .frame(height: 220)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 220)
                    .clipped()
                    .cornerRadius(14)
                    .onTapGesture {
                        onTap()
                    }
                } else {
                    Text("Invalid URL")
                        .foregroundColor(.red)
                        .frame(maxWidth: .infinity)
                        .frame(height: 220)
                }
            } else {
                FeedVideoPlayer(
                    media: media,
                    isVisible: isVisible,
                    onTap: onTap
                )
                .frame(maxWidth: .infinity)
                .frame(height: 220)
                .clipped()
                .cornerRadius(14)
            }
        }
    }
}
