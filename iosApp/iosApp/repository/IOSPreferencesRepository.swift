import Foundation
import Shared

final class IOSPreferencesRepository: PreferencesRepository {
    private let userDefaults = UserDefaults.standard
    
    private let keySelectedLanguage = "selected_language_code"
    private let keyVerificationId = "verification_id"
    private let keyAuthCompleted = "auth_completed"
    
    func getSelectedLanguageCode() async throws -> String? {
        return userDefaults.string(forKey: keySelectedLanguage)
    }
    
    func setSelectedLanguageCode(code: String) async throws {
        userDefaults.set(code, forKey: keySelectedLanguage)
    }
    
    func getVerificationId() async throws -> String? {
        return userDefaults.string(forKey: keyVerificationId)
    }
    
    func setVerificationId(id: String) async throws {
        userDefaults.set(id, forKey: keyVerificationId)
    }
    
    func clearVerificationId() async throws {
        userDefaults.removeObject(forKey: keyVerificationId)
    }
    
    func hasCompletedAuth() async throws -> KotlinBoolean {
        return KotlinBoolean(value: userDefaults.bool(forKey: keyAuthCompleted))
    }
    
    func setAuthCompleted() async throws {
        userDefaults.set(true, forKey: keyAuthCompleted)
    }
    
    func clearSession() async throws {
        userDefaults.removeObject(forKey: keyAuthCompleted)
        userDefaults.removeObject(forKey: keyVerificationId)
        // Keep selected_language_code so language preference persists
    }
}
