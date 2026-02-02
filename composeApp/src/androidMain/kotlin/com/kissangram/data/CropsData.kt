package com.kissangram.data

/**
 * Reference data for crops categorized by type.
 * Used for populating crop selection dropdowns in Edit Profile screen.
 * This data can be uploaded to Firestore using the upload button in HomeScreen (dev only).
 */
object CropsData {
    
    val categories: Map<String, List<String>> = mapOf(
        "cereals_and_millets" to listOf(
            "Rice", "Wheat", "Maize", "Barley", "Oats", "Rye",
            "Sorghum (Jowar)", "Pearl Millet (Bajra)", "Finger Millet (Ragi)",
            "Foxtail Millet", "Proso Millet", "Kodo Millet", "Barnyard Millet", "Little Millet"
        ),
        "pulses_and_legumes" to listOf(
            "Chickpea (Gram)", "Pigeon Pea (Arhar)", "Green Gram (Moong)",
            "Black Gram (Urad)", "Lentil (Masoor)", "Field Pea", "Cowpea",
            "Horse Gram", "Kidney Bean (Rajma)", "Soybean", "Broad Bean", "Cluster Bean (Guar)"
        ),
        "oilseeds" to listOf(
            "Mustard", "Groundnut (Peanut)", "Sunflower", "Sesame (Til)",
            "Safflower", "Linseed (Flax)", "Castor", "Niger Seed"
        ),
        "vegetables" to listOf(
            "Potato", "Tomato", "Onion", "Garlic", "Ginger", "Chilli", "Capsicum",
            "Brinjal (Eggplant)", "Okra (Lady Finger)", "Cabbage", "Cauliflower",
            "Carrot", "Radish", "Beetroot", "Spinach", "Fenugreek (Methi)",
            "Coriander", "Lettuce", "Cucumber", "Pumpkin", "Bottle Gourd",
            "Bitter Gourd", "Ridge Gourd", "Snake Gourd", "Peas", "Sweet Corn"
        ),
        "fruits" to listOf(
            "Apple", "Banana", "Mango", "Orange", "Papaya", "Pineapple", "Guava",
            "Grapes", "Watermelon", "Muskmelon", "Pomegranate", "Lemon",
            "Sweet Lime (Mosambi)", "Pear", "Peach", "Plum", "Cherry",
            "Strawberry", "Blueberry", "Raspberry"
        ),
        "cash_crops" to listOf(
            "Sugarcane", "Cotton", "Jute", "Tobacco", "Rubber"
        ),
        "plantation_crops" to listOf(
            "Tea", "Coffee", "Cocoa", "Coconut", "Arecanut", "Oil Palm"
        ),
        "spices_and_condiments" to listOf(
            "Black Pepper", "Cardamom", "Clove", "Cinnamon", "Nutmeg",
            "Turmeric", "Ginger", "Coriander", "Cumin", "Fennel",
            "Fenugreek", "Bay Leaf", "Star Anise"
        ),
        "fodder_and_forage" to listOf(
            "Berseem", "Lucerne (Alfalfa)", "Napier Grass", "Sudan Grass",
            "Cowpea (Fodder)", "Maize (Fodder)", "Sorghum (Fodder)"
        ),
        "medicinal_and_aromatic" to listOf(
            "Aloe Vera", "Ashwagandha", "Tulsi (Holy Basil)", "Neem",
            "Lemongrass", "Mint", "Vetiver", "Isabgol", "Senna"
        )
    )
    
    /**
     * Human-readable category names for UI display
     */
    val categoryNames: Map<String, String> = mapOf(
        "cereals_and_millets" to "Cereals and Millets",
        "pulses_and_legumes" to "Pulses and Legumes",
        "oilseeds" to "Oilseeds",
        "vegetables" to "Vegetables",
        "fruits" to "Fruits",
        "cash_crops" to "Cash Crops",
        "plantation_crops" to "Plantation Crops",
        "spices_and_condiments" to "Spices and Condiments",
        "fodder_and_forage" to "Fodder and Forage",
        "medicinal_and_aromatic" to "Medicinal and Aromatic"
    )
    
    /**
     * Get all crops as a flat list (sorted alphabetically)
     */
    val allCrops: List<String> = categories.values.flatten().distinct().sorted()
    
    /**
     * Get crops for a specific category
     */
    fun getCrops(category: String): List<String> {
        return categories[category] ?: emptyList()
    }
    
    /**
     * Get the total count of all crops
     */
    val totalCrops: Int = allCrops.size
}
