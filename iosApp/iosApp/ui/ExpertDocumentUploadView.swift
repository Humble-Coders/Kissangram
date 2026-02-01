import SwiftUI
import UniformTypeIdentifiers
import FirebaseAuth

struct ExpertDocumentUploadView: View {
    @StateObject private var viewModel = ExpertDocumentUploadViewModel()
    @State private var showDocumentPicker = false
    let onComplete: () -> Void
    let onSkip: () -> Void
    
    var body: some View {
        GeometryReader { geometry in
            let screenWidth = geometry.size.width
            // Responsive scaling factors based on screen width (360 as baseline)
            let scaleFactor = min(screenWidth / 360, 1.3)
            let padding: CGFloat = 27 * scaleFactor
            let spacing: CGFloat = 13.5 * scaleFactor
            
            ZStack {
                Color(hex: 0xF8F9F1)
                    .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Header - Fixed at top
                    VStack(alignment: .leading, spacing: 9 * scaleFactor) {
                        Text("Verify your expertise")
                            .font(.system(size: 31.5 * scaleFactor, weight: .bold))
                            .foregroundColor(Color(hex: 0x1B1B1B))
                            .lineSpacing(0)
                            .lineLimit(2)
                            .minimumScaleFactor(0.6)
                        
                        Text("Upload credentials to get verified and earn trust")
                            .font(.system(size: 17.1 * scaleFactor))
                            .foregroundColor(Color(hex: 0x6B6B6B))
                            .lineLimit(2)
                            .minimumScaleFactor(0.7)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, padding)
                    .padding(.horizontal, padding)
                    
                    Spacer()
                        .frame(height: padding)
                    
                    // Scrollable content
                    ScrollView {
                        VStack(spacing: 0) {
                        
                        // Why Verify Section
                        VStack(alignment: .leading, spacing: spacing) {
                            Text("WHY VERIFY?")
                                .font(.system(size: 15.75 * scaleFactor, weight: .semibold))
                                .foregroundColor(Color(hex: 0x2D6A4F))
                                .tracking(0.7875 * scaleFactor)
                                .minimumScaleFactor(0.7)
                            
                            VStack(spacing: spacing) {
                                BenefitCard(
                                    iconName: "checkmark.seal.fill",
                                    title: "Verified Badge",
                                    description: "Stand out with a blue checkmark",
                                    scaleFactor: scaleFactor
                                )
                                
                                BenefitCard(
                                    iconName: "arrow.up.right.square.fill",
                                    title: "Higher Visibility",
                                    description: "Get featured in expert listings",
                                    scaleFactor: scaleFactor
                                )
                                
                                BenefitCard(
                                    iconName: "shield.fill",
                                    title: "Build Trust",
                                    description: "Farmers connect with verified experts",
                                    scaleFactor: scaleFactor
                                )
                            }
                        }
                        .padding(.horizontal, padding)
                        
                        Spacer()
                            .frame(height: padding)
                    
                        // Upload Documents Section
                        VStack(alignment: .leading, spacing: spacing) {
                            Text("UPLOAD DOCUMENTS")
                                .font(.system(size: 15.75 * scaleFactor, weight: .semibold))
                                .foregroundColor(Color(hex: 0x2D6A4F))
                                .tracking(0.7875 * scaleFactor)
                                .minimumScaleFactor(0.7)
                            
                            // Upload Button
                            Button(action: {
                                showDocumentPicker = true
                            }) {
                                VStack(spacing: spacing) {
                                    Circle()
                                        .fill(Color(hex: 0x2D6A4F))
                                        .frame(width: 63 * scaleFactor, height: 63 * scaleFactor)
                                        .overlay(
                                            Image(systemName: "arrow.up")
                                                .foregroundColor(.white)
                                                .font(.system(size: 31.5 * scaleFactor))
                                        )
                                    
                                    Text("Tap to upload credentials")
                                        .font(.system(size: 16.875 * scaleFactor, weight: .semibold))
                                        .foregroundColor(Color(hex: 0x1B1B1B))
                                        .lineLimit(1)
                                        .minimumScaleFactor(0.7)
                                    
                                    Text("Certificates, degrees, or official ID")
                                        .font(.system(size: 14.625 * scaleFactor, weight: .semibold))
                                        .foregroundColor(Color(hex: 0x6B6B6B))
                                        .lineLimit(2)
                                        .minimumScaleFactor(0.7)
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 20 * scaleFactor)
                                .background(Color(hex: 0x2D6A4F).opacity(0.03))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 22 * scaleFactor)
                                        .stroke(Color(hex: 0x2D6A4F), lineWidth: 1.962 * scaleFactor)
                                )
                                .cornerRadius(22 * scaleFactor)
                            }
                            .buttonStyle(PlainButtonStyle())
                            .disabled(viewModel.isUploading)
                            
                            // Upload Progress
                            if viewModel.isUploading {
                                VStack(spacing: 8 * scaleFactor) {
                                    ProgressView(value: viewModel.uploadProgress)
                                        .progressViewStyle(LinearProgressViewStyle(tint: Color(hex: 0x2D6A4F)))
                                    
                                    Text("Uploading... \(Int(viewModel.uploadProgress * 100))%")
                                        .font(.system(size: 14 * scaleFactor))
                                        .foregroundColor(Color(hex: 0x6B6B6B))
                                        .minimumScaleFactor(0.7)
                                }
                                .padding(.top, 8 * scaleFactor)
                            }
                            
                            // Uploaded File Name
                            if let fileName = viewModel.uploadedFileName {
                                HStack {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundColor(Color(hex: 0x2D6A4F))
                                        .font(.system(size: 16 * scaleFactor))
                                    Text(fileName)
                                        .font(.system(size: 14 * scaleFactor))
                                        .foregroundColor(Color(hex: 0x1B1B1B))
                                        .lineLimit(1)
                                        .minimumScaleFactor(0.7)
                                    Spacer()
                                }
                                .padding(.top, 8 * scaleFactor)
                            }
                            
                            // Skip Option
                            VStack(spacing: 0) {
                                Text("You can skip this step and verify later")
                                    .font(.system(size: 15.75 * scaleFactor, weight: .semibold))
                                    .foregroundColor(Color(hex: 0x1B1B1B))
                                    .lineLimit(2)
                                    .minimumScaleFactor(0.7)
                                
                                Text("from your profile")
                                    .font(.system(size: 15.75 * scaleFactor, weight: .semibold))
                                    .foregroundColor(Color(hex: 0x1B1B1B))
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.7)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 18.5 * scaleFactor)
                            .background(Color(hex: 0xFFB703).opacity(0.08))
                            .overlay(
                                RoundedRectangle(cornerRadius: 22 * scaleFactor)
                                    .stroke(Color(hex: 0xFFB703).opacity(0.2), lineWidth: 0.654 * scaleFactor)
                            )
                            .cornerRadius(22 * scaleFactor)
                            .padding(.top, spacing)
                        }
                        .padding(.horizontal, padding)
                        }
                    }
                    
