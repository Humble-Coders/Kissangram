package com.kissangram.model

enum class UserRole {
    FARMER,
    AGRIPRENEUR,
    EXPERT,
    INPUT_SELLER,
    AGRI_LOVER;
    
    /**
     * Convert enum to Firestore string value
     */
    fun toFirestoreString(): String {
        return when (this) {
            FARMER -> "farmer"
            AGRIPRENEUR -> "agripreneur"
            EXPERT -> "expert"
            INPUT_SELLER -> "input_seller"
            AGRI_LOVER -> "agri_lover"
        }
    }
    
    /**
     * Convert Firestore string to enum
     */
    companion object {
        fun fromFirestoreString(value: String): UserRole {
            return when (value) {
                "farmer" -> FARMER
                "agripreneur" -> AGRIPRENEUR
                "expert" -> EXPERT
                "input_seller" -> INPUT_SELLER
                "agri_lover" -> AGRI_LOVER
                else -> FARMER // Default fallback
            }
        }
    }
}
