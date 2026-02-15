import Foundation
import SwiftUI
import os.log
import Shared
import PhotosUI

private let editProfileVMLog = Logger(subsystem: "com.kissangram", category: "EditProfileViewModel")

@MainActor
class EditProfileViewModel: ObservableObject {
    
    // Repositories
    private let preferencesRepository = IOSPreferencesRepository()
    private lazy var authRepository: IOSAuthRepository = {
        IOSAuthRepository(preferencesRepository: preferencesRepository)
    }()
    private lazy var userRepository: FirestoreUserRepository = {
        FirestoreUserRepository(authRepository: authRepository)
    }()
    private let locationRepository = IOSLocationRepository()
    private let cropsRepository = IOSCropsRepository()
    private let storageRepository = IOSStorageRepository()
    
    // Current user
    @Published var currentUser: User? = nil
    
    // Editable fields
    @Published var name: String = ""
    @Published var username: String = ""
    @Published var bio: String = ""
    @Published var profileImageUrl: String? = nil
    @Published var selectedImageItem: PhotosPickerItem? = nil
    @Published var selectedImageData: Data? = nil
    @Published var village: String = ""
    @Published var selectedRole: UserRole = .farmer
    @Published var selectedCrops: Set<String> = []
    
    // Location state
    @Published var states: [String] = []
    @Published var districts: [String] = []
    @Published var selectedState: String? = nil
    @Published var selectedDistrict: String? = nil
    
    // Crops state
    @Published var allCrops: [String] = []
    
    // Loading states
    @Published var isLoadingUser: Bool = false
    @Published var isLoadingStates: Bool = false
    @Published var isLoadingDistricts: Bool = false
    @Published var isLoadingCrops: Bool = false
    @Published var isLoadingLocation: Bool = false
    @Published var isSaving: Bool = false
    @Published var isUploadingImage: Bool = false
    
    // Errors
    @Published var userError: String? = nil
    @Published var statesError: String? = nil
    @Published var districtsError: String? = nil
    @Published var cropsError: String? = nil
    @Published var locationError: String? = nil
    @Published var saveError: String? = nil
    
    // Success state
    @Published var saveSuccess: Bool = false
    
    init() {
        loadCurrentUser()
        loadStates()
        loadCrops()
    }
    
    /// Load current user profile from Firestore
    func loadCurrentUser() {
        isLoadingUser = true
        userError = nil
        
        Task {
            do {
                let user = try await userRepository.getCurrentUser()
                
                await MainActor.run {
                    if let user = user {
                        self.currentUser = user
                        self.name = user.name
                        self.username = user.username
                        self.bio = user.bio ?? ""
                        self.profileImageUrl = user.profileImageUrl
                        self.selectedRole = user.role
                        self.selectedState = user.location?.state
                        self.selectedDistrict = user.location?.district
                        self.village = user.location?.village ?? ""
                        self.selectedCrops = Set(user.expertise)
                        
                        // Load districts if state is already selected
                        if let state = user.location?.state {
                            self.loadDistricts(for: state)
                        }
                    } else {
                        self.userError = "User not found"
                    }
                    
                    self.isLoadingUser = false
                }
            } catch {
                await MainActor.run {
                    self.isLoadingUser = false
                    self.userError = error.localizedDescription
                }
            }
        }
    }
    
    /// Load all available states from Firestore
    func loadStates() {
        isLoadingStates = true
        statesError = nil
        
        Task {
            do {
                let fetchedStates = try await locationRepository.getStates()
                await MainActor.run {
                    self.states = fetchedStates
                    self.isLoadingStates = false
                }
            } catch {
                await MainActor.run {
                    self.isLoadingStates = false
                    self.statesError = error.localizedDescription
                }
            }
        }
    }
    
    /// Load districts for the selected state
    func loadDistricts(for state: String) {
        guard !state.isEmpty else {
            districts = []
            return
        }
        
        isLoadingDistricts = true
        districtsError = nil
        
        Task {
            do {
                let fetchedDistricts = try await locationRepository.getDistricts(state: state)
                await MainActor.run {
                    self.districts = fetchedDistricts
                    self.isLoadingDistricts = false
                }
            } catch {
                await MainActor.run {
                    self.isLoadingDistricts = false
                    self.districtsError = error.localizedDescription
                }
            }
        }
    }
    
    /// Called when user selects a state
    func onStateSelected(_ state: String) {
        selectedState = state
        selectedDistrict = nil // Reset district when state changes
        districts = []
        loadDistricts(for: state)
    }
    
    /// Called when user selects a district
    func onDistrictSelected(_ district: String) {
        selectedDistrict = district
    }
    
    /// Clear location selection
    func clearLocationSelection() {
        selectedState = nil
        selectedDistrict = nil
        districts = []
    }
    
