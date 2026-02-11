import SwiftUI
import Shared
import Foundation

// MARK: - Create Story View Content
struct CreateStoryViewContent: View {
    let onBackClick: () -> Void
    let onStoryCreated: () -> Void
    
    var body: some View {
        CreateStoryView(
            onBackClick: onBackClick,
            onStoryCreated: onStoryCreated
        )
    }
}
