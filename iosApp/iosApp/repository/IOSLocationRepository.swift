import Foundation
import FirebaseFirestore

/// iOS implementation of LocationRepository.
/// Fetches location data from Firestore appConfig/locations document.
final class IOSLocationRepository {
    
    private let firestore = Firestore.firestore(database: "kissangram")
    
    // Cache for location data to avoid repeated Firestore reads
    private var cachedStates: [String]?
    private var cachedStatesAndDistricts: [String: [String]]?
    
    func getStates() async throws -> [String] {
        // Return cached data if available
        if let cached = cachedStates {
            return cached
        }
        
        // Fetch from Firestore
        let document = try await firestore.collection("appConfig").document("locations").getDocument()
        
        guard document.exists else {
            throw NSError(domain: "LocationRepository", code: 404, userInfo: [NSLocalizedDescriptionKey: "Location data not found. Please upload location data first."])
        }
        
        guard let stateNames = document.get("stateNames") as? [String] else {
            throw NSError(domain: "LocationRepository", code: 500, userInfo: [NSLocalizedDescriptionKey: "State names not found in location data"])
        }
        
        // Cache and return
        cachedStates = stateNames
        return stateNames
    }
    
    func getDistricts(state: String) async throws -> [String] {
        // Try to get from cache first
        if let cached = cachedStatesAndDistricts?[state] {
            return cached
        }
        
        // Fetch all data if not cached
        let allData = try await getAllStatesAndDistricts()
        return allData[state] ?? []
    }
    
    func getAllStatesAndDistricts() async throws -> [String: [String]] {
        // Return cached data if available
        if let cached = cachedStatesAndDistricts {
            return cached
        }
        
        // Fetch from Firestore
        let document = try await firestore.collection("appConfig").document("locations").getDocument()
        
        guard document.exists else {
            throw NSError(domain: "LocationRepository", code: 404, userInfo: [NSLocalizedDescriptionKey: "Location data not found. Please upload location data first."])
        }
        
        guard let statesAndDistricts = document.get("statesAndDistricts") as? [String: [String]] else {
            throw NSError(domain: "LocationRepository", code: 500, userInfo: [NSLocalizedDescriptionKey: "States and districts data not found"])
        }
        
        // Also cache the state names
        if let stateNames = document.get("stateNames") as? [String] {
            cachedStates = stateNames
        }
        
        // Cache and return
        cachedStatesAndDistricts = statesAndDistricts
        return statesAndDistricts
    }
    
    /// Clear the cache. Useful when you want to force a refresh.
    func clearCache() {
        cachedStates = nil
        cachedStatesAndDistricts = nil
    }
}