    /// Use current GPS location to automatically set state and district
    func useCurrentLocation() {
        guard !isLoadingLocation else { return }
        
        isLoadingLocation = true
        locationError = nil
        
        Task {
            // Check location permission
            if !locationRepository.hasLocationPermission() {
                do {
                    let granted = try await locationRepository.requestLocationPermission()
                    if !granted.boolValue {
                        await MainActor.run {
                            self.isLoadingLocation = false
                            self.locationError = "Location permission is required to use current location"
                        }
                        return
                    }
                } catch {
                    await MainActor.run {
                        self.isLoadingLocation = false
                        self.locationError = "Failed to request location permission: \(error.localizedDescription)"
                    }
                    return
                }
            }
            
            do {
                // Get GPS coordinates
                guard let coordinates = try await locationRepository.getCurrentLocation() else {
                    await MainActor.run {
                        self.isLoadingLocation = false
                        self.locationError = "Unable to get current location. Please try again."
                    }
                    return
                }
                
                // Reverse geocode to get location name
                let locationName = try await locationRepository.reverseGeocode(
                    latitude: coordinates.latitude,
                    longitude: coordinates.longitude
                )
                
                guard let locationName = locationName else {
                    await MainActor.run {
                        self.isLoadingLocation = false
                        self.locationError = "Unable to get location name. Please try manual selection."
                    }
                    return
                }
                
                // Parse location name to extract state and district
                // Format is typically "District, State" or "State" or "District"
                let parts = locationName.components(separatedBy: ",").map { $0.trimmingCharacters(in: .whitespaces) }
                
                // Just set the location parts directly without matching to lists
                // If format is "District, State", set district and state
                // If only one part, assume it's the state
                if parts.count >= 2 {
                    // Format: "District, State"
                    let districtName = parts[0]
                    let stateName = parts[1]
                    
                    await MainActor.run {
                        self.selectedState = stateName
                        self.selectedDistrict = districtName
                        self.districts = [] // Clear districts, will be loaded when state is set
                    }
                    
                    // Load districts for the state (so dropdown works)
                    do {
                        let fetchedDistricts = try await locationRepository.getDistricts(state: stateName)
                        await MainActor.run {
                            self.districts = fetchedDistricts
                            self.isLoadingLocation = false
                            self.locationError = nil
                        }
                    } catch {
                        await MainActor.run {
                            self.isLoadingLocation = false
                            self.locationError = nil // Still set the values even if districts load fails
                        }
                    }
                } else if parts.count == 1 {
                    // Only one part, assume it's the state
                    await MainActor.run {
                        self.selectedState = parts[0]
                        self.selectedDistrict = nil
                        self.districts = []
                        self.isLoadingLocation = false
                        self.locationError = nil
                    }
                    
                    // Try to load districts for the state
                    do {
                        let fetchedDistricts = try await locationRepository.getDistricts(state: parts[0])
                        await MainActor.run {
                            self.districts = fetchedDistricts
                        }
                    } catch {
                        // Ignore error, just set the state
                    }
                } else {
                    await MainActor.run {
                        self.isLoadingLocation = false
                        self.locationError = "Invalid location format"
                    }
                }
            } catch {
                await MainActor.run {
                    self.isLoadingLocation = false
                    self.locationError = "Failed to get location: \(error.localizedDescription)"
                }
            }
        }
    }
    
    /// Load all available crops from Firestore
    func loadCrops() {
        isLoadingCrops = true
        cropsError = nil
        
        Task {
            do {
                let fetchedCrops = try await cropsRepository.getAllCrops()
                await MainActor.run {
                    self.allCrops = fetchedCrops
                    self.isLoadingCrops = false
                }
            } catch {
                await MainActor.run {
                    self.isLoadingCrops = false
                    self.cropsError = error.localizedDescription
                }
            }
        }
    }
    
    // MARK: - Profile Field Updates
    
    /// Update name field
    func updateName(_ name: String) {
        self.name = name
    }
    
    /// Update bio field
    func updateBio(_ bio: String) {
        self.bio = bio
    }
    
    /// Update village field
    func updateVillage(_ village: String) {
        self.village = village
    }
    
    /// Set selected image data (from PhotosPicker or camera). Must be called when user picks an image so Save can upload it.
    func setSelectedImageData(_ data: Data?, item: PhotosPickerItem?) {
        selectedImageData = data
        selectedImageItem = item
    }
    
    /// Called when user selects a role
    func onRoleSelected(_ role: UserRole) {
        selectedRole = role
    }
    
    /// Toggle crop selection
    func toggleCrop(_ crop: String) {
        if selectedCrops.contains(crop) {
            selectedCrops.remove(crop)
        } else {
            selectedCrops.insert(crop)
        }
    }
    
    // MARK: - Save Profile
    
