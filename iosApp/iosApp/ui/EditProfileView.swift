import SwiftUI
import Shared
import PhotosUI
import AVFoundation

private let editProfileBackground = Color(red: 0.973, green: 0.976, blue: 0.945)
private let cardBackground = Color.white
private let inputBackground = Color(red: 0.973, green: 0.976, blue: 0.945)
private let disabledInputBackground = Color(red: 0.898, green: 0.902, blue: 0.859).opacity(0.3)


// Note: State and District data is now fetched from Firestore via EditProfileViewModel

struct EditProfileView: View {
    @StateObject private var viewModel = EditProfileViewModel()
    
    @State private var showStatePicker = false
    @State private var showDistrictPicker = false
    
    // Image picker state
    @State private var showImageSourceSheet = false
    @State private var showImagePicker = false
    @State private var showCamera = false
    @State private var selectedImage: UIImage? = nil
    @State private var selectedItem: PhotosPickerItem? = nil
    @State private var imagePickerSourceType: UIImagePickerController.SourceType = .photoLibrary
    
    // Snackbar/Alert state
    @State private var showAlert = false
    @State private var alertMessage = ""
    @State private var isAlertSuccess = false
    
    // Unsaved changes confirmation dialog
    @State private var showUnsavedChangesDialog = false
    
    var onBackClick: () -> Void = {}
    var onSaveClick: () -> Void = {}
    var onNavigateToExpertDocument: () -> Void = {}
    
    // Handle role change
    private func handleRoleChange(_ newRole: UserRole) {
        viewModel.onRoleSelected(newRole)
        // If Expert is selected, navigate to document upload screen
        if newRole == .expert {
            onNavigateToExpertDocument()
        }
    }
    
