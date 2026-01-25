import SwiftUI
import Shared

struct NameView: View {
    @StateObject private var viewModel = NameViewModel()
    let onNameSaved: () -> Void
    
    var body: some View {
        ZStack {
            Color(hex: 0xF8F9F1)
                .ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Step indicator
                Text("Step 1 of 3")
                    .font(.system(size: 15.75))
                    .foregroundColor(Color(hex: 0x6B6B6B))
                    .padding(.top, 27)
                
                Spacer()
                
                // Title
                Text("What should we call you?")
                    .font(.system(size: 33.75, weight: .bold))
                    .foregroundColor(Color(hex: 0x1B1B1B))
                    .lineSpacing(0)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 27)
                
                Spacer()
                    .frame(height: 9)
                
                Text("You can say your name or type it")
                    .font(.system(size: 16.2))
                    .foregroundColor(Color(hex: 0x6B6B6B))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 27)
                
                Spacer()
                    .frame(height: 36)
                
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
                    defaultText: "Hold to say your name",
                    listeningText: "Listening... Release when done"
                )
                .padding(.horizontal, 27)
                
                Spacer()
                    .frame(height: 36)
                
                // Name Input
                TextField("Your name", text: $viewModel.name)
                    .font(.system(size: 19.125))
                    .foregroundColor(Color(hex: 0x1B1B1B))
                    .padding(27)
                    .background(Color(hex: 0xF8F9F1))
                    .overlay(
                        RoundedRectangle(cornerRadius: 18)
                            .stroke(Color(hex: 0x2D6A4F).opacity(0.15), lineWidth: 1.18)
                    )
                    .cornerRadius(18)
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
                            .frame(height: 75)
                    } else {
                        Text("Next")
                            .font(.system(size: 20.25, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 75)
                    }
                }
                .background(Color(hex: 0x2D6A4F))
                .cornerRadius(18)
                .disabled(viewModel.isLoading || viewModel.name.trimmingCharacters(in: .whitespaces).count < 2)
                .opacity((viewModel.isLoading || viewModel.name.trimmingCharacters(in: .whitespaces).count < 2) ? 0.4 : 1.0)
                .padding(.horizontal, 27)
                .padding(.bottom, 27)
            }
        }
    }
}