    /// Save all profile changes to Firestore
    func saveProfile(onSuccess: @escaping () -> Void, onError: @escaping (String) -> Void) {
        editProfileVMLog.info("saveProfile called")
        // Validate
        guard !name.trimmingCharacters(in: .whitespaces).isEmpty else {
            onError("Name cannot be empty")
            return
        }
        
        guard name.count >= 2 else {
            onError("Name must be at least 2 characters")
            return
        }
        
        guard bio.count <= 150 else {
            onError("Bio cannot exceed 150 characters")
            return
        }
        
        isSaving = true
        isUploadingImage = selectedImageData != nil
        saveError = nil
        
        Task {
            do {
                // Upload image to Firebase Storage if a new image was selected
                var imageUrl = profileImageUrl
                editProfileVMLog.info("Initial profileImageUrl for Firestore: \(String(describing: imageUrl))")
                
                if let imageData = selectedImageData {
                    isUploadingImage = true
                    editProfileVMLog.info("New image selected, uploading to Storage (size=\(imageData.count) bytes)")
                    
                    // Get current user ID for upload
                    do {
                        if let userId = try await authRepository.getCurrentUserId() {
                            do {
                                // Convert Data to KotlinByteArray
                                let kotlinByteArray = KotlinByteArray(size: Int32(imageData.count))
                                imageData.withUnsafeBytes { bytes in
                                    for (index, byte) in bytes.enumerated() {
                                        kotlinByteArray.set(index: Int32(index), value: Int8(bitPattern: byte))
                                    }
                                }
                                
                                imageUrl = try await storageRepository.uploadProfileImage(userId: userId, imageData: kotlinByteArray)
                                editProfileVMLog.info("Storage upload succeeded, imageUrl=\(String(describing: imageUrl))")
                            } catch {
                                editProfileVMLog.error("Storage upload failed: \(error.localizedDescription); keeping existing URL")
                                // Continue with existing URL if upload fails
                            }
                        } else {
                            editProfileVMLog.warning("getCurrentUserId returned nil, cannot upload image")
                        }
                    } catch {
                        editProfileVMLog.error("getCurrentUserId failed: \(error.localizedDescription)")
                        // Continue with existing URL if getting user ID fails
                    }
                    
                    isUploadingImage = false
                }
                
                editProfileVMLog.info("Sending to Firestore: profileImageUrl=\(String(describing: imageUrl))")
                // Save profile to Firestore
                await saveProfileToFirestore(imageUrl: imageUrl, onSuccess: onSuccess, onError: onError)
                
            } catch {
                await MainActor.run {
                    isSaving = false
                    isUploadingImage = false
                    saveError = error.localizedDescription
                    onError(error.localizedDescription)
                }
            }
        }
    }
    
    /// Helper to save profile to Firestore
    private func saveProfileToFirestore(imageUrl: String?, onSuccess: @escaping () -> Void, onError: @escaping (String) -> Void) async {
        editProfileVMLog.info("saveProfileToFirestore called with profileImageUrl=\(String(describing: imageUrl))")
        do {
            try await userRepository.updateFullProfile(
                name: name.trimmingCharacters(in: .whitespaces),
                bio: bio.isEmpty ? nil : bio,
                profileImageUrl: imageUrl,
                role: selectedRole,
                state: selectedState,
                district: selectedDistrict,
                village: village.isEmpty ? nil : village,
                crops: selectedCrops.isEmpty ? nil : Array(selectedCrops)
            )
            
            await MainActor.run {
                editProfileVMLog.info("Firestore update succeeded; profileImageUrl saved=\(String(describing: imageUrl))")
                self.isSaving = false
                self.isUploadingImage = false
                self.profileImageUrl = imageUrl // Update with new URL
                self.selectedImageData = nil // Clear selected image after upload
                self.selectedImageItem = nil
                self.saveSuccess = true
                onSuccess()
            }
        } catch {
            await MainActor.run {
                editProfileVMLog.error("Firestore update failed: \(error.localizedDescription)")
                self.isSaving = false
                self.isUploadingImage = false
                self.saveError = error.localizedDescription
                onError(error.localizedDescription)
            }
        }
    }
    
    /// Clear save success flag
    func clearSaveSuccess() {
        saveSuccess = false
    }
    
    /// Check if there are unsaved changes
    func hasUnsavedChanges() -> Bool {
        guard let user = currentUser else { return false }
        
        // Check if any field has changed
        let nameChanged = name != user.name
        let bioChanged = bio != (user.bio ?? "")
        let roleChanged = selectedRole != user.role
        let stateChanged = selectedState != user.location?.state
        let districtChanged = selectedDistrict != user.location?.district
        let villageChanged = village != (user.location?.village ?? "")
        let cropsChanged = selectedCrops != Set(user.expertise)
        let imageChanged = selectedImageData != nil || selectedImageItem != nil
        
        return nameChanged || bioChanged || roleChanged || stateChanged ||
               districtChanged || villageChanged || cropsChanged || imageChanged
    }
}
