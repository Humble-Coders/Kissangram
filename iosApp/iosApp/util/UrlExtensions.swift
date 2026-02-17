import Foundation

/// Ensures HTTP URLs use HTTPS (required for iOS App Transport Security).
/// Cloudinary and other CDNs may return http:// URLs; iOS blocks them by default.
func ensureHttps(_ urlString: String) -> String {
    if urlString.hasPrefix("http://") {
        return urlString.replacingOccurrences(of: "http://", with: "https://")
    }
    return urlString
}

/// Generate thumbnail URL from a video URL.
/// For Cloudinary videos, adds thumbnail transformations to extract a frame.
/// For Firebase Storage videos, returns the video URL (thumbnails are stored separately).
/// Format: /video/upload/w_300,h_300,c_fill/so_0.3/v123/folder/file.jpg
/// The .jpg extension at the end tells Cloudinary to render the frame as an image.
func generateVideoThumbnailUrl(_ videoUrl: String) -> String {
    let secureUrl = ensureHttps(videoUrl)
    
    // Check if it's a Firebase Storage URL (thumbnails stored separately, use video URL as fallback)
    if secureUrl.contains("firebasestorage.googleapis.com") || secureUrl.contains("firebase.storage") {
        return secureUrl // Firebase Storage thumbnails are stored separately, use video URL as fallback
    }
    
    // Check if it's a Cloudinary URL
    guard secureUrl.contains("cloudinary.com") || secureUrl.contains("res.cloudinary.com") else {
        return secureUrl
    }
    
    // Remove any existing query parameters
    let baseUrl = secureUrl.split(separator: "?", maxSplits: 1).first.map(String.init) ?? secureUrl
    
    // Change file extension from video format to .jpg
    let urlWithoutExtension = baseUrl.replacingOccurrences(
        of: #"\.(mp4|mov|avi|webm|mkv)$"#,
        with: "",
        options: .regularExpression
    )
    let urlWithJpg = "\(urlWithoutExtension).jpg"
    
    // Insert transformations and so_ parameter right after /upload/
    // Format: https://res.cloudinary.com/cloud/video/upload/w_300,h_300,c_fill/so_0.3/v123/folder/file.jpg
    if let uploadRange = urlWithJpg.range(of: "/upload/") {
        let afterUpload = String(urlWithJpg[uploadRange.upperBound...])
        
        // Insert transformations and so_ right after /upload/
        let transformations = "w_300,h_300,c_fill/so_0.3"
        return String(urlWithJpg[..<uploadRange.upperBound]) + "\(transformations)/" + afterUpload
    }
    
    return urlWithJpg
}

/// Transform URL for thumbnail display (small size)
/// Uses: w_300,h_300,c_fill,q_auto,f_auto for Cloudinary
/// Firebase Storage URLs are returned as-is (no transformations)
func transformThumbnailUrl(_ url: String) -> String {
    let secureUrl = ensureHttps(url)
    
    // Check if Firebase Storage URL (no transformations)
    if secureUrl.contains("firebasestorage.googleapis.com") || secureUrl.contains("firebase.storage") {
        return secureUrl
    }
    
    // Check if Cloudinary URL (apply transformations)
    if secureUrl.contains("cloudinary.com") || secureUrl.contains("res.cloudinary.com") {
        let parts = secureUrl.split(separator: "?", maxSplits: 1)
        let baseUrl = parts.first.map(String.init) ?? secureUrl
        let existingParams = parts.count > 1 ? String(parts[1]) : nil
        
        if let params = existingParams, !params.isEmpty {
            return "\(baseUrl)?w_300,h_300,c_fill,q_auto,f_auto,\(params)"
        } else {
            return "\(baseUrl)?w_300,h_300,c_fill,q_auto,f_auto"
        }
    }
    
    return secureUrl
}
