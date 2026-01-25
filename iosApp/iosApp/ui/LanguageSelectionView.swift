import SwiftUI
import Shared

struct LanguageSelectionView: View {
    @StateObject private var viewModel = LanguageSelectionViewModel()
    let onLanguageSelected: (String) -> Void
    
    var body: some View {
        ZStack {
            Color(hex: 0xF8F9F1)
                .ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Header
                VStack(alignment: .leading, spacing: 9) {
                    Text("Choose your language")
                        .font(.system(size: 31.5, weight: .bold))
                        .foregroundColor(Color(hex: 0x1B1B1B))
                        .lineSpacing(0)
                    
                    Text("ਆਪਣੀ ਭਾਸ਼ਾ ਚੁਣੋ")
                        .font(.system(size: 18))
                        .foregroundColor(Color(hex: 0x6B6B6B))
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, 27)
                .padding(.horizontal, 27)
                
                Spacer()
                    .frame(height: 13)
                
                // Search Bar
                SearchBar(query: $viewModel.searchQuery) { query in
                    viewModel.updateSearchQuery(query)
                }
                .padding(.horizontal, 27)
                
                Spacer()
                    .frame(height: 13)
                
                // Language List
                ScrollView {
                    LazyVStack(spacing: 13.5) {
                        ForEach(viewModel.filteredLanguages, id: \.code) { language in
                            LanguageItem(
                                language: language,
                                isSelected: language.code == viewModel.selectedLanguage.code,
                                onTap: {
                                    viewModel.selectLanguage(language)
                                }
                            )
                        }
                    }
                    .padding(.horizontal, 27)
                }
                
                Spacer()
                    .frame(height: 13)
                
                // Continue Button
                Button(action: {
                    Task {
                        await viewModel.continueWithSelection(onLanguageSelected: onLanguageSelected)
                    }
                }) {
                    Text("Continue")
                        .font(.system(size: 20.25, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 75)
                        .background(Color(hex: 0x2D6A4F))
                        .cornerRadius(18)
                }
                .padding(.horizontal, 27)
                .padding(.bottom, 27)
            }
        }
        .task {
            await viewModel.loadSavedLanguage()
        }
    }
}

struct SearchBar: View {
    @Binding var query: String
    let onQueryChange: (String) -> Void
    
    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: "magnifyingglass")
                .foregroundColor(Color(hex: 0x6B6B6B))
                .frame(width: 22.5, height: 22.5)
            
            TextField("Search language...", text: $query)
                .onChange(of: query) { newValue in
                    onQueryChange(newValue)
                }
                .font(.system(size: 18))
                .foregroundColor(Color(hex: 0x1B1B1B).opacity(0.5))
        }
        .padding(.horizontal, 18)
        .frame(height: 63)
        .background(Color.white)
        .cornerRadius(18)
        .shadow(color: Color.black.opacity(0.05), radius: 2, x: 0, y: 2)
    }
}

struct LanguageItem: View {
    let language: Language
    let isSelected: Bool
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 18) {
                // Language Icon Circle
                ZStack {
                    Circle()
                        .fill(isSelected ? Color(hex: 0x2D6A4F) : Color(hex: 0xE5E6DE).opacity(0.3))
                        .frame(width: 36, height: 36)
                    
                    Image(systemName: "checkmark")
                        .foregroundColor(isSelected ? .white : Color(hex: 0x6B6B6B))
                        .font(.system(size: 22.5))
                }
                
                // Language Names
                VStack(alignment: .leading, spacing: 4.5) {
                    Text(language.nativeName)
                        .font(.system(size: 22.5, weight: .semibold))
                        .foregroundColor(Color(hex: 0x1B1B1B))
                        .lineSpacing(0)
                    
                    Text(language.englishName)
                        .font(.system(size: 15.75))
                        .foregroundColor(Color(hex: 0x6B6B6B))
                        .lineSpacing(0)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                
                // Speaker Icon
                ZStack {
                    Circle()
                        .fill(Color(hex: 0xFFB703).opacity(0.2))
                        .frame(width: 54, height: 54)
                    
                    Image(systemName: "speaker.wave.2")
                        .foregroundColor(Color(hex: 0xFFB703))
                        .font(.system(size: 27))
                }
            }
            .padding(.horizontal, 23.7)
            .padding(.vertical, 1.18)
            .frame(height: 109)
            .background(Color.white)
            .cornerRadius(18)
            .overlay(
                RoundedRectangle(cornerRadius: 18)
                    .stroke(isSelected ? Color(hex: 0x2D6A4F) : Color.clear, lineWidth: 1.18)
            )
            .shadow(
                color: isSelected ? Color(hex: 0x2D6A4F).opacity(0.15) : Color.black.opacity(0.05),
                radius: isSelected ? 4 : 2,
                x: 0,
                y: isSelected ? 4 : 2
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}

extension Color {
    init(hex: UInt, alpha: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 08) & 0xFF) / 255,
            blue: Double((hex >> 00) & 0xFF) / 255,
            opacity: alpha
        )
    }
}
