import SwiftUI
import Lottie

private let minDisplayDuration: TimeInterval = 1.5

// MARK: - Lottie UIViewRepresentable
private struct LottieAnimationViewRepresentable: UIViewRepresentable {
    let name: String
    let loopMode: LottieLoopMode

    func makeUIView(context: Context) -> LottieAnimationView {
        let view: LottieAnimationView
        if let _ = Bundle.main.path(forResource: name, ofType: "json", inDirectory: "Resources") {
            view = LottieAnimationView(name: name, bundle: .main, subdirectory: "Resources")
        } else {
            view = LottieAnimationView(name: name, bundle: .main)
        }
        view.loopMode = loopMode
        view.contentMode = .scaleAspectFit
        view.play()
        return view
    }

    func updateUIView(_ uiView: LottieAnimationView, context: Context) {
        if !uiView.isAnimationPlaying {
            uiView.play()
        }
    }
}

// MARK: - Full-screen loader with minimum 1.5s display
/// Shows Trator verde Lottie. If loading finishes in under 1 second, keeps showing for at least 1.5s total.
struct FullScreenLottieLoaderView: View {
    let isLoading: Bool
    var backgroundColor: Color = Color(red: 0.973, green: 0.976, blue: 0.945)

    @State private var loadingStartTime: Date?
    @State private var showLoaderExtra = false

    private var showLoader: Bool {
        isLoading || showLoaderExtra
    }

    var body: some View {
        Group {
            if showLoader {
                ZStack {
                    backgroundColor.ignoresSafeArea()
                    LottieAnimationViewRepresentable(
                        name: "TratorVerde",
                        loopMode: .loop
                    )
                    .frame(width: 200, height: 200)
                }
                .onChange(of: isLoading) { newValue in
                    if newValue {
                        loadingStartTime = Date()
                        showLoaderExtra = false
                    } else if let start = loadingStartTime {
                        loadingStartTime = nil
                        showLoaderExtra = true
                        let elapsed = Date().timeIntervalSince(start)
                        let remaining = max(0, minDisplayDuration - elapsed)
                        DispatchQueue.main.asyncAfter(deadline: .now() + remaining) {
                            showLoaderExtra = false
                        }
                    }
                }
                .onAppear {
                    if isLoading {
                        loadingStartTime = Date()
                    }
                }
            }
        }
    }
}