    // Handle save
    private func handleSave() {
        viewModel.saveProfile(
            onSuccess: {
                alertMessage = "Profile saved successfully!"
                isAlertSuccess = true
                showAlert = true
                // Delay the navigation back
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                    onSaveClick()
                }
            },
            onError: { error in
                alertMessage = error
                isAlertSuccess = false
                showAlert = true
            }
        )
    }
    
    // Handle back button with unsaved changes check
    private func handleBackClick() {
        if viewModel.hasUnsavedChanges() {
            showUnsavedChangesDialog = true
        } else {
            onBackClick()
        }
    }
    
    var body: some View {
        NavigationStack {
            ZStack {
                editProfileBackground
                
                VStack(spacing: 0) {
                    if viewModel.isLoadingUser {
                        // Loading state
                        VStack {
                            Spacer()
                            ProgressView()
                                .scaleEffect(1.5)
                            Text("Loading profile...")
                                .foregroundColor(.textSecondary)
                                .padding(.top, 16)
                            Spacer()
                        }
                    } else {
                        ScrollView {
                            VStack(spacing: 27) {
                                // Profile Image Section
                                ProfileImageSection(
                                    profileImageUrl: viewModel.profileImageUrl,
                                    selectedImage: selectedImage,
                                    userName: viewModel.name.isEmpty ? "R" : String(viewModel.name.prefix(1)),
                                    onImageClick: { showImageSourceSheet = true }
                                )
                                .photosPicker(
                                    isPresented: $showImagePicker,
                                    selection: $selectedItem,
                                    matching: .images
                                )
                                .onChange(of: selectedItem) { newItem in
                                    Task {
                                        if let newItem = newItem {
                                            if let data = try? await newItem.loadTransferable(type: Data.self) {
                                                if let uiImage = UIImage(data: data) {
                                                    await MainActor.run {
                                                        selectedImage = uiImage
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                // Basic Details Section
                                BasicDetailsSection(
                                    name: Binding(
                                        get: { viewModel.name },
                                        set: { viewModel.updateName($0) }
                                    ),
                                    username: viewModel.username,
                                    bio: Binding(
                                        get: { viewModel.bio },
                                        set: { viewModel.updateBio($0) }
                                    ),
                                    role: Binding(
                                        get: { viewModel.selectedRole },
                                        set: { viewModel.onRoleSelected($0) }
                                    ),
                                    onRoleChange: { handleRoleChange($0) }
                                )
                                
                                // Location Section with Village
                                LocationSectionWithVillage(
                                    viewModel: viewModel,
                                    showStatePicker: $showStatePicker,
                                    showDistrictPicker: $showDistrictPicker
                                )
                                
                                // Crops Section
                                CropsSection(
                                    viewModel: viewModel,
                                    selectedCrops: Binding(
                                        get: { viewModel.selectedCrops },
                                        set: { _ in } // Ignore sets, use toggleCrop instead
                                    ),
                                    onCropToggle: { viewModel.toggleCrop($0) }
                                )
                                
                                // Add bottom padding to prevent content from being hidden behind the button
                                Spacer()
                                    .frame(height: 100)
                            }
                            .padding(.horizontal, 18)
                            .padding(.top, 24)
                            .padding(.bottom, 20)
                        }
                        .frame(maxHeight: .infinity)
                    }
                    
                    // Bottom Save Button
                    VStack(spacing: 0) {
                        Rectangle()
                            .frame(height: 1)
                            .foregroundColor(Color.black.opacity(0.05))
                        
                        Button(action: handleSave) {
                            HStack(spacing: 8) {
                                if viewModel.isSaving {
                                    ProgressView()
                                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                        .scaleEffect(0.8)
                                    Text("Saving...")
                                        .font(.system(size: 18, weight: .bold))
                                        .foregroundColor(.white)
                                } else {
                                    Text("Save Changes")
                                        .font(.system(size: 18, weight: .bold))
                                        .foregroundColor(.white)
                                }
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 63)
                            .background(viewModel.isSaving ? Color.primaryGreen.opacity(0.5) : Color.primaryGreen)
                            .cornerRadius(18)
                        }
                        .disabled(viewModel.isSaving)
                        .padding(18)
                        .background(editProfileBackground)
                        .shadow(color: Color.black.opacity(0.08), radius: 10, x: 0, y: -2)
                    }
                }
            }
            .navigationTitle("Edit Profile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: { handleBackClick() }) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundColor(.textPrimary)
                    }
                }
            }
            .confirmationDialog("Select Photo", isPresented: $showImageSourceSheet, titleVisibility: .visible) {
                Button("Choose from Gallery") {
                    if #available(iOS 16.0, *) {
                        showImagePicker = true
                    } else {
                        imagePickerSourceType = .photoLibrary
                        showCamera = true
                    }
                }
                Button("Take Photo") {
                    imagePickerSourceType = .camera
                    showCamera = true
                }
                Button("Cancel", role: .cancel) {}
            }
            .sheet(isPresented: $showCamera) {
                ImagePicker(sourceType: imagePickerSourceType) { image in
                    selectedImage = image
                }
            }
        }
        .alert(isPresented: $showAlert) {
            Alert(
                title: Text(isAlertSuccess ? "Success" : "Error"),
                message: Text(alertMessage),
                dismissButton: .default(Text("OK"))
            )
        }
        .alert("Discard Changes?", isPresented: $showUnsavedChangesDialog) {
            Button("Discard", role: .destructive) {
                onBackClick()
            }
            Button("Cancel", role: .cancel) {
                // Do nothing, stay on screen
            }
        } message: {
            Text("You have unsaved changes. Are you sure you want to go back?")
        }
    }
       
}

struct ProfileImageSection: View {
    let profileImageUrl: String?
    let selectedImage: UIImage?
    let userName: String
    let onImageClick: () -> Void
    
    var body: some View {
        VStack(spacing: 16) {
            ZStack {
                // Gradient border circle
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [.primaryGreen, .accentYellow],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    .frame(width: 126, height: 126)
                
                if let selectedImage = selectedImage {
                    // Show selected image from picker
                    Image(uiImage: selectedImage)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(width: 120, height: 120)
                        .clipShape(Circle())
                } else if let urlString = profileImageUrl, let url = URL(string: urlString) {
                    AsyncImage(url: url) { image in
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Text(userName.uppercased())
                            .font(.system(size: 45, weight: .semibold))
                            .foregroundColor(.white)
                    }
                    .frame(width: 120, height: 120)
                    .clipShape(Circle())
                } else {
                    Circle()
                        .fill(Color.white)
                        .frame(width: 120, height: 120)
                        .overlay(
                            Text(userName.uppercased())
                                .font(.system(size: 45, weight: .semibold))
                                .foregroundColor(.white)
                        )
                }
                
                // Camera icon button
                Button(action: onImageClick) {
                    Image(systemName: "camera.fill")
                        .font(.system(size: 12))
                        .foregroundColor(.white)
                        .frame(width: 45, height: 45)
                        .background(Color.primaryGreen)
                        .clipShape(Circle())
                        .shadow(color: Color.primaryGreen.opacity(0.3), radius: 8, x: 0, y: 2)
                }
                .offset(x: 40, y: 40)
            }
            
            Button(action: onImageClick) {
                Text("Change photo")
                    .font(.system(size: 15.75, weight: .semibold))
                    .foregroundColor(.primaryGreen)
            }
        }
    }
}

struct BasicDetailsSection: View {
    @Binding var name: String
    var username: String
    @Binding var bio: String
    @Binding var role: UserRole
    var onRoleChange: ((UserRole) -> Void)?
    
    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Basic Details")
                .font(.system(size: 16.875, weight: .bold))
                .foregroundColor(.textPrimary)
            
            // Name Field
            VStack(alignment: .leading, spacing: 9) {
                Text("Name")
                    .font(.system(size: 14.625, weight: .semibold))
                    .foregroundColor(.textSecondary)
                
                TextField("", text: $name)
                    .font(.system(size: 16.875))
                    .foregroundColor(.textPrimary.opacity(0.5))
                    .padding(.horizontal, 18)
                    .padding(.vertical, 13.5)
                    .background(inputBackground)
                    .cornerRadius(22)
                    .overlay(
                        RoundedRectangle(cornerRadius: 22)
                            .stroke(Color.black.opacity(0.1), lineWidth: 1.18)
                    )
            }
            
            // Username Field (Read-only, auto-generated)
            VStack(alignment: .leading, spacing: 9) {
                HStack {
                    Text("Username")
                        .font(.system(size: 14.625, weight: .semibold))
                        .foregroundColor(.textSecondary)
                    Spacer()
                    HStack(spacing: 4) {
                        Image(systemName: "info.circle")
                            .font(.system(size: 12))
                            .foregroundColor(.textSecondary)
                        Text("Auto-generated")
                            .font(.system(size: 11, weight: .regular))
                            .italic()
                            .foregroundColor(.textSecondary)
                    }
                }
                
                Text("@\(username)")
                    .font(.system(size: 16.875))
                    .foregroundColor(.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 13.5)
                    .background(disabledInputBackground)
                    .cornerRadius(22)
                    .overlay(
                        RoundedRectangle(cornerRadius: 22)
                            .stroke(Color.black.opacity(0.05), lineWidth: 1.18)
                    )
            }
            
            // Bio Field
            VStack(alignment: .leading, spacing: 9) {
                HStack {
                    Text("Bio")
                        .font(.system(size: 14.625, weight: .semibold))
                        .foregroundColor(.textSecondary)
                    Spacer()
                    Text("\(bio.count)/150")
                        .font(.system(size: 13.5))
                        .foregroundColor(.textSecondary)
                }
                
                TextEditor(text: Binding(
                    get: { bio },
                    set: { newValue in
                        if newValue.count <= 150 {
                            bio = newValue
                        }
                    }
                ))
                .font(.system(size: 16.875))
                .foregroundColor(.textPrimary.opacity(0.5))
                .frame(height: 80)
                .padding(.horizontal, 14)
                .padding(.vertical, 9.5)
                .background(inputBackground)
                .cornerRadius(22)
                .overlay(
                    RoundedRectangle(cornerRadius: 22)
                        .stroke(Color.black.opacity(0.1), lineWidth: 1.18)
                )
                .scrollContentBackground(.hidden)
            }
            
            // Role Field (Editable Picker)
            VStack(alignment: .leading, spacing: 9) {
                Text("Role")
                    .font(.system(size: 14.625, weight: .semibold))
                    .foregroundColor(.textSecondary)
                
                Menu {
                    ForEach([UserRole.farmer, UserRole.expert, UserRole.agripreneur, UserRole.inputSeller, UserRole.agriLover], id: \.self) { roleOption in
                        Button(action: {
                            role = roleOption
                            onRoleChange?(roleOption)
                        }) {
                            HStack {
                                Text(roleLabel(roleOption))
                                if role == roleOption {
                                    Image(systemName: "checkmark")
                                }
                            }
                        }
                    }
                } label: {
                    HStack {
                        Text(roleLabel(role))
                            .font(.system(size: 16.875))
                            .foregroundColor(.textPrimary)
                        Spacer()
                        Image(systemName: "chevron.down")
                            .font(.system(size: 12))
                            .foregroundColor(.textSecondary)
                    }
                    .padding(.horizontal, 18)
                    .padding(.vertical, 13.5)
                    .background(inputBackground)
                    .cornerRadius(22)
                    .overlay(
                        RoundedRectangle(cornerRadius: 22)
                            .stroke(Color.black.opacity(0.1), lineWidth: 1.18)
                    )
                }
            }
        }
        .padding(22.5)
        .background(cardBackground)
        .cornerRadius(18)
        .shadow(color: Color.black.opacity(0.06), radius: 8, x: 0, y: 2)
    }
}

