import Foundation
import SwiftUI
import Shared

@MainActor
class LanguageSelectionViewModel: ObservableObject {
    @Published var selectedLanguage: Language
    @Published var searchQuery: String = ""
    @Published var filteredLanguages: [Language]
    
    private let preferencesRepository: PreferencesRepository
    private let allLanguages: [Language]
    
    init() {
        let factory = PreferencesRepositoryFactory()
        self.preferencesRepository = factory.create()
        self.allLanguages = Language.Companion.shared.SUPPORTED_LANGUAGES
        
        // Default to first language (Hindi)
        self.selectedLanguage = allLanguages[0]
        self.filteredLanguages = allLanguages
    }
    
    func loadSavedLanguage() async {
        do {
            let savedCode = try await preferencesRepository.getSelectedLanguageCode()
            if let code = savedCode,
               let language = allLanguages.first(where: { $0.code == code }) {
                selectedLanguage = language
            }
        } catch {
            // Keep default language if loading fails
        }
    }
    
    func selectLanguage(_ language: Language) {
        selectedLanguage = language
    }
    
    func updateSearchQuery(_ query: String) {
        searchQuery = query
        if query.isEmpty {
            filteredLanguages = allLanguages
        } else {
            filteredLanguages = allLanguages.filter { language in
                language.englishName.localizedCaseInsensitiveContains(query) ||
                language.nativeName.localizedCaseInsensitiveContains(query)
            }
        }
    }
    
    func continueWithSelection(onLanguageSelected: @escaping (String) -> Void) async {
        let code = selectedLanguage.code
        do {
            try await preferencesRepository.setSelectedLanguageCode(code: code)
        } catch {
            // Continue even if save fails
        }
        onLanguageSelected(code)
    }
}
