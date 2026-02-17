package com.kissangram.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Utility for compressing media files (images, videos, audio) before upload.
 * Ensures files meet size and quality requirements for Firebase Storage.
 */
object MediaCompressor {
    private const val TAG = "MediaCompressor"
    
    // Image compression limits
    private const val MAX_IMAGE_DIMENSION = 1920 // pixels
    private const val MAX_IMAGE_SIZE_BYTES = 2 * 1024 * 1024 // 2MB
    private const val IMAGE_COMPRESSION_QUALITY = 85 // 0-100
    
    // Video compression limits
    private const val MAX_VIDEO_RESOLUTION = 1080 // pixels (height)
    private const val MAX_VIDEO_BITRATE = 5 * 1024 * 1024 // 5Mbps
    private const val MAX_VIDEO_SIZE_BYTES = 50 * 1024 * 1024 // 50MB
    
    // Audio compression limits
    private const val MAX_AUDIO_BITRATE = 128 * 1024 // 128kbps
    private const val MAX_AUDIO_SIZE_BYTES = 10 * 1024 * 1024 // 10MB
    
    /**
     * Compress an image to meet size and dimension requirements.
     * @param imageData Original image data
     * @return Compressed image data as ByteArray
     */
    fun compressImage(imageData: ByteArray): ByteArray {
        return try {
            // Decode image
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(imageData, 0, imageData.size, options)
            
            val originalWidth = options.outWidth
            val originalHeight = options.outHeight
            
            // Calculate scaling factor to fit within max dimensions
            val scaleFactor = if (originalWidth > MAX_IMAGE_DIMENSION || originalHeight > MAX_IMAGE_DIMENSION) {
                val widthScale = MAX_IMAGE_DIMENSION.toFloat() / originalWidth
                val heightScale = MAX_IMAGE_DIMENSION.toFloat() / originalHeight
                min(widthScale, heightScale)
            } else {
                1f
            }
            
            val targetWidth = (originalWidth * scaleFactor).toInt()
            val targetHeight = (originalHeight * scaleFactor).toInt()
            
            // Decode with scaling
            val scaledOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(originalWidth, originalHeight, targetWidth, targetHeight)
            }
            val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size, scaledOptions)
                ?: throw IllegalStateException("Failed to decode image")
            
            // Scale bitmap if needed
            val scaledBitmap = if (bitmap.width != targetWidth || bitmap.height != targetHeight) {
                Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            } else {
                bitmap
            }
            
            // Compress iteratively to meet size requirement
            var quality = IMAGE_COMPRESSION_QUALITY
            var compressedData = compressBitmap(scaledBitmap, quality)
            
            while (compressedData.size > MAX_IMAGE_SIZE_BYTES && quality > 50) {
                quality -= 10
                compressedData = compressBitmap(scaledBitmap, quality)
            }
            
            // Clean up
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            bitmap.recycle()
            
            Log.d(TAG, "Image compressed: ${imageData.size} -> ${compressedData.size} bytes")
            compressedData
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compress image", e)
            // Return original if compression fails
            imageData
        }
    }
    
    /**
     * Compress a video file.
     * Note: Full video compression requires MediaCodec or FFmpeg.
     * This is a placeholder that checks file size and returns original if within limits.
     * For production, consider using a library like FFmpeg or MediaCodec.
     * 
     * @param videoData Original video data
     * @param context Android context for temporary file operations
     * @return Compressed video data (or original if already within limits)
     */
    fun compressVideo(videoData: ByteArray, context: Context): ByteArray {
        return try {
            // Check if video is already within size limits
            if (videoData.size <= MAX_VIDEO_SIZE_BYTES) {
                Log.d(TAG, "Video already within size limits: ${videoData.size} bytes")
                return videoData
            }
            
            // For now, return original with a warning
            // Full video compression requires MediaCodec or FFmpeg integration
            Log.w(TAG, "Video exceeds size limit (${videoData.size} bytes > $MAX_VIDEO_SIZE_BYTES). " +
                    "Full compression requires MediaCodec or FFmpeg integration.")
            
            // TODO: Implement full video compression using MediaCodec or FFmpeg
            // For now, return original - client should handle size validation
            videoData
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compress video", e)
            videoData
        }
    }
    
    /**
     * Compress an audio file.
     * Note: Full audio compression requires MediaRecorder or AudioRecord.
     * This is a placeholder that checks file size and returns original if within limits.
     * 
     * @param audioData Original audio data
     * @return Compressed audio data (or original if already within limits)
     */
    fun compressAudio(audioData: ByteArray): ByteArray {
        return try {
            // Check if audio is already within size limits
            if (audioData.size <= MAX_AUDIO_SIZE_BYTES) {
                Log.d(TAG, "Audio already within size limits: ${audioData.size} bytes")
                return audioData
            }
            
            // For now, return original with a warning
            // Full audio compression requires MediaRecorder or AudioRecord integration
            Log.w(TAG, "Audio exceeds size limit (${audioData.size} bytes > $MAX_AUDIO_SIZE_BYTES). " +
                    "Full compression requires MediaRecorder or AudioRecord integration.")
            
            // TODO: Implement full audio compression using MediaRecorder or AudioRecord
            // For now, return original - client should handle size validation
            audioData
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compress audio", e)
            audioData
        }
    }
    
    /**
     * Compress a bitmap to JPEG format.
     */
    private fun compressBitmap(bitmap: Bitmap, quality: Int): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return outputStream.toByteArray()
    }
    
    /**
     * Calculate inSampleSize for efficient bitmap decoding.
     */
    private fun calculateInSampleSize(
        originalWidth: Int,
        originalHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Int {
        var inSampleSize = 1
        
        if (originalHeight > targetHeight || originalWidth > targetWidth) {
            val halfHeight = originalHeight / 2
            val halfWidth = originalWidth / 2
            
            while ((halfHeight / inSampleSize) >= targetHeight &&
                (halfWidth / inSampleSize) >= targetWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    /**
     * Get video metadata to check if compression is needed.
     */
    fun getVideoMetadata(videoData: ByteArray, context: Context): VideoMetadata? {
        return try {
            val tempFile = File(context.cacheDir, "temp_video_${System.currentTimeMillis()}.mp4")
            tempFile.outputStream().use { it.write(videoData) }
            
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(tempFile.absolutePath)
            
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            
            retriever.release()
            tempFile.delete()
            
            VideoMetadata(width, height, duration)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get video metadata", e)
            null
        }
    }
    
    data class VideoMetadata(
        val width: Int,
        val height: Int,
        val durationMs: Long
    )
}