struct LocationSection: View {
    @ObservedObject var viewModel: EditProfileViewModel
    @Binding var showStatePicker: Bool
    @Binding var showDistrictPicker: Bool
    
    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 9) {
                Image(systemName: "location.fill")
                    .font(.system(size: 22.5))
                    .foregroundColor(.textPrimary)
                Text("Location")
                    .font(.system(size: 16.875, weight: .bold))
                    .foregroundColor(.textPrimary)
            }
            
            // Use Current Location Button
            if let error = viewModel.locationError {
                Text(error)
                    .font(.system(size: 13.5))
                    .foregroundColor(.red)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 8)
            }
            
            Button(action: {
                viewModel.useCurrentLocation()
            }) {
                HStack {
                    if viewModel.isLoadingLocation {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .scaleEffect(0.8)
                        Text("Detecting location...")
                            .font(.system(size: 16.875, weight: .semibold))
                            .foregroundColor(.white)
                    } else {
                        Image(systemName: "location.fill")
                            .font(.system(size: 20))
                        Text("Use Current Location")
                            .font(.system(size: 16.875, weight: .semibold))
                            .foregroundColor(.white)
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .background(Color.primaryGreen)
                .cornerRadius(22)
            }
            .disabled(viewModel.isLoadingLocation)
            
            // State Picker
            VStack(alignment: .leading, spacing: 9) {
                Text("State")
                    .font(.system(size: 14.625, weight: .semibold))
                    .foregroundColor(.textSecondary)
                
                Button(action: { 
                    if !viewModel.isLoadingStates {
                        showStatePicker = true 
                    }
                }) {
                    HStack {
                        if viewModel.isLoadingStates {
                            Text("Loading states...")
                                .font(.system(size: 16.875))
                                .foregroundColor(.textPrimary.opacity(0.5))
                        } else {
                            Text(viewModel.selectedState ?? "Select State")
                                .font(.system(size: 16.875))
                                .foregroundColor(viewModel.selectedState != nil ? .textPrimary : .textPrimary.opacity(0.5))
                        }
                        Spacer()
                        if viewModel.isLoadingStates {
                            ProgressView()
                                .scaleEffect(0.7)
                        } else {
                            Image(systemName: "chevron.down")
                                .font(.system(size: 12))
                                .foregroundColor(.textSecondary)
                        }
                    }
                    .padding(.horizontal, 18)
                    .padding(.vertical, 13.5)
                    .background(inputBackground)
                    .cornerRadius(22)
                    .overlay(
                        RoundedRectangle(cornerRadius: 22)
                            .stroke(Color.black.opacity(0.1), lineWidth: 1.18)
                    )
                }
                .disabled(viewModel.isLoadingStates)
                .sheet(isPresented: $showStatePicker) {
                    StatePickerView(
                        states: viewModel.states,
                        selectedState: viewModel.selectedState,
                        onStateSelected: { state in
                            viewModel.onStateSelected(state)
                        },
                        isPresented: $showStatePicker
                    )
                }
            }
            
            // District Picker
            VStack(alignment: .leading, spacing: 9) {
                Text("District")
                    .font(.system(size: 14.625, weight: .semibold))
                    .foregroundColor(.textSecondary)
                
                Button(action: { 
                    if viewModel.selectedState != nil && !viewModel.isLoadingDistricts {
                        showDistrictPicker = true 
                    }
                }) {
                    HStack {
                        if viewModel.selectedState == nil {
                            Text("Select state first")
                                .font(.system(size: 16.875))
                                .foregroundColor(.textPrimary.opacity(0.5))
                        } else if viewModel.isLoadingDistricts {
                            Text("Loading districts...")
                                .font(.system(size: 16.875))
                                .foregroundColor(.textPrimary.opacity(0.5))
                        } else {
                            Text(viewModel.selectedDistrict ?? "Select District")
                                .font(.system(size: 16.875))
                                .foregroundColor(viewModel.selectedDistrict != nil ? .textPrimary : .textPrimary.opacity(0.5))
                        }
                        Spacer()
                        if viewModel.isLoadingDistricts {
                            ProgressView()
                                .scaleEffect(0.7)
                        } else {
                            Image(systemName: "chevron.down")
                                .font(.system(size: 12))
                                .foregroundColor(viewModel.selectedState != nil ? .textSecondary : .textSecondary.opacity(0.3))
                        }
                    }
                    .padding(.horizontal, 18)
                    .padding(.vertical, 13.5)
                    .background(viewModel.selectedState != nil ? inputBackground : disabledInputBackground)
                    .cornerRadius(22)
                    .overlay(
                        RoundedRectangle(cornerRadius: 22)
                            .stroke(Color.black.opacity(viewModel.selectedState != nil ? 0.1 : 0.05), lineWidth: 1.18)
                    )
                }
                .disabled(viewModel.selectedState == nil || viewModel.isLoadingDistricts)
                .sheet(isPresented: $showDistrictPicker) {
                    DistrictPickerView(
                        districts: viewModel.districts,
                        selectedDistrict: viewModel.selectedDistrict,
                        onDistrictSelected: { district in
                            viewModel.onDistrictSelected(district)
                        },
                        isPresented: $showDistrictPicker
                    )
                }
            }
        }
        .padding(22.5)
        .background(cardBackground)
        .cornerRadius(18)
        .shadow(color: Color.black.opacity(0.06), radius: 8, x: 0, y: 2)
    }
}

