package com.kissangram.model

/**
 * Result of uploading media to Cloudinary.
 * Contains the media URL and optional thumbnail URL.
 */
data class MediaUploadResult(
    val mediaUrl: String,
    val thumbnailUrl: String? = null
)
