import SwiftUI

/**
 * Style configuration for HoldToSpeakButton.
 * Allows customization of visual appearance while maintaining consistent behavior.
 */
struct HoldToSpeakButtonStyle {
    var buttonHeight: CGFloat = 83
    var cornerRadius: CGFloat = 18
    var iconSize: CGFloat = 45
    var iconInnerSize: CGFloat = 20
    var textSize: CGFloat = 18
    var textFontWeight: Font.Weight = .semibold
    var horizontalPadding: CGFloat = 19
    var verticalPadding: CGFloat = 0
    var spacing: CGFloat = 14
    var scaleAnimation: CGFloat = 1.1
    var backgroundColorIdle: Color = Color(hex: 0xF8F9F1)
    var backgroundColorListening: Color = Color(hex: 0x2D6A4F).opacity(0.1)
    var borderColorIdle: Color = Color(hex: 0x2D6A4F).opacity(0.2)
    var borderColorListening: Color = Color(hex: 0x2D6A4F)
    var iconColorIdle: Color = Color(hex: 0x2D6A4F)
    var iconColorListening: Color = Color(hex: 0xFFB703)
    var textColorIdle: Color = Color(hex: 0x1B1B1B)
    var textColorListening: Color = Color(hex: 0x2D6A4F)
    var layoutDirection: LayoutDirection = .horizontal
    
    enum LayoutDirection {
        case horizontal
        case vertical
    }
}

/**
 * Default style matching the phone number screen (stable version).
 */
let defaultHoldToSpeakButtonStyle = HoldToSpeakButtonStyle()

/**
 * OTP screen style - horizontal layout with "Tap to speak" text.
 */
let otpScreenButtonStyle = HoldToSpeakButtonStyle(
    buttonHeight: 83,
    cornerRadius: 18,
    iconSize: 45,
    iconInnerSize: 20,
    textSize: 18,
    textFontWeight: .semibold,
    horizontalPadding: 19,
    verticalPadding: 0,
    spacing: 14,
    backgroundColorIdle: Color(hex: 0xF8F9F1),
    backgroundColorListening: Color(hex: 0x2D6A4F).opacity(0.1),
    borderColorIdle: Color(hex: 0x2D6A4F).opacity(0.2),
    borderColorListening: Color(hex: 0x2D6A4F),
    textColorIdle: Color(hex: 0x1B1B1B),
    textColorListening: Color(hex: 0x2D6A4F),
    layoutDirection: .horizontal
)

/**
 * Name screen style - vertical layout with larger icon and "Tap to say your name" text.
 */
let nameScreenButtonStyle = HoldToSpeakButtonStyle(
    buttonHeight: 198,
    cornerRadius: 27,
    iconSize: 80,
    iconInnerSize: 45,
    textSize: 16.875,
    textFontWeight: .medium,
    horizontalPadding: 0,
    verticalPadding: 37,
    spacing: 18,
    backgroundColorIdle: Color(hex: 0x2D6A4F).opacity(0.05),
    backgroundColorListening: Color(hex: 0x2D6A4F).opacity(0.1),
    borderColorIdle: Color(hex: 0x2D6A4F).opacity(0.1),
    borderColorListening: Color(hex: 0x2D6A4F),
    textColorIdle: Color(hex: 0x2D6A4F),
    textColorListening: Color(hex: 0x2D6A4F),
    layoutDirection: .vertical
)

/**
 * Reusable hold-to-speak button component for speech recognition.
 * 
 * - Parameters:
 *   - isListening: Whether speech recognition is currently active
 *   - isLoading: Whether the component is in a loading state
 *   - onStartListening: Callback when user starts holding (press down)
 *   - onStopListening: Callback when user releases (lift up)
 *   - defaultText: Text to show when not listening (default: "Hold to speak")
 *   - listeningText: Text to show when listening (default: "Listening... Release when done")
 *   - style: Style configuration for customizing the button appearance
 */
struct HoldToSpeakButton: View {
    let isListening: Bool
    let isLoading: Bool
    let isProcessing: Bool
    let onStartListening: () -> Void
    let onStopListening: () -> Void
    let defaultText: String
    let listeningText: String
    let processingText: String
    let style: HoldToSpeakButtonStyle
    
    init(
        isListening: Bool,
        isLoading: Bool,
        isProcessing: Bool = false,
        onStartListening: @escaping () -> Void,
        onStopListening: @escaping () -> Void,
        defaultText: String = "Hold to speak",
        listeningText: String = "Listening... Release when done",
        processingText: String = "Processing...",
        style: HoldToSpeakButtonStyle = defaultHoldToSpeakButtonStyle
    ) {
        self.isListening = isListening
        self.isLoading = isLoading
        self.isProcessing = isProcessing
        self.onStartListening = onStartListening
        self.onStopListening = onStopListening
        self.defaultText = defaultText
        self.listeningText = listeningText
        self.processingText = processingText
        self.style = style
    }
    
    var body: some View {
        Group {
            switch style.layoutDirection {
            case .horizontal:
                HStack(spacing: style.spacing) {
                    iconView
                    textView
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, style.horizontalPadding)
            case .vertical:
                VStack(spacing: style.spacing) {
                    iconView
                    textView
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, style.verticalPadding)
            }
        }
        .frame(height: style.buttonHeight)
        .background(
            isProcessing || isListening ? style.backgroundColorListening : style.backgroundColorIdle
        )
        .overlay(
            RoundedRectangle(cornerRadius: style.cornerRadius)
                .stroke(
                    isProcessing || isListening ? style.borderColorListening : style.borderColorIdle,
                    lineWidth: 1.18
                )
        )
        .cornerRadius(style.cornerRadius)
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    if !isListening && !isLoading && !isProcessing {
                        // Haptic feedback
                        let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
                        impactFeedback.impactOccurred()
                        onStartListening()
                    }
                }
                .onEnded { _ in
                    if isListening {
                        // Haptic feedback
                        let impactFeedback = UIImpactFeedbackGenerator(style: .light)
                        impactFeedback.impactOccurred()
                        onStopListening()
                    }
                }
        )
        .disabled(isLoading || isProcessing)
    }
    
    private var iconView: some View {
        Circle()
            .fill(
                isProcessing || isListening ? style.iconColorListening : style.iconColorIdle
            )
            .frame(width: style.iconSize, height: style.iconSize)
            .overlay(
                Group {
                    if isProcessing {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .scaleEffect(0.8)
                    } else {
                        Image(systemName: "mic.fill")
                            .foregroundColor(.white)
                            .font(.system(size: style.iconInnerSize))
                            .scaleEffect(isListening ? style.scaleAnimation : 1.0)
                            .animation(.easeInOut(duration: 0.2).repeatForever(autoreverses: isListening), value: isListening)
                    }
                }
            )
    }
    
    private var textView: some View {
        Text(
            isProcessing ? processingText :
            isListening ? listeningText :
            defaultText
        )
        .font(.system(size: style.textSize, weight: style.textFontWeight))
        .foregroundColor(
            isProcessing || isListening ? style.textColorListening : style.textColorIdle
        )
    }
}
