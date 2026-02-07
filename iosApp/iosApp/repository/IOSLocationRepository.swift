import Foundation
import FirebaseFirestore
import CoreLocation
import Shared

/// iOS implementation of LocationRepository.
/// Fetches location data from Firestore appConfig/locations document.
final class IOSLocationRepository: NSObject, LocationRepository {
  
    
 
   
    
    
    private let firestore = Firestore.firestore(database: "kissangram")
    private let geocoder = CLGeocoder()
    private let locationManager = CLLocationManager()
    
    // Cache for location data to avoid repeated Firestore reads
    private var cachedStates: [String]?
    private var cachedStatesAndDistricts: [String: [String]]?
    
    // For async location requests
    private var locationContinuation: CheckedContinuation<Shared.LocationCoordinates?, Error>?
    private var permissionContinuation: CheckedContinuation<Bool, Never>?
    
    override init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
    }
    
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
    
    // MARK: - Location Services
    
    func getCurrentLocation() async throws -> Shared.LocationCoordinates? {
        guard hasLocationPermission() else {
            throw NSError(domain: "LocationRepository", code: 403, userInfo: [NSLocalizedDescriptionKey: "Location permission not granted"])
        }
        
        return try await withCheckedThrowingContinuation { continuation in
            locationContinuation = continuation
            
            locationManager.requestLocation()
            
            // Timeout after 30 seconds
            DispatchQueue.main.asyncAfter(deadline: .now() + 30) {
                if self.locationContinuation != nil {
                    self.locationContinuation = nil
                    continuation.resume(throwing: NSError(domain: "LocationRepository", code: 408, userInfo: [NSLocalizedDescriptionKey: "Location request timed out"]))
                }
            }
        }
    }
    
    func reverseGeocode(latitude: Double, longitude: Double) async throws -> String? {
        let location = CLLocation(latitude: latitude, longitude: longitude)
        
        do {
            let placemarks = try await geocoder.reverseGeocodeLocation(location)
            guard let placemark = placemarks.first else { return nil }
            
            // Build location name from placemark components
            var components: [String] = []
            
            if let locality = placemark.locality {
                components.append(locality)
            }
            if let administrativeArea = placemark.administrativeArea {
                components.append(administrativeArea)
            }
            
            return components.isEmpty ? nil : components.joined(separator: ", ")
        } catch {
            throw NSError(domain: "LocationRepository", code: 500, userInfo: [NSLocalizedDescriptionKey: "Reverse geocoding failed: \(error.localizedDescription)"])
        }
    }
    
    func forwardGeocode(locationName: String) async throws -> Shared.LocationCoordinates? {
        do {
            let placemarks = try await geocoder.geocodeAddressString(locationName)
            guard let placemark = placemarks.first,
                  let location = placemark.location else {
                return nil
            }
            
            return Shared.LocationCoordinates(
                latitude: location.coordinate.latitude,
                longitude: location.coordinate.longitude
            )
        } catch {
            throw NSError(domain: "LocationRepository", code: 500, userInfo: [NSLocalizedDescriptionKey: "Forward geocoding failed: \(error.localizedDescription)"])
        }
    }
    
    func hasLocationPermission() -> Bool {
        let status = locationManager.authorizationStatus
        let hasPermission = status == .authorizedWhenInUse || status == .authorizedAlways
        return hasPermission
    }
    
    func requestLocationPermission() async throws -> KotlinBoolean {
        guard !hasLocationPermission() else { return KotlinBoolean(value: true) }
        
        let status = locationManager.authorizationStatus
        
        if status == .notDetermined {
            let granted = await withCheckedContinuation { [weak self] continuation in
                guard let self = self else {
                    continuation.resume(returning: false)
                    return
                }
                self.permissionContinuation = continuation
                self.locationManager.requestWhenInUseAuthorization()
                
                // Fallback timeout after 5 seconds
                DispatchQueue.main.asyncAfter(deadline: .now() + 5) { [weak self] in
                    guard let self = self, let permCont = self.permissionContinuation else {
                        return
                    }
                    self.permissionContinuation = nil
                    permCont.resume(returning: self.hasLocationPermission())
                }
            }
            return KotlinBoolean(value: granted)
        } else {
            return KotlinBoolean(value: false)
        }
    }
}

// MARK: - CLLocationManagerDelegate
extension IOSLocationRepository: CLLocationManagerDelegate {
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.first,
              let continuation = locationContinuation else {
            return
        }
        
        locationContinuation = nil
        let coordinates = Shared.LocationCoordinates(
            latitude: location.coordinate.latitude,
            longitude: location.coordinate.longitude
        )
        continuation.resume(returning: coordinates)
    }
    
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        guard let continuation = locationContinuation else {
            return
        }
        
        locationContinuation = nil
        continuation.resume(throwing: error)
    }
    
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        // Handle authorization changes for permission requests
        if let continuation = permissionContinuation {
            permissionContinuation = nil
            continuation.resume(returning: hasLocationPermission())
        }
    }
}
