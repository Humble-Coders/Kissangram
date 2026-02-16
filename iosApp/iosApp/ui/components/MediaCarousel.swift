import SwiftUI
import Shared

/**
 * Horizontal carousel for displaying multiple media items
 * Shows dot indicators when there are multiple items
 * Uses TabView for horizontal paging (one item at a time)
 */
struct MediaCarousel: View {
    let media: [PostMedia]
    let onMediaClick: () -> Void
    let isVisible: Bool
    let showFullImage: Bool
    
    @State private var currentPage: Int = 0
    
    init(
        media: [PostMedia],
        onMediaClick: @escaping () -> Void = {},
        isVisible: Bool = true,
        showFullImage: Bool = false
    ) {
        self.media = media
        self.onMediaClick = onMediaClick
        self.isVisible = isVisible
        self.showFullImage = showFullImage
    }
    
    var body: some View {
        if media.isEmpty {
            EmptyView()
        } else if media.count == 1 {
            // Single media item - no carousel needed, show full size
            MediaItemView(
                media: media[0],
                isVisible: isVisible,
                onTap: onMediaClick,
                showFullImage: showFullImage
            )
        } else {
            // Multiple media items - show carousel with TabView
            GeometryReader { geometry in
                let carouselHeight = showFullImage ? geometry.size.width * 2 : 440
                
                ZStack(alignment: .bottom) {
                    TabView(selection: $currentPage) {
                        ForEach(Array(media.enumerated()), id: \.element.url) { index, item in
                            MediaItemView(
                                media: item,
                                isVisible: isVisible && (index == currentPage),
                                onTap: onMediaClick,
                                showFullImage: showFullImage
                            )
                            .tag(index)
                            .frame(width: geometry.size.width)
                        }
                    }
                    .tabViewStyle(.page(indexDisplayMode: .never))
                    .frame(width: geometry.size.width, height: carouselHeight)
                    
                    // Dot indicators overlaid on bottom of media
                    if media.count > 1 {
                        HStack(spacing: 6) {
                            ForEach(0..<media.count, id: \.self) { index in
                                Circle()
                                    .fill(index == currentPage ? Color.white : Color.white.opacity(0.5))
                                    .frame(width: index == currentPage ? 6 : 5, height: index == currentPage ? 6 : 5)
                            }
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                    }
                }
            }
            .frame(height: showFullImage ? UIScreen.main.bounds.width * 2 : 440) // Use screen width for calculation when showFullImage is true
        }
    }
}
