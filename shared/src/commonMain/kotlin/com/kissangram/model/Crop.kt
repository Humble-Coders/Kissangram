package com.kissangram.model

/**
 * Crop reference data model matching Firestore schema
 */
data class Crop(
    val id: String,
    val nameEn: String,
    val nameHi: String,
    val namePa: String,
    val imageUrl: String?,
    val hashtags: List<String>,
    val category: CropCategory,
    val season: CropSeason
)

enum class CropCategory {
    CEREAL,
    PULSE,
    VEGETABLE,
    FRUIT,
    OILSEED,
    SPICE
}

enum class CropSeason {
    RABI,
    KHARIF,
    ZAID
}

/**
 * Hashtag data model
 */
data class Hashtag(
    val id: String,
    val displayName: String,
    val relatedCrops: List<String>,
    val postsCount: Int,
    val trendingScore: Int
)
