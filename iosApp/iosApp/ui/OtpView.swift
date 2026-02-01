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
                        Text("Enter OTP")
                            .font(.system(size: 31.5 * scaleFactor, weight: .bold))
                            .foregroundColor(Color(hex: 0x1B1B1B))
                            .lineSpacing(0)
                            .lineLimit(1)
                            .minimumScaleFactor(0.6)
                        
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
                                .frame(width: 36 * scaleFactor, height: 36 * scaleFactor)
                        }
                    }
                    .padding(.horizontal, padding)
                    
                    Spacer()
                        .frame(height: 9 * scaleFactor)
                    
                    Text("Code sent to \(phoneNumber)")
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
                        },
                        defaultText: "Tap to speak",
                        style: otpScreenButtonStyle
                    )
                    .padding(.horizontal, padding)
                    
                    Spacer()
                        .frame(height: spacing)
                    
                    // OTP Input
                    TextField("Enter the 6 digit code", text: $viewModel.otp)
                        .keyboardType(.numberPad)
                        .font(.system(size: 27 * scaleFactor, weight: .semibold))
                        .foregroundColor(Color(hex: 0x1B1B1B))
                        .multilineTextAlignment(.center)
                        .tracking(9 * scaleFactor)
                        .onChange(of: viewModel.otp) { newValue in
                            // Only allow digits and limit to 6
                            let filtered = newValue.filter { $0.isNumber }
                            if filtered.count <= 6 {
                                viewModel.otp = filtered
                            } else {
                                viewModel.otp = String(filtered.prefix(6))
                            }
                        }
                        .padding(18 * scaleFactor)
                        .background(Color.white)
                        .cornerRadius(18 * scaleFactor)
                        .shadow(color: Color.black.opacity(0.05), radius: 2 * scaleFactor, x: 0, y: 2 * scaleFactor)
                        .padding(.horizontal, padding)
                    
                    Spacer()
                        .frame(height: spacing)
                    
                    // Resend OTP
                    Button(action: {
                        viewModel.resendOtp(onResend: onResendOtp)
                    }) {
                        Text("Didn't receive code? Resend")
                            .font(.system(size: 17.1 * scaleFactor, weight: .semibold))
                            .foregroundColor(Color(hex: 0x2D6A4F))
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                    }
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
                                .frame(height: 75 * scaleFactor)
                        } else {
                            Text("Verify & Continue")
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
                    .disabled(viewModel.isLoading || viewModel.otp.count != 6)
                    .opacity((viewModel.isLoading || viewModel.otp.count != 6) ? 0.6 : 1.0)
                    .padding(.horizontal, padding)
                    .padding(.bottom, padding)
                }
            }
        }
    }
}