                    Spacer()
                        .frame(height: padding)
                    
                    // Buttons
                    VStack(spacing: spacing) {
                            // Complete Setup Button
                            Button(action: {
                                viewModel.completeSetup {
                                    onComplete()
                                }
                            }) {
                                Text("Complete Setup")
                                    .font(.system(size: 20.25 * scaleFactor, weight: .semibold))
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 75 * scaleFactor)
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.7)
                            }
                            .background(Color(hex: 0x2D6A4F))
                            .cornerRadius(18 * scaleFactor)
                            .shadow(color: Color(hex: 0x2D6A4F).opacity(0.3), radius: 4 * scaleFactor, x: 0, y: 4 * scaleFactor)
                            
                            // Skip Button
                            Button(action: {
                                viewModel.skipVerification {
                                    onSkip()
                                }
                            }) {
                                Text("I'll do this later")
                                    .font(.system(size: 16.875 * scaleFactor, weight: .medium))
                                    .foregroundColor(Color(hex: 0x6B6B6B))
                                    .frame(maxWidth: .infinity)
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.7)
                            }
                    }
                    .padding(.horizontal, padding)
                    .padding(.bottom, padding)
                    
                    // Error message
                    if let error = viewModel.error {
                        Text(error)
                            .font(.system(size: 14 * scaleFactor))
                            .foregroundColor(.red)
                            .padding(.horizontal, padding)
                            .padding(.bottom, 9 * scaleFactor)
                            .lineLimit(2)
                            .minimumScaleFactor(0.7)
                    }
                }
            }
        }
        .sheet(isPresented: $showDocumentPicker) {
            DocumentPicker { url in
                if let userId = Auth.auth().currentUser?.uid {
                    Task {
                        await viewModel.uploadDocument(fileURL: url, userId: userId)
                    }
                }
            }
        }
    }
}

