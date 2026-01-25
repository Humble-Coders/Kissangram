import SwiftUI
import Shared

struct OtpView: View {
    let phoneNumber: String
    @StateObject private var viewModel: OtpViewModel
    let onBackClick: () -> Void
    let onOtpVerified: () -> Void
    let onResendOtp: () -> Void
    
    init(
        phoneNumber: String,
        onBackClick: @escaping () -> Void,
        onOtpVerified: @escaping () -> Void,
        onResendOtp: @escaping () -> Void
    ) {
        self.phoneNumber = phoneNumber
        self._viewModel = StateObject(wrappedValue: OtpViewModel(phoneNumber: phoneNumber))
        self.onBackClick = onBackClick
        self.onOtpVerified = onOtpVerified
        self.onResendOtp = onResendOtp
    }
    
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
                    Text("Enter OTP")
                        .font(.system(size: 31.5, weight: .bold))
                        .foregroundColor(Color(hex: 0x1B1B1B))
                        .lineSpacing(0)
                    
                    Spacer()
                    
                    // Tap to speak button (header)
                    Button(action: {
                        if viewModel.isListening {
                            Task {
                                await viewModel.stopSpeechRecognition()
                            }
                        } else {
                            Task {
                                await viewModel.startSpeechRecognition()
                            }
                        }
                    }) {
                        Circle()
                            .fill(viewModel.isListening ? Color(hex: 0xFFB703) : Color(hex: 0xFFB703).opacity(0.2))
                            .frame(width: 36, height: 36)
                    }
                }
                .padding(.horizontal, 27)
                
                Spacer()
                    .frame(height: 9)
                
                Text("Code sent to \(phoneNumber)")
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
                    },
                    defaultText: "Hold to speak"
                )
                .padding(.horizontal, 27)
                
                Spacer()
                    .frame(height: 18)
                
                // OTP Input
                TextField("Enter the 6 digit code", text: $viewModel.otp)
                    .keyboardType(.numberPad)
                    .font(.system(size: 27, weight: .semibold))
                    .foregroundColor(Color(hex: 0x1B1B1B))
                    .multilineTextAlignment(.center)
                    .tracking(9)
                    .onChange(of: viewModel.otp) { newValue in
                        // Only allow digits and limit to 6
                        let filtered = newValue.filter { $0.isNumber }
                        if filtered.count <= 6 {
                            viewModel.otp = filtered
                        } else {
                            viewModel.otp = String(filtered.prefix(6))
                        }
                    }
                    .padding(18)
                    .background(Color.white)
                    .cornerRadius(18)
                    .shadow(color: Color.black.opacity(0.05), radius: 2, x: 0, y: 2)
                    .padding(.horizontal, 27)
                
                Spacer()
                    .frame(height: 18)
                
                // Resend OTP
                Button(action: {
                    viewModel.resendOtp(onResend: onResendOtp)
                }) {
                    Text("Didn't receive code? Resend")
                        .font(.system(size: 17.1, weight: .semibold))
                        .foregroundColor(Color(hex: 0x2D6A4F))
                }
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
                
                // Verify & Continue Button
                Button(action: {
                    Task {
                        await viewModel.verifyOtp(
                            onSuccess: onOtpVerified,
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
                        Text("Verify & Continue")
                            .font(.system(size: 20.25, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 75)
                    }
                }
                .background(Color(hex: 0x2D6A4F))
                .cornerRadius(18)
                .disabled(viewModel.isLoading || viewModel.otp.count != 6)
                .opacity((viewModel.isLoading || viewModel.otp.count != 6) ? 0.6 : 1.0)
                .padding(.horizontal, 27)
                .padding(.bottom, 27)
            }
        }
    }
}