struct LocationSectionWithVillage: View {
    @ObservedObject var viewModel: EditProfileViewModel
    @Binding var showStatePicker: Bool
    @Binding var showDistrictPicker: Bool
    
    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 9) {
                Image(systemName: "location.fill")
                    .font(.system(size: 22.5))
                    .foregroundColor(.textPrimary)
                Text("Location")
                    .font(.system(size: 16.875, weight: .bold))
                    .foregroundColor(.textPrimary)
            }
            
            // Use Current Location Button
            if let error = viewModel.locationError {
                Text(error)
                    .font(.system(size: 13.5))
                    .foregroundColor(.red)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 8)
            }
            
            Button(action: {
                viewModel.useCurrentLocation()
            }) {
                HStack {
                    if viewModel.isLoadingLocation {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .scaleEffect(0.8)
                        Text("Detecting location...")
                            .font(.system(size: 16.875, weight: .semibold))
                            .foregroundColor(.white)
                    } else {
                        Image(systemName: "location.fill")
                            .font(.system(size: 20))
                        Text("Use Current Location")
                            .font(.system(size: 16.875, weight: .semibold))
                            .foregroundColor(.white)
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .background(Color.primaryGreen)
                .cornerRadius(22)
            }
            .disabled(viewModel.isLoadingLocation)
            
            // State Picker
            VStack(alignment: .leading, spacing: 9) {
                Text("State")
                    .font(.system(size: 14.625, weight: .semibold))
                    .foregroundColor(.textSecondary)
                
                Button(action: { 
                    if !viewModel.isLoadingStates {
                        showStatePicker = true 
                    }
                }) {
                    HStack {
                        if viewModel.isLoadingStates {
                            Text("Loading states...")
                                .font(.system(size: 16.875))
                                .foregroundColor(.textPrimary.opacity(0.5))
                        } else {
                            Text(viewModel.selectedState ?? "Select State")
                                .font(.system(size: 16.875))
                                .foregroundColor(viewModel.selectedState != nil ? .textPrimary : .textPrimary.opacity(0.5))
                        }
                        Spacer()
                        if viewModel.isLoadingStates {
                            ProgressView()
                                .scaleEffect(0.7)
                        } else {
                            Image(systemName: "chevron.down")
                                .font(.system(size: 12))
                                .foregroundColor(.textSecondary)
                        }
                    }
                    .padding(.horizontal, 18)
                    .padding(.vertical, 13.5)
                    .background(inputBackground)
                    .cornerRadius(22)
                    .overlay(
                        RoundedRectangle(cornerRadius: 22)
                            .stroke(Color.black.opacity(0.1), lineWidth: 1.18)
                    )
                }
                .disabled(viewModel.isLoadingStates)
                .sheet(isPresented: $showStatePicker) {
                    StatePickerView(
                        states: viewModel.states,
                        selectedState: viewModel.selectedState,
                        onStateSelected: { state in
                            viewModel.onStateSelected(state)
                        },
                        isPresented: $showStatePicker
                    )
                }
            }
            
            // District Picker
            VStack(alignment: .leading, spacing: 9) {
                Text("District")
                    .font(.system(size: 14.625, weight: .semibold))
                    .foregroundColor(.textSecondary)
                
                Button(action: { 
                    if viewModel.selectedState != nil && !viewModel.isLoadingDistricts {
                        showDistrictPicker = true 
                    }
                }) {
                    HStack {
                        if viewModel.selectedState == nil {
                            Text("Select state first")
                                .font(.system(size: 16.875))
                                .foregroundColor(.textPrimary.opacity(0.5))
                        } else if viewModel.isLoadingDistricts {
                            Text("Loading districts...")
                                .font(.system(size: 16.875))
                                .foregroundColor(.textPrimary.opacity(0.5))
                        } else {
                            Text(viewModel.selectedDistrict ?? "Select District")
                                .font(.system(size: 16.875))
                                .foregroundColor(viewModel.selectedDistrict != nil ? .textPrimary : .textPrimary.opacity(0.5))
                        }
                        Spacer()
                        if viewModel.isLoadingDistricts {
                            ProgressView()
                                .scaleEffect(0.7)
                        } else {
                            Image(systemName: "chevron.down")
                                .font(.system(size: 12))
                                .foregroundColor(viewModel.selectedState != nil ? .textSecondary : .textSecondary.opacity(0.3))
                        }
                    }
                    .padding(.horizontal, 18)
                    .padding(.vertical, 13.5)
                    .background(viewModel.selectedState != nil ? inputBackground : disabledInputBackground)
                    .cornerRadius(22)
                    .overlay(
                        RoundedRectangle(cornerRadius: 22)
                            .stroke(Color.black.opacity(viewModel.selectedState != nil ? 0.1 : 0.05), lineWidth: 1.18)
                    )
                }
                .disabled(viewModel.selectedState == nil || viewModel.isLoadingDistricts)
                .sheet(isPresented: $showDistrictPicker) {
                    DistrictPickerView(
                        districts: viewModel.districts,
                        selectedDistrict: viewModel.selectedDistrict,
                        onDistrictSelected: { district in
                            viewModel.onDistrictSelected(district)
                        },
                        isPresented: $showDistrictPicker
                    )
                }
            }
            
            // Village TextField (Optional)
            VStack(alignment: .leading, spacing: 9) {
                HStack(spacing: 4) {
                    Text("Village")
                        .font(.system(size: 14.625, weight: .semibold))
                        .foregroundColor(.textSecondary)
                    Text("(Optional)")
                        .font(.system(size: 13.5))
                        .foregroundColor(.textSecondary.opacity(0.6))
                        .italic()
                }
                
                TextField("Enter village name", text: Binding(
                    get: { viewModel.village },
                    set: { viewModel.updateVillage($0) }
                ))
                .font(.system(size: 16.875))
                .foregroundColor(.textPrimary)
                .padding(.horizontal, 18)
                .padding(.vertical, 13.5)
                .background(inputBackground)
                .cornerRadius(22)
                .overlay(
                    RoundedRectangle(cornerRadius: 22)
                        .stroke(Color.black.opacity(0.1), lineWidth: 1.18)
                )
            }
        }
        .padding(22.5)
        .background(cardBackground)
        .cornerRadius(18)
        .shadow(color: Color.black.opacity(0.06), radius: 8, x: 0, y: 2)
    }
}

