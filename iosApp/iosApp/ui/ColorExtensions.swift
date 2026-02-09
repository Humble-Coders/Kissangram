import SwiftUI

extension Color {
    /// Initialize Color from hex value
    /// - Parameters:
    ///   - hex: Hex value (supports both 6-digit RRGGBB and 8-digit AARRGGBB)
    ///   - alpha: Optional alpha value (only used for 6-digit hex, ignored for 8-digit)
    init(hex: UInt64, alpha: Double? = nil) {
        // Check if hex includes alpha (8-digit: AARRGGBB)
        let hasAlpha = hex > 0xFFFFFF
        
        if hasAlpha {
            // 8-digit hex with alpha: AARRGGBB
            let actualAlpha = Double((hex >> 24) & 0xFF) / 255.0
            self.init(
                .sRGB,
                red: Double((hex >> 16) & 0xFF) / 255.0,
                green: Double((hex >> 8) & 0xFF) / 255.0,
                blue: Double(hex & 0xFF) / 255.0,
                opacity: actualAlpha
            )
        } else {
            // 6-digit hex without alpha: RRGGBB
            let finalAlpha = alpha ?? 1.0
            self.init(
                .sRGB,
                red: Double((hex >> 16) & 0xFF) / 255.0,
                green: Double((hex >> 8) & 0xFF) / 255.0,
                blue: Double(hex & 0xFF) / 255.0,
                opacity: finalAlpha
            )
        }
    }
}
