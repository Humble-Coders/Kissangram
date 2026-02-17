import Foundation
import UIKit
import AVFoundation

/// Utility for compressing media files (images, videos, audio) before upload.
/// Ensures files meet size and quality requirements for Firebase Storage.
enum MediaCompressor {
    // Image compression limits
    static let maxImageDimension: CGFloat = 1920 // pixels
    static let maxImageSizeBytes = 2 * 1024 * 1024 // 2MB
    static let imageCompressionQuality: CGFloat = 0.85 // 0.0-1.0
    
    // Video compression limits
    static let maxVideoResolution: Int = 1080 // pixels (height)
    static let maxVideoBitrate: Int = 5 * 1024 * 1024 // 5Mbps
    static let maxVideoSizeBytes = 50 * 1024 * 1024 // 50MB
    
    // Audio compression limits
    static let maxAudioBitrate: Int = 128 * 1024 // 128kbps
    static let maxAudioSizeBytes = 10 * 1024 * 1024 // 10MB
    
    /// Compress an image to meet size and dimension requirements.
    /// - Parameter imageData: Original image data
    /// - Returns: Compressed image data as Data
    static func compressImage(_ imageData: Data) -> Data {
        guard let image = UIImage(data: imageData) else {
            print("MediaCompressor: Failed to decode image")
            return imageData
        }
        
        // Calculate scaling factor to fit within max dimensions
        let originalWidth = image.size.width
        let originalHeight = image.size.height
        
        let scaleFactor: CGFloat
        if originalWidth > maxImageDimension || originalHeight > maxImageDimension {
            let widthScale = maxImageDimension / originalWidth
            let heightScale = maxImageDimension / originalHeight
            scaleFactor = min(widthScale, heightScale)
        } else {
            scaleFactor = 1.0
        }
        
        let targetWidth = originalWidth * scaleFactor
        let targetHeight = originalHeight * scaleFactor
        
        // Scale image
        let scaledImage: UIImage
        if scaleFactor < 1.0 {
            let size = CGSize(width: targetWidth, height: targetHeight)
            UIGraphicsBeginImageContextWithOptions(size, false, 1.0)
            image.draw(in: CGRect(origin: .zero, size: size))
            scaledImage = UIGraphicsGetImageFromCurrentImageContext() ?? image
            UIGraphicsEndImageContext()
        } else {
            scaledImage = image
        }
        
        // Compress iteratively to meet size requirement
        var quality = imageCompressionQuality
        var compressedData = scaledImage.jpegData(compressionQuality: quality) ?? imageData
        
        while compressedData.count > maxImageSizeBytes && quality > 0.5 {
            quality -= 0.1
            if let newData = scaledImage.jpegData(compressionQuality: quality) {
                compressedData = newData
            } else {
                break
            }
        }
        
        print("MediaCompressor: Image compressed: \(imageData.count) -> \(compressedData.count) bytes")
        return compressedData
    }
    
    /// Compress a video file.
    /// Note: Full video compression requires AVAssetExportSession.
    /// This is a placeholder that checks file size and returns original if within limits.
    /// 
    /// - Parameters:
    ///   - videoData: Original video data
    ///   - videoURL: Optional video file URL if video is saved to disk (more efficient)
    /// - Returns: Compressed video data (or original if already within limits)
    static func compressVideo(_ videoData: Data, videoURL: URL? = nil) -> Data {
        // Check if video is already within size limits
        if videoData.count <= maxVideoSizeBytes {
            print("MediaCompressor: Video already within size limits: \(videoData.count) bytes")
            return videoData
        }
        
        // For now, return original with a warning
        // Full video compression requires AVAssetExportSession integration
        print("MediaCompressor: Video exceeds size limit (\(videoData.count) bytes > \(maxVideoSizeBytes)). " +
              "Full compression requires AVAssetExportSession integration.")
        
        // TODO: Implement full video compression using AVAssetExportSession
        // For now, return original - client should handle size validation
        return videoData
    }
    
    /// Compress an audio file.
    /// Note: Full audio compression requires AVAudioFile.
    /// This is a placeholder that checks file size and returns original if within limits.
    /// 
    /// - Parameter audioData: Original audio data
    /// - Returns: Compressed audio data (or original if already within limits)
    static func compressAudio(_ audioData: Data) -> Data {
        // Check if audio is already within size limits
        if audioData.count <= maxAudioSizeBytes {
            print("MediaCompressor: Audio already within size limits: \(audioData.count) bytes")
            return audioData
        }
        
        // For now, return original with a warning
        // Full audio compression requires AVAudioFile integration
        print("MediaCompressor: Audio exceeds size limit (\(audioData.count) bytes > \(maxAudioSizeBytes)). " +
              "Full compression requires AVAudioFile integration.")
        
        // TODO: Implement full audio compression using AVAudioFile
        // For now, return original - client should handle size validation
        return audioData
    }
    
    /// Get video metadata to check if compression is needed.
    /// - Parameter videoURL: URL to video file
    /// - Returns: Video metadata or nil if extraction fails
    static func getVideoMetadata(videoURL: URL) -> VideoMetadata? {
        let asset = AVAsset(url: videoURL)
        
        guard let track = asset.tracks(withMediaType: .video).first else {
            return nil
        }
        
        let size = track.naturalSize
        let duration = asset.duration.seconds
        
        return VideoMetadata(
            width: Int(size.width),
            height: Int(size.height),
            durationMs: Int64(duration * 1000)
        )
    }
    
    struct VideoMetadata {
        let width: Int
        let height: Int
        let durationMs: Int64
    }
}