struct CropsSection: View {
    @ObservedObject var viewModel: EditProfileViewModel
    @Binding var selectedCrops: Set<String>
    var onCropToggle: (String) -> Void = { _ in }
    @State private var searchText: String = ""
    
    // Filter crops based on search text
    private var filteredCrops: [String] {
        let unselectedCrops = viewModel.allCrops.filter { !selectedCrops.contains($0) }
        if searchText.isEmpty {
            return unselectedCrops
        } else {
            return unselectedCrops.filter { $0.localizedCaseInsensitiveContains(searchText) }
        }
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Crops You Grow")
                .font(.system(size: 16.875, weight: .bold))
                .foregroundColor(.textPrimary)
            
            // Search Bar
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.textSecondary)
                    .font(.system(size: 16))
                
                TextField("Search crops...", text: $searchText)
                    .font(.system(size: 16.875))
                    .foregroundColor(.textPrimary)
                    .disabled(viewModel.isLoadingCrops)
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 13.5)
            .background(viewModel.isLoadingCrops ? disabledInputBackground : inputBackground)
            .cornerRadius(22)
            .overlay(
                RoundedRectangle(cornerRadius: 22)
                    .stroke(Color.black.opacity(0.1), lineWidth: 1.18)
            )
            .overlay(
                HStack {
                    Spacer()
                    if !searchText.isEmpty {
                        Button(action: { searchText = "" }) {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(.textSecondary)
                                .font(.system(size: 16))
                        }
                        .padding(.trailing, 8)
                    }
                }
            )
            
            // Selected Crops Section
            if !selectedCrops.isEmpty {
                VStack(alignment: .leading, spacing: 9) {
                    Text("Selected Crops")
                        .font(.system(size: 14.625, weight: .semibold))
                        .foregroundColor(.textSecondary)
                    
                    FlowLayout(spacing: 8) {
                        ForEach(Array(selectedCrops).sorted(), id: \.self) { crop in
                            CropChip(
                                crop: crop,
                                isSelected: true,
                                onClick: {
                                    onCropToggle(crop)
                                }
                            )
                        }
                    }
                }
            }
            
            // Search Results Section
            if viewModel.isLoadingCrops {
                HStack {
                    Spacer()
                    ProgressView()
                        .scaleEffect(0.8)
                    Spacer()
                }
                .padding(.vertical, 16)
            } else if searchText.isEmpty && selectedCrops.isEmpty && !viewModel.allCrops.isEmpty {
                // Show all crops when no search and nothing selected
                FlowLayout(spacing: 8) {
                    ForEach(viewModel.allCrops, id: \.self) { crop in
                        CropChip(
                            crop: crop,
                            isSelected: false,
                            onClick: {
                                onCropToggle(crop)
                            }
                        )
                    }
                }
            } else if !filteredCrops.isEmpty {
                VStack(alignment: .leading, spacing: 9) {
                    if !searchText.isEmpty {
                        Text("Search Results")
                            .font(.system(size: 14.625, weight: .semibold))
                            .foregroundColor(.textSecondary)
                    }
                    
                    FlowLayout(spacing: 8) {
                        ForEach(filteredCrops, id: \.self) { crop in
                            CropChip(
                                crop: crop,
                                isSelected: false,
                                onClick: {
                                    onCropToggle(crop)
                                }
                            )
                        }
                    }
                }
            } else if !searchText.isEmpty && filteredCrops.isEmpty {
                Text("No crops found")
                    .font(.system(size: 14))
                    .foregroundColor(.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.vertical, 16)
            }
        }
        .padding(22.5)
        .background(cardBackground)
        .cornerRadius(18)
        .shadow(color: Color.black.opacity(0.06), radius: 8, x: 0, y: 2)
    }
}

