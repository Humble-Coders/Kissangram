import SwiftUI
import Shared

struct LanguageSelectionView: View {
    @StateObject private var viewModel = LanguageSelectionViewModel()
    let onLanguageSelected: (String) -> Void
    
    var body: some View {
        GeometryReader { geometry in
            let screenWidth = geometry.size.width
            // Responsive scaling factors based on screen width (360 as baseline)
            let scaleFactor = min(screenWidth / 360, 1.3)
            let padding: CGFloat = 27 * scaleFactor
            let spacing: CGFloat = 13 * scaleFactor
            let headerSpacing: CGFloat = 9 * scaleFactor
            
            ZStack {
                Color(hex: 0xF8F9F1)
                    .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Header
                    VStack(alignment: .leading, spacing: headerSpacing) {
                        Text("Choose your language")
                            .font(.system(size: 31.5 * scaleFactor, weight: .bold))
                            .foregroundColor(Color(hex: 0x1B1B1B))
                            .lineSpacing(0)
                            .lineLimit(1)
                            .minimumScaleFactor(0.6)
                        
                        Text("ਆਪਣੀ ਭਾਸ਼ਾ ਚੁਣੋ")
                            .font(.system(size: 18 * scaleFactor))
                            .foregroundColor(Color(hex: 0x6B6B6B))
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, padding)
                    .padding(.horizontal, padding)
                    
                    Spacer()
                        .frame(height: spacing)
                    
                    // Search Bar
                    SearchBar(
                        query: $viewModel.searchQuery,
                        scaleFactor: scaleFactor
                    ) { query in
                        viewModel.updateSearchQuery(query)
                    }
                    .padding(.horizontal, padding)
                    
                    Spacer()
                        .frame(height: spacing)
                    
                    // Language List
                    ScrollView {
                        LazyVStack(spacing: 13.5 * scaleFactor) {
                            ForEach(viewModel.filteredLanguages, id: \.code) { language in
                                LanguageItem(
                                    language: language,
                                    isSelected: language.code == viewModel.selectedLanguage.code,
                                    scaleFactor: scaleFactor,
                                    onTap: {
                                        viewModel.selectLanguage(language)
                                    }
                                )
                            }
                        }
                        .padding(.horizontal, padding)
                    }
                    
                    Spacer()
                        .frame(height: spacing)
                    
                    // Continue Button
                    Button(action: {
                        Task {
                            await viewModel.continueWithSelection(onLanguageSelected: onLanguageSelected)
                        }
                    }) {
                        Text("Continue")
                            .font(.system(size: 20.25 * scaleFactor, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 75 * scaleFactor)
                            .background(Color(hex: 0x2D6A4F))
                            .cornerRadius(18 * scaleFactor)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                    .padding(.horizontal, padding)
                    .padding(.bottom, padding)
                }
            }
        }
        .task {
            await viewModel.loadSavedLanguage()
        }
    }
}

struct SearchBar: View {
    @Binding var query: String
    let scaleFactor: CGFloat
    let onQueryChange: (String) -> Void
    
    var body: some View {
        HStack(spacing: 14 * scaleFactor) {
            Image(systemName: "magnifyingglass")
                .foregroundColor(Color(hex: 0x6B6B6B))
                .frame(width: 22.5 * scaleFactor, height: 22.5 * scaleFactor)
            
            TextField("Search language...", text: $query)
                .onChange(of: query) { newValue in
                    onQueryChange(newValue)
                }
                .font(.system(size: 18 * scaleFactor))
                .foregroundColor(Color(hex: 0x1B1B1B).opacity(0.5))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .padding(.horizontal, 18 * scaleFactor)
        .frame(height: 63 * scaleFactor)
        .background(Color.white)
        .cornerRadius(18 * scaleFactor)
        .shadow(color: Color.black.opacity(0.05), radius: 2 * scaleFactor, x: 0, y: 2 * scaleFactor)
    }
}

struct LanguageItem: View {
    let language: Language
    let isSelected: Bool
    let scaleFactor: CGFloat
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 18 * scaleFactor) {
                // Language Icon Circle
                ZStack {
                    Circle()
                        .fill(isSelected ? Color(hex: 0x2D6A4F) : Color(hex: 0xE5E6DE).opacity(0.3))
                        .frame(width: 36 * scaleFactor, height: 36 * scaleFactor)
                    
                    Image(systemName: "checkmark")
                        .foregroundColor(isSelected ? .white : Color(hex: 0x6B6B6B))
                        .font(.system(size: 22.5 * scaleFactor))
                }
                
                // Language Names
                VStack(alignment: .leading, spacing: 4.5 * scaleFactor) {
                    Text(language.nativeName)
                        .font(.system(size: 22.5 * scaleFactor, weight: .semibold))
                        .foregroundColor(Color(hex: 0x1B1B1B))
                        .lineSpacing(0)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                    
                    Text(language.englishName)
                        .font(.system(size: 15.75 * scaleFactor))
                        .foregroundColor(Color(hex: 0x6B6B6B))
                        .lineSpacing(0)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                
                // Speaker Icon
                ZStack {
                    Circle()
                        .fill(Color(hex: 0xFFB703).opacity(0.2))
                        .frame(width: 54 * scaleFactor, height: 54 * scaleFactor)
                    
                    Image(systemName: "speaker.wave.2")
                        .foregroundColor(Color(hex: 0xFFB703))
                        .font(.system(size: 27 * scaleFactor))
                }
            }
            .padding(.horizontal, 23.7 * scaleFactor)
            .padding(.vertical, 1.18 * scaleFactor)
            .frame(height: 109 * scaleFactor)
            .background(Color.white)
            .cornerRadius(18 * scaleFactor)
            .overlay(
                RoundedRectangle(cornerRadius: 18 * scaleFactor)
                    .stroke(isSelected ? Color(hex: 0x2D6A4F) : Color.clear, lineWidth: 1.18 * scaleFactor)
            )
            .shadow(
                color: isSelected ? Color(hex: 0x2D6A4F).opacity(0.15) : Color.black.opacity(0.05),
                radius: isSelected ? 4 * scaleFactor : 2 * scaleFactor,
                x: 0,
                y: isSelected ? 4 * scaleFactor : 2 * scaleFactor
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}
