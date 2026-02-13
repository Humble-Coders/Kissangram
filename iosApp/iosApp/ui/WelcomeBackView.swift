import SwiftUI
import Shared

struct WelcomeBackView: View {
    let userName: String
    let onContinue: () -> Void
    
    private let preferencesRepository = IOSPreferencesRepository()
    
    var body: some View {
        GeometryReader { geometry in
            let screenWidth = geometry.size.width
            // Responsive scaling factors based on screen width (360 as baseline)
            let scaleFactor = min(screenWidth / 360, 1.3)
            let padding: CGFloat = 27 * scaleFactor
            
            ZStack {
                Color(hex: 0xF8F9F1)
                    .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    Spacer()
                    
                    // Welcome message
                    Text("Welcome back,")
                        .font(.system(size: 33.75 * scaleFactor, weight: .bold))
                        .foregroundColor(Color(hex: 0x1B1B1B))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, padding)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                    
                    Spacer()
                        .frame(height: 12 * scaleFactor)
                    
                    // User name
                    Text(userName)
                        .font(.system(size: 33.75 * scaleFactor, weight: .bold))
                        .foregroundColor(Color(hex: 0x2D6A4F))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, padding)
                        .lineLimit(2)
                        .minimumScaleFactor(0.6)
                    
                    Spacer()
                        .frame(height: 24 * scaleFactor)
                    
                    // Loading indicator
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: Color(hex: 0x2D6A4F)))
                        .scaleEffect(1.5)
                    
                    Spacer()
                }
            }
            .onAppear {
                // Set auth completed flag for existing user
                Task {
                    try? await preferencesRepository.setAuthCompleted()
                }
                
                // Auto-navigate to home after 2 seconds
                DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                    onContinue()
                }
            }
        }
    }
}
