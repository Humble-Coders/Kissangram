import SwiftUI
import Shared

/**
 * Horizontal carousel for displaying multiple media items
 * Shows dot indicators when there are multiple items
 */
struct MediaCarousel: View {
    let media: [PostMedia]
    let onMediaClick: () -> Void
    let isVisible: Bool
    
    @State private var currentPage = 0
    
    var body: some View {
        if media.isEmpty {
            EmptyView()
        } else if media.count == 1 {
            // Single media item - no carousel needed
            MediaItemView(
                media: media[0],
                isVisible: isVisible,
                onTap: onMediaClick
            )
        } else {
            // Multiple media items - show carousel with TabView
            ZStack(alignment: .bottom) {
                TabView(selection: $currentPage) {
                    ForEach(Array(media.enumerated()), id: \.offset) { index, item in
                        MediaItemView(
                            media: item,
                            isVisible: isVisible && (index == currentPage),
                            onTap: onMediaClick
                        )
                        .tag(index)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .frame(height: 220)
                
                // Dot indicators overlaid on bottom of media - transparent background
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
}
