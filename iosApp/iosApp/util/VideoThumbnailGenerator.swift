import Foundation
import UIKit
import AVFoundation

/// Utility for generating video thumbnails.
/// Extracts a frame from video at 30% duration and compresses it to JPEG.
enum VideoThumbnailGenerator {
    static let thumbnailWidth: CGFloat = 300
    static let thumbnailHeight: CGFloat = 300
    static let thumbnailQuality: CGFloat = 0.95 // 0.0-1.0 (increased from 0.80 for higher quality)
    static let thumbnailTimePercentage: Double = 0.3 // Extract frame at 30% of video duration
    
    /// Generate a thumbnail from video data.
    /// - Parameters:
    ///   - videoData: Video file data as Data
    ///   - videoURL: Optional file URL if video is saved to disk (more efficient)
    /// - Returns: Thumbnail image data as Data (JPEG format), or nil if generation fails
    static func generateThumbnail(videoData: Data, videoURL: URL? = nil) -> Data? {
        let tempURL: URL
        
        if let url = videoURL, FileManager.default.fileExists(atPath: url.path) {
            tempURL = url
        } else {
            // Create temporary file for video data
            let tempDir = FileManager.default.temporaryDirectory
            tempURL = tempDir.appendingPathComponent("video_thumb_\(UUID().uuidString).mp4")
            
            do {
                try videoData.write(to: tempURL)
            } catch {
                print("VideoThumbnailGenerator: Failed to write temporary file: \(error)")
                return nil
            }
        }
        
        defer {
            // Clean up temporary file if we created it
            if videoURL == nil {
                try? FileManager.default.removeItem(at: tempURL)
            }
        }
        
        return generateThumbnailFromURL(tempURL)
    }
    
    /// Generate thumbnail from video file URL (more efficient than Data).
    /// - Parameter videoURL: URL to video file
    /// - Returns: Thumbnail image data as Data (JPEG format), or nil if generation fails
    static func generateThumbnailFromURL(_ videoURL: URL) -> Data? {
        let asset = AVAsset(url: videoURL)
        let imageGenerator = AVAssetImageGenerator(asset: asset)
        imageGenerator.appliesPreferredTrackTransform = true
        imageGenerator.requestedTimeToleranceAfter = .zero
        imageGenerator.requestedTimeToleranceBefore = .zero
        
        // Get video duration
        let duration = asset.duration.seconds
        
        if duration <= 0 {
            print("VideoThumbnailGenerator: Invalid video duration: \(duration)")
            return nil
        }
        
        // Calculate time for thumbnail (30% of duration)
        let thumbnailTime = CMTime(seconds: duration * thumbnailTimePercentage, preferredTimescale: 600)
        
        do {
            // Extract frame at specified time
            let cgImage = try imageGenerator.copyCGImage(at: thumbnailTime, actualTime: nil)
            let image = UIImage(cgImage: cgImage)
            
            // Scale image to thumbnail size
            let size = CGSize(width: thumbnailWidth, height: thumbnailHeight)
            UIGraphicsBeginImageContextWithOptions(size, false, 1.0)
            image.draw(in: CGRect(origin: .zero, size: size))
            let scaledImage = UIGraphicsGetImageFromCurrentImageContext()
            UIGraphicsEndImageContext()
            
            guard let thumbnailImage = scaledImage else {
                print("VideoThumbnailGenerator: Failed to scale image")
                return nil
            }
            
            // Compress to JPEG
            guard let thumbnailData = thumbnailImage.jpegData(compressionQuality: thumbnailQuality) else {
                print("VideoThumbnailGenerator: Failed to compress thumbnail")
                return nil
            }
            
            print("VideoThumbnailGenerator: Generated thumbnail: \(thumbnailData.count) bytes")
            return thumbnailData
        } catch {
            print("VideoThumbnailGenerator: Failed to generate thumbnail: \(error)")
            return nil
        }
    }
}