struct BenefitCard: View {
    let iconName: String
    let title: String
    let description: String
    let scaleFactor: CGFloat
    
    var body: some View {
        HStack(spacing: 13.5 * scaleFactor) {
            Circle()
                .fill(Color(hex: 0xFFB703).opacity(0.15))
                .frame(width: 45 * scaleFactor, height: 45 * scaleFactor)
                .overlay(
                    Image(systemName: iconName)
                        .foregroundColor(Color(hex: 0xFFB703))
                        .font(.system(size: 22.5 * scaleFactor))
                )
            
            VStack(alignment: .leading, spacing: 4.5 * scaleFactor) {
                Text(title)
                    .font(.system(size: 16.875 * scaleFactor, weight: .semibold))
                    .foregroundColor(Color(hex: 0x1B1B1B))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                
                Text(description)
                    .font(.system(size: 14.625 * scaleFactor))
                    .foregroundColor(Color(hex: 0x6B6B6B))
                    .lineLimit(2)
                    .minimumScaleFactor(0.7)
            }
            
            Spacer()
        }
        .padding(18.5 * scaleFactor)
        .background(Color.white)
        .overlay(
            RoundedRectangle(cornerRadius: 22 * scaleFactor)
                .stroke(Color(hex: 0x2D6A4F).opacity(0.1), lineWidth: 0.654 * scaleFactor)
        )
        .cornerRadius(22 * scaleFactor)
        .shadow(color: Color.black.opacity(0.05), radius: 1 * scaleFactor, x: 0, y: 1 * scaleFactor)
    }
}

struct DocumentPicker: UIViewControllerRepresentable {
    let onDocumentPicked: (URL) -> Void
    
    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [
            .pdf,
            .jpeg,
            .png,
            UTType(filenameExtension: "doc")!,
            UTType(filenameExtension: "docx")!
        ])
        picker.delegate = context.coordinator
        picker.allowsMultipleSelection = false
        return picker
    }
    
    func updateUIViewController(_ uiViewController: UIDocumentPickerViewController, context: Context) {}
    
    func makeCoordinator() -> Coordinator {
        Coordinator(onDocumentPicked: onDocumentPicked)
    }
    
    class Coordinator: NSObject, UIDocumentPickerDelegate {
        let onDocumentPicked: (URL) -> Void
        
        init(onDocumentPicked: @escaping (URL) -> Void) {
            self.onDocumentPicked = onDocumentPicked
        }
        
        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            guard let url = urls.first else { return }
            
            // Request access to the security-scoped resource
            guard url.startAccessingSecurityScopedResource() else {
                print("Failed to access security-scoped resource")
                return
            }
            
            // Copy file to a temporary location that we can access
            let fileManager = FileManager.default
            let tempDirectory = fileManager.temporaryDirectory
            let tempURL = tempDirectory.appendingPathComponent(url.lastPathComponent)
            
            do {
                // Remove file if it already exists
                if fileManager.fileExists(atPath: tempURL.path) {
                    try fileManager.removeItem(at: tempURL)
                }
                
                // Copy file to temp directory
                try fileManager.copyItem(at: url, to: tempURL)
                
                // Stop accessing the security-scoped resource
                url.stopAccessingSecurityScopedResource()
                
                // Use the temp URL for upload
                onDocumentPicked(tempURL)
            } catch {
                url.stopAccessingSecurityScopedResource()
                print("Error copying file: \(error.localizedDescription)")
            }
        }
    }
}
