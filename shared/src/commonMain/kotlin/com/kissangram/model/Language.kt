package com.kissangram.model

data class Language(
    val code: String,
    val nativeName: String,
    val englishName: String
) {
    companion object {
        val SUPPORTED_LANGUAGES = listOf(
            Language("hi", "हिंदी", "Hindi"),
            Language("pa", "ਪੰਜਾਬੀ", "Punjabi"),
            Language("bn", "বাংলা", "Bengali"),
            Language("ta", "தமிழ்", "Tamil"),
            Language("te", "తెలుగు", "Telugu"),
            Language("mr", "मराठी", "Marathi"),
            Language("gu", "ગુજરાતી", "Gujarati"),
            Language("kn", "ಕನ್ನಡ", "Kannada"),
            Language("ml", "മലയാളം", "Malayalam"),
            Language("en", "English", "English")
        )
    }
}