struct CropChip: View {
    let crop: String
    let isSelected: Bool
    let onClick: () -> Void
    
    var body: some View {
        Button(action: onClick) {
            HStack(spacing: 8) {
                Text(crop)
                    .font(.system(size: 14.625, weight: .semibold))
                    .foregroundColor(isSelected ? .white : .textSecondary)
                
                if isSelected {
                    Image(systemName: "xmark")
                        .font(.system(size: 10))
                        .foregroundColor(.white)
                }
            }
            .padding(.horizontal, 19)
            .padding(.vertical, 10)
            .background(isSelected ? Color.primaryGreen : inputBackground)
            .cornerRadius(39602500) // Very rounded
        }
    }
}

struct StatePickerView: View {
    let states: [String]
    let selectedState: String?
    let onStateSelected: (String) -> Void
    @Binding var isPresented: Bool
    
    @State private var searchText = ""
    
    var filteredStates: [String] {
        if searchText.isEmpty {
            return states
        }
        return states.filter { $0.localizedCaseInsensitiveContains(searchText) }
    }
    
    var body: some View {
        NavigationView {
            List(filteredStates, id: \.self) { state in
                Button(action: {
                    onStateSelected(state)
                    isPresented = false
                }) {
                    HStack {
                        Text(state)
                            .foregroundColor(.textPrimary)
                        Spacer()
                        if state == selectedState {
                            Image(systemName: "checkmark")
                                .foregroundColor(.primaryGreen)
                        }
                    }
                }
            }
            .searchable(text: $searchText, prompt: "Search states")
            .navigationTitle("Select State")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Cancel") {
                        isPresented = false
                    }
                }
            }
        }
    }
}

