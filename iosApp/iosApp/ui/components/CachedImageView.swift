import SwiftUI
import Kingfisher

/// Reusable cached image view using Kingfisher.
/// Provides memory + disk cache for feed images—no reload when scrolling back.
/// Use for post media, profile images, story thumbnails, etc.
/// - Parameter loadDiskFileSynchronously: If false (recommended for feed), disk loads are async—smoother scroll.
struct CachedImageView: View {
    let url: URL?
    let contentMode: SwiftUI.ContentMode
    let loadDiskFileSynchronously: Bool
    
    init(url: URL?, contentMode: SwiftUI.ContentMode = .fill, loadDiskFileSynchronously: Bool = false) {
        self.url = url
        self.contentMode = contentMode
        self.loadDiskFileSynchronously = loadDiskFileSynchronously
    }
    
    var body: some View {
        Group {
            if let url = url {
                KFImage(url)
                    .placeholder { Color.gray.opacity(0.3) }
                    .loadDiskFileSynchronously(loadDiskFileSynchronously)
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
            } else {
                Color.gray.opacity(0.3)
            }
        }
    }
}
