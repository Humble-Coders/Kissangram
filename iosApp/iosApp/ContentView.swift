import SwiftUI
import Shared

struct ContentView: View {
    @State private var currentScreen: Screen = .languageSelection
    @State private var navigationStack: [Screen] = []
    
    var body: some View {
        Group {
            switch currentScreen {
            case .languageSelection:
                LanguageSelectionView { languageCode in
                    navigateTo(.phoneNumber(languageCode: languageCode))
                }
                
            case .phoneNumber(let languageCode):
                PhoneNumberView(
                    onBackClick: {
                        navigateBack()
                    },
                    onOtpSent: { phoneNumber in
                        navigateTo(.otp(phoneNumber: phoneNumber))
                    }
                )
                
            case .otp(let phoneNumber):
                OtpView(
                    phoneNumber: phoneNumber,
                    onBackClick: {
                        navigateBack()
                    },
                    onOtpVerified: {
                        navigateTo(.name)
                    },
                    onResendOtp: {
                        // Navigate back to phone number screen
                        navigateBack()
                    }
                )
                
            case .name:
                NameView(
                    onNameSaved: {
                        // TODO: Navigate to main app/home screen
                        // For now, just stay on this screen
                    }
                )
            }
        }
    }
    
    private func navigateTo(_ screen: Screen) {
        navigationStack.append(currentScreen)
        currentScreen = screen
    }
    
    private func navigateBack() {
        if let previousScreen = navigationStack.popLast() {
            currentScreen = previousScreen
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