struct DistrictPickerView: View {
    let districts: [String]
    let selectedDistrict: String?
    let onDistrictSelected: (String) -> Void
    @Binding var isPresented: Bool
    
    @State private var searchText = ""
    
    var filteredDistricts: [String] {
        if searchText.isEmpty {
            return districts
        }
        return districts.filter { $0.localizedCaseInsensitiveContains(searchText) }
    }
    
    var body: some View {
        NavigationView {
            List(filteredDistricts, id: \.self) { district in
                Button(action: {
                    onDistrictSelected(district)
                    isPresented = false
                }) {
                    HStack {
                        Text(district)
                            .foregroundColor(.textPrimary)
                        Spacer()
                        if district == selectedDistrict {
                            Image(systemName: "checkmark")
                                .foregroundColor(.primaryGreen)
                        }
                    }
                }
            }
            .searchable(text: $searchText, prompt: "Search districts")
            .navigationTitle("Select District")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Cancel") {
                        isPresented = false
                    }
                }
            }
        }
    }
}

// FlowLayout for wrapping crop chips
struct FlowLayout: Layout {
    var spacing: CGFloat = 8
    
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let result = FlowResult(
            in: proposal.replacingUnspecifiedDimensions().width,
            subviews: subviews,
            spacing: spacing
        )
        return result.size
    }
    
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let result = FlowResult(
            in: bounds.width,
            subviews: subviews,
            spacing: spacing
        )
        for (index, subview) in subviews.enumerated() {
            subview.place(at: CGPoint(x: bounds.minX + result.frames[index].minX, y: bounds.minY + result.frames[index].minY), proposal: .unspecified)
        }
    }
    
    struct FlowResult {
        var size: CGSize = .zero
        var frames: [CGRect] = []
        
        init(in maxWidth: CGFloat, subviews: Subviews, spacing: CGFloat) {
            var currentX: CGFloat = 0
            var currentY: CGFloat = 0
            var lineHeight: CGFloat = 0
            
            for subview in subviews {
                let size = subview.sizeThatFits(.unspecified)
                
                if currentX + size.width > maxWidth && currentX > 0 {
                    currentX = 0
                    currentY += lineHeight + spacing
                    lineHeight = 0
                }
                
                frames.append(CGRect(x: currentX, y: currentY, width: size.width, height: size.height))
                lineHeight = max(lineHeight, size.height)
                currentX += size.width + spacing
            }
            
            self.size = CGSize(width: maxWidth, height: currentY + lineHeight)
        }
    }
}

