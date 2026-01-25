import Foundation

enum Screen: Equatable {
    case languageSelection
    case phoneNumber(languageCode: String)
    case otp(phoneNumber: String)
    case name
}
