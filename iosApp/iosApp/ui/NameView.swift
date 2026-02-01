import SwiftUI
import Shared

struct NameView: View {
    @StateObject private var viewModel = NameViewModel()
    let onNameSaved: () -> Void
    
    var body: some View {
        GeometryReader { geometry in
            let screenWidth = geometry.size.width
            // Responsive scaling factors based on screen width (360 as baseline)
            let scaleFactor = min(screenWidth / 360, 1.3)
            let padding: CGFloat = 27 * scaleFactor
            let spacing: CGFloat = 36 * scaleFactor
            
            ZStack {
                Color(hex: 0xF8F9F1)
                    .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Step indicator
                    Text("Step 1 of 3")
                        .font(.system(size: 15.75 * scaleFactor))
                        .foregroundColor(Color(hex: 0x6B6B6B))
                        .padding(.top, padding)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                    
                    Spacer()
                    
                    // Title
                    Text("What should we call you?")
                        .font(.system(size: 33.75 * scaleFactor, weight: .bold))
                        .foregroundColor(Color(hex: 0x1B1B1B))
                        .lineSpacing(0)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, padding)
                        .lineLimit(2)
                        .minimumScaleFactor(0.6)
                    
                    Spacer()
                        .frame(height: 9 * scaleFactor)
                    
                    Text("You can say your name or type it")
                        .font(.system(size: 16.2 * scaleFactor))
                        .foregroundColor(Color(hex: 0x6B6B6B))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, padding)
                        .lineLimit(2)
                        .minimumScaleFactor(0.7)
                    
                    Spacer()
                        .frame(height: spacing)
                
                    // Hold to say name button
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
                        defaultText: "Tap to say your name",
                        listeningText: "Listening... Release when done",
                        style: nameScreenButtonStyle
                    )
                    .padding(.horizontal, padding)
                    
                    Spacer()
                        .frame(height: spacing)
                    
                    // Name Input
                    TextField("Your name", text: $viewModel.name)
                        .font(.system(size: 19.125 * scaleFactor))
                        .foregroundColor(Color(hex: 0x1B1B1B))
                        .padding(padding)
                        .background(Color(hex: 0xF8F9F1))
                        .overlay(
                            RoundedRectangle(cornerRadius: 18 * scaleFactor)
                                .stroke(Color(hex: 0x2D6A4F).opacity(0.15), lineWidth: 1.18 * scaleFactor)
                        )
                        .cornerRadius(18 * scaleFactor)
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
                    
                    // Next Button
                    Button(action: {
                        Task {
                            await viewModel.saveName(
                                onSuccess: onNameSaved,
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
                            Text("Next")
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
                    .disabled(viewModel.isLoading || viewModel.name.trimmingCharacters(in: .whitespaces).count < 2)
                    .opacity((viewModel.isLoading || viewModel.name.trimmingCharacters(in: .whitespaces).count < 2) ? 0.4 : 1.0)
                    .padding(.horizontal, padding)
                    .padding(.bottom, padding)
                }
            }
        }
    }
}