private func roleLabel(_ role: UserRole) -> String {
    switch role {
    case .farmer: return "Farmer"
    case .expert: return "Expert"
    case .agripreneur: return "Agripreneur"
    case .inputSeller: return "Input Seller"
    case .agriLover: return "Agri Lover"
    default: return "Farmer"
    }
}

// UIImagePickerController wrapper for camera and photo library
struct ImagePicker: UIViewControllerRepresentable {
    let sourceType: UIImagePickerController.SourceType
    let onImagePicked: (UIImage) -> Void
    
    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = sourceType
        picker.delegate = context.coordinator
        picker.allowsEditing = true
        return picker
    }
    
    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}
    
    func makeCoordinator() -> Coordinator {
        Coordinator(onImagePicked: onImagePicked)
    }
    
    class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let onImagePicked: (UIImage) -> Void
        
        init(onImagePicked: @escaping (UIImage) -> Void) {
            self.onImagePicked = onImagePicked
        }
        
        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
            if let editedImage = info[.editedImage] as? UIImage {
                onImagePicked(editedImage)
            } else if let originalImage = info[.originalImage] as? UIImage {
                onImagePicked(originalImage)
            }
            picker.dismiss(animated: true)
        }
        
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            picker.dismiss(animated: true)
        }
    }
}

#Preview {
    EditProfileView()
}
