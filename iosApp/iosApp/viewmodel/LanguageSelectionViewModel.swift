import Foundation
import SwiftUI
import Shared

@MainActor
class LanguageSelectionViewModel: ObservableObject {
    @Published var selectedLanguage: Language
    @Published var searchQuery: String = ""
    @Published var filteredLanguages: [Language]
    
    private let preferencesRepository: PreferencesRepository
    private let getSelectedLanguageUseCase: GetSelectedLanguageUseCase
    private let setSelectedLanguageUseCase: SetSelectedLanguageUseCase
    private let allLanguages: [Language]
    
    init() {
        self.preferencesRepository = IOSPreferencesRepository()
        self.getSelectedLanguageUseCase = GetSelectedLanguageUseCase(preferencesRepository: preferencesRepository)
        self.setSelectedLanguageUseCase = SetSelectedLanguageUseCase(preferencesRepository: preferencesRepository)
        self.allLanguages = Language.Companion.shared.SUPPORTED_LANGUAGES
        
        // Default to first language (Hindi)
        self.selectedLanguage = allLanguages[0]
        self.filteredLanguages = allLanguages
    }
    
    func loadSavedLanguage() async {
        do {
            let language = try await getSelectedLanguageUseCase.invoke()
            selectedLanguage = language
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
            try await setSelectedLanguageUseCase.invoke(languageCode: code)
        } catch {
            // Continue even if save fails
        }
        onLanguageSelected(code)
    }
}
