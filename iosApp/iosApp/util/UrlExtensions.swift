import Foundation

/// Ensures HTTP URLs use HTTPS (required for iOS App Transport Security).
/// Cloudinary and other CDNs may return http:// URLs; iOS blocks them by default.
func ensureHttps(_ urlString: String) -> String {
    if urlString.hasPrefix("http://") {
        return urlString.replacingOccurrences(of: "http://", with: "https://")
    }
    return urlString
}
