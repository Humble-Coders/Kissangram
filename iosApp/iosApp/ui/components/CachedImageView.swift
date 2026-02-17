import SwiftUI
import Kingfisher

/// Reusable cached image view using Kingfisher.
/// Provides memory + disk cache for feed images—no reload when scrolling back.
/// Supports progressive loading with thumbnail placeholder for faster perceived load times.
/// Use for post media, profile images, story thumbnails, etc.
/// - Parameter loadDiskFileSynchronously: If false (recommended for feed), disk loads are async—smoother scroll.
/// - Parameter placeholderUrl: Optional thumbnail URL to show while full image loads (progressive loading)
/// - Parameter priority: Image loading priority (high for visible items, low for prefetched)
struct CachedImageView: View {
    let url: URL?
    let contentMode: SwiftUI.ContentMode
    let loadDiskFileSynchronously: Bool
    let placeholderUrl: URL?
    let priority: ImageLoadingPriority

    enum ImageLoadingPriority {
        case high
        case normal
        case low

        /// Maps to URLSessionTask.Priority float values used by Kingfisher's .downloadPriority()
        var value: Float {
            switch self {
            case .high:   return URLSessionTask.highPriority
            case .normal: return URLSessionTask.defaultPriority
            case .low:    return URLSessionTask.lowPriority
            }
        }
    }

    init(
        url: URL?,
        contentMode: SwiftUI.ContentMode = .fill,
        loadDiskFileSynchronously: Bool = false,
        placeholderUrl: URL? = nil,
        priority: ImageLoadingPriority = .normal
    ) {
        self.url = url
        self.contentMode = contentMode
        self.loadDiskFileSynchronously = loadDiskFileSynchronously
        self.placeholderUrl = placeholderUrl
        self.priority = priority
    }

    var body: some View {
        Group {
            if let url = url {
                KFImage(url)
                    .placeholder {
                        // Show thumbnail placeholder if available for progressive loading
                        if let placeholderUrl = placeholderUrl {
                            KFImage(placeholderUrl)
                                .resizable()
                                .aspectRatio(contentMode: contentMode)
                        } else {
                            Color.gray.opacity(0.3)
                        }
                    }
                    .loadDiskFileSynchronously(loadDiskFileSynchronously)
                    .downloadPriority(priority.value)
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
            } else {
                Color.gray.opacity(0.3)
            }
        }
    }
}
