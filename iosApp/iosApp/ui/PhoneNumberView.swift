import SwiftUI
import Shared

// Import the component (it's in the same module, so no import needed)

struct PhoneNumberView: View {
    @StateObject private var viewModel = PhoneNumberViewModel()
    let onBackClick: () -> Void
    let onOtpSent: (String) -> Void
    
    var body: some View {
        GeometryReader { geometry in
            let screenWidth = geometry.size.width
            // Responsive scaling factors based on screen width (360 as baseline)
            let scaleFactor = min(screenWidth / 360, 1.3)
            let padding: CGFloat = 27 * scaleFactor
            let spacing: CGFloat = 18 * scaleFactor
            
            ZStack {
                Color(hex: 0xF8F9F1)
                    .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Back Button
                    HStack {
                        Button(action: onBackClick) {
                            ZStack {
                                Circle()
                                    .fill(Color.white)
                                    .frame(width: 54 * scaleFactor, height: 54 * scaleFactor)
                                
                                Image(systemName: "chevron.left")
                                    .foregroundColor(Color(hex: 0x1B1B1B))
                                    .font(.system(size: 24 * scaleFactor))
                            }
                        }
                        Spacer()
                    }
                    .padding(.top, padding)
                    .padding(.horizontal, padding)
                    
                    Spacer()
                        .frame(height: padding)
                    
                    // Header
                    HStack {
                        Text("Enter your phone number")
                            .font(.system(size: 31.5 * scaleFactor, weight: .bold))
                            .foregroundColor(Color(hex: 0x1B1B1B))
                            .lineSpacing(0)
                            .lineLimit(2)
                            .minimumScaleFactor(0.6)
                        
                        Spacer()
                        
                        // Tap to speak button (header)
                        Button(action: {
                            Task {
                                if viewModel.isListening {
                                    await viewModel.stopSpeechRecognition()
                                } else {
                                    await viewModel.startSpeechRecognition()
                                }
                            }
                        }) {
                            Circle()
                                .fill(viewModel.isListening ? Color(hex: 0xFFB703) : Color(hex: 0xFFB703).opacity(0.2))
                                .frame(width: 36 * scaleFactor, height: 36 * scaleFactor)
                                .overlay(
                                    Image(systemName: "mic.fill")
                                        .foregroundColor(viewModel.isListening ? .white : Color(hex: 0xFFB703))
                                        .font(.system(size: 16 * scaleFactor))
                                )
                        }
                    }
                    .padding(.horizontal, padding)
                    
                    Spacer()
                        .frame(height: 9 * scaleFactor)
                    
                    Text("We'll send you a verification code")
                        .font(.system(size: 17.1 * scaleFactor))
                        .foregroundColor(Color(hex: 0x6B6B6B))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, padding)
                        .lineLimit(2)
                        .minimumScaleFactor(0.7)
                    
                    Spacer()
                        .frame(height: spacing)
                
                    // Hold to speak button
                    HoldToSpeakButton(
                        isListening: viewModel.isListening,
                        isLoading: viewModel.isLoading,
                        isProcessing: viewModel.isProcessing,
                        onStartListening: {
                            Task {
                                await viewModel.startSpeechRecognition()
                            }
                        },
                        onStopListening: {
                            Task {
                                await viewModel.stopSpeechRecognition()
                            }
                        }
                    )
                    .padding(.horizontal, padding)
                    
                    Spacer()
                        .frame(height: spacing)
                    
                    // Phone Input
                    HStack(spacing: 9 * scaleFactor) {
                        Text(viewModel.countryCode)
                            .font(.system(size: 20.25 * scaleFactor, weight: .semibold))
                            .foregroundColor(Color(hex: 0x6B6B6B))
                            .minimumScaleFactor(0.7)
                        
                        TextField("Enter phone number", text: $viewModel.phoneNumber)
                            .keyboardType(.phonePad)
                            .font(.system(size: 22.5 * scaleFactor, weight: .semibold))
                            .foregroundColor(Color(hex: 0x1B1B1B))
                    }
                    .padding(18 * scaleFactor)
                    .background(Color.white)
                    .cornerRadius(18 * scaleFactor)
                    .shadow(color: Color.black.opacity(0.05), radius: 2 * scaleFactor, x: 0, y: 2 * scaleFactor)
                    .padding(.horizontal, padding)
                    
                    // Error message
                    if let error = viewModel.error {
                        Text(error)
                            .font(.system(size: 14 * scaleFactor))
                            .foregroundColor(.red)
                            .padding(.horizontal, padding)
                            .padding(.top, 8 * scaleFactor)
                            .lineLimit(2)
                            .minimumScaleFactor(0.7)
                    }
                    
                    Spacer()
                    
                    // Get OTP Button
                    Button(action: {
                        Task {
                            await viewModel.sendOtp(
                                onSuccess: onOtpSent,
                                onError: { _ in }
                            )
                        }
                    }) {
                        if viewModel.isLoading {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                .frame(maxWidth: .infinity)
                                .frame(height: 75 * scaleFactor)
                        } else {
                            Text("Get OTP")
                                .font(.system(size: 20.25 * scaleFactor, weight: .semibold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 75 * scaleFactor)
                                .lineLimit(1)
                                .minimumScaleFactor(0.7)
                        }
                    }
                    .background(Color(hex: 0x2D6A4F))
                    .cornerRadius(18 * scaleFactor)
                    .disabled(viewModel.isLoading || viewModel.phoneNumber.count < 10)
                    .opacity((viewModel.isLoading || viewModel.phoneNumber.count < 10) ? 0.6 : 1.0)
                    .padding(.horizontal, padding)
                    .padding(.bottom, padding)
                }
            }
        }
    }
}
