import SwiftUI
import Shared

// Import the component (it's in the same module, so no import needed)

struct PhoneNumberView: View {
    @StateObject private var viewModel = PhoneNumberViewModel()
    let onBackClick: () -> Void
    let onOtpSent: (String) -> Void
    
    var body: some View {
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
                                .frame(width: 54, height: 54)
                            
                            Image(systemName: "chevron.left")
                                .foregroundColor(Color(hex: 0x1B1B1B))
                        }
                    }
                    Spacer()
                }
                .padding(.top, 27)
                .padding(.horizontal, 27)
                
                Spacer()
                    .frame(height: 27)
                
                // Header
                HStack {
                    Text("Enter your phone number")
                        .font(.system(size: 31.5, weight: .bold))
                        .foregroundColor(Color(hex: 0x1B1B1B))
                        .lineSpacing(0)
                    
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
                            .frame(width: 36, height: 36)
                            .overlay(
                                Image(systemName: "mic.fill")
                                    .foregroundColor(viewModel.isListening ? .white : Color(hex: 0xFFB703))
                                    .font(.system(size: 16))
                            )
                    }
                }
                .padding(.horizontal, 27)
                
                Spacer()
                    .frame(height: 9)
                
                Text("We'll send you a verification code")
                    .font(.system(size: 17.1))
                    .foregroundColor(Color(hex: 0x6B6B6B))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 27)
                
                Spacer()
                    .frame(height: 18)
                
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
                .padding(.horizontal, 27)
                
                Spacer()
                    .frame(height: 18)
                
                // Phone Input
                HStack(spacing: 9) {
                    Text(viewModel.countryCode)
                        .font(.system(size: 20.25, weight: .semibold))
                        .foregroundColor(Color(hex: 0x6B6B6B))
                    
                    TextField("Enter phone number", text: $viewModel.phoneNumber)
                        .keyboardType(.phonePad)
                        .font(.system(size: 22.5, weight: .semibold))
                        .foregroundColor(Color(hex: 0x1B1B1B))
                }
                .padding(18)
                .background(Color.white)
                .cornerRadius(18)
                .shadow(color: Color.black.opacity(0.05), radius: 2, x: 0, y: 2)
                .padding(.horizontal, 27)
                
                // Error message
                if let error = viewModel.error {
                    Text(error)
                        .font(.system(size: 14))
                        .foregroundColor(.red)
                        .padding(.horizontal, 27)
                        .padding(.top, 8)
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
                            .frame(height: 75)
                    } else {
                        Text("Get OTP")
                            .font(.system(size: 20.25, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 75)
                    }
                }
                .background(Color(hex: 0x2D6A4F))
                .cornerRadius(18)
                .disabled(viewModel.isLoading || viewModel.phoneNumber.count < 10)
                .opacity((viewModel.isLoading || viewModel.phoneNumber.count < 10) ? 0.6 : 1.0)
                .padding(.horizontal, 27)
                .padding(.bottom, 27)
            }
        }
    }
}
