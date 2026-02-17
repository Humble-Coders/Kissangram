package com.kissangram.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Utility for generating video thumbnails.
 * Extracts a frame from video at 30% duration and compresses it to JPEG.
 */
object VideoThumbnailGenerator {
    private const val TAG = "VideoThumbnailGenerator"
    private const val THUMBNAIL_WIDTH = 300
    private const val THUMBNAIL_HEIGHT = 300
    private const val THUMBNAIL_QUALITY = 95 // 0-100 (increased from 80 for higher quality)
    private const val THUMBNAIL_TIME_PERCENTAGE = 0.3f // Extract frame at 30% of video duration
    
    /**
     * Generate a thumbnail from video data.
     * @param videoData Video file data as ByteArray
     * @param videoFilePath Optional file path if video is saved to disk (more efficient)
     * @return Thumbnail image data as ByteArray (JPEG format), or null if generation fails
     */
    fun generateThumbnail(videoData: ByteArray, videoFilePath: String? = null): ByteArray? {
        return try {
            val retriever = MediaMetadataRetriever()
            
            // Set data source
            if (videoFilePath != null && File(videoFilePath).exists()) {
                retriever.setDataSource(videoFilePath)
            } else {
                // Use temporary file for ByteArray
                val tempFile = File.createTempFile("video_thumb_", ".mp4")
                try {
                    tempFile.outputStream().use { it.write(videoData) }
                    retriever.setDataSource(tempFile.absolutePath)
                } finally {
                    tempFile.delete()
                }
            }
            
            // Get video duration
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L
            
            if (duration <= 0) {
                Log.w(TAG, "Invalid video duration: $durationStr")
                retriever.release()
                return null
            }
            
            // Calculate time for thumbnail (30% of duration)
            val thumbnailTime = (duration * THUMBNAIL_TIME_PERCENTAGE).toLong()
            
            // Extract frame at specified time
            val bitmap = retriever.getFrameAtTime(
                thumbnailTime * 1000, // Convert to microseconds
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            
            retriever.release()
            
            if (bitmap == null) {
                Log.w(TAG, "Failed to extract frame from video")
                return null
            }
            
            // Scale bitmap to thumbnail size
            val scaledBitmap = Bitmap.createScaledBitmap(
                bitmap,
                THUMBNAIL_WIDTH,
                THUMBNAIL_HEIGHT,
                true
            )
            
            // Compress to JPEG
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, outputStream)
            val thumbnailData = outputStream.toByteArray()
            
            // Clean up
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            bitmap.recycle()
            
            Log.d(TAG, "Generated thumbnail: ${thumbnailData.size} bytes")
            thumbnailData
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate video thumbnail", e)
            null
        }
    }
    
    /**
     * Generate thumbnail from video file path (more efficient than ByteArray).
     * @param videoFilePath Path to video file
     * @return Thumbnail image data as ByteArray (JPEG format), or null if generation fails
     */
    fun generateThumbnailFromPath(videoFilePath: String): ByteArray? {
        return generateThumbnail(ByteArray(0), videoFilePath)
    }
}
