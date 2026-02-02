import Foundation
import FirebaseFirestore

/// iOS implementation of CropsRepository.
/// Fetches crops data from Firestore appConfig/crops document.
final class IOSCropsRepository {
    
    private let firestore = Firestore.firestore(database: "kissangram")
    
    // Cache for crops data to avoid repeated Firestore reads
    private var cachedCrops: [String]?
    
    func getAllCrops() async throws -> [String] {
        // Return cached data if available
        if let cached = cachedCrops {
            return cached
        }
        
        // Fetch from Firestore
        let document = try await firestore.collection("appConfig").document("crops").getDocument()
        
        guard document.exists else {
            throw NSError(domain: "CropsRepository", code: 404, userInfo: [NSLocalizedDescriptionKey: "Crops data not found. Please upload crops data first."])
        }
        
        guard let allCrops = document.get("allCrops") as? [String] else {
            throw NSError(domain: "CropsRepository", code: 500, userInfo: [NSLocalizedDescriptionKey: "Crops list not found in crops data"])
        }
        
        // Cache and return
        cachedCrops = allCrops
        return allCrops
    }
    
    /// Clear the cache. Useful when you want to force a refresh.
    func clearCache() {
        cachedCrops = nil
    }
}
