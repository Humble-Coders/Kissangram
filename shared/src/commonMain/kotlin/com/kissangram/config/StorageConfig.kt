package com.kissangram.config

/**
 * Storage provider configuration.
 * Determines which storage service to use for media uploads.
 */
enum class StorageProvider {
    /**
     * Cloudinary storage provider (legacy, kept for future use)
     */
    CLOUDINARY,
    
    /**
     * Firebase Storage provider (default, primary provider)
     */
    FIREBASE_STORAGE
}

/**
 * Configuration for storage provider selection.
 * Defaults to FIREBASE_STORAGE for all new uploads.
 * Cloudinary code remains in repository but is not used unless explicitly changed.
 */
object StorageConfig {
    /**
     * Current storage provider for media uploads.
     * Default: FIREBASE_STORAGE
     */
    val currentProvider: StorageProvider = StorageProvider.FIREBASE_STORAGE
}
