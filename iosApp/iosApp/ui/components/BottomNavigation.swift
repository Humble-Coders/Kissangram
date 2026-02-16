import SwiftUI

enum BottomNavItem: CaseIterable {
    case home, search, post, reels, profile
    
    var icon: String {
        switch self {
        case .home: return "house"
        case .search: return "magnifyingglass"
        case .post: return "plus.circle"
        case .reels: return "play.circle"
        case .profile: return "person"
        }
    }
    
    var selectedIcon: String {
        switch self {
        case .home: return "house.fill"
        case .search: return "magnifyingglass"
        case .post: return "plus.circle.fill"
        case .reels: return "play.circle.fill"
        case .profile: return "person.fill"
        }
    }
    
    var labelHindi: String {
        switch self {
        case .home: return "होम"
        case .search: return "खोजें"
        case .post: return "पोस्ट"
        case .reels: return "रील्स"
        case .profile: return "प्रोफ़ाइल"
        }
    }
    
    var label: String {
        switch self {
        case .home: return "Home"
        case .search: return "Search"
        case .post: return "Post"
        case .reels: return "Reels"
        case .profile: return "Profile"
        }
    }
}

struct KissangramBottomNavigation: View {
    @Binding var selectedItem: BottomNavItem
    var useHindi: Bool = true
    
    var body: some View {
        HStack {
            ForEach(BottomNavItem.allCases, id: \.self) { item in
                Spacer()
                BottomNavItemButton(
                    item: item,
                    isSelected: item == selectedItem,
                    useHindi: useHindi
                ) {
                    selectedItem = item
                }
                Spacer()
            }
        }
        .padding(.horizontal, 17)
        .frame(height: 83)
        .background(Color.appBackground)
        .overlay(
            Rectangle()
                .frame(height: 1)
                .foregroundColor(.black.opacity(0.05)),
            alignment: .top
        )
    }
}

struct BottomNavItemButton: View {
    let item: BottomNavItem
    let isSelected: Bool
    let useHindi: Bool
    let action: () -> Void
    
    private var isDisabled: Bool {
        item == .reels // Disable Reels as it's not implemented
    }
    
    var body: some View {
        Button(action: isDisabled ? {} : action) {
            VStack(spacing: 4) {
                Image(systemName: isSelected ? item.selectedIcon : item.icon)
                    .font(.system(size: 22))
                    .foregroundColor(isDisabled ? .textSecondary.opacity(0.38) : (isSelected ? .primaryGreen : .textSecondary))
                
                Text(useHindi ? item.labelHindi : item.label)
                    .font(.system(size: 12, weight: isSelected ? .semibold : .medium))
                    .foregroundColor(isDisabled ? .textSecondary.opacity(0.38) : (isSelected ? .primaryGreen : .textSecondary))
            }
            .frame(width: 68, height: 61)
        }
        .disabled(isDisabled)
    }
}

#Preview {
    KissangramBottomNavigation(selectedItem: .constant(.home))
}
