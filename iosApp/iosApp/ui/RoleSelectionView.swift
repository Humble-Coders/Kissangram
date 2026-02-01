import SwiftUI
import Shared

struct RoleSelectionView: View {
    @StateObject private var viewModel = RoleSelectionViewModel()
    let onRoleSelected: (UserRole?) -> Void
    
    var body: some View {
        GeometryReader { geometry in
            let screenWidth = geometry.size.width
            // More conservative scaling to prevent too small text
            let scaleFactor = min(max(screenWidth / 360, 0.9), 1.15)
            let padding: CGFloat = 24
            let spacing: CGFloat = 10
            
            ZStack {
                Color(hex: 0xF8F9F1)
                    .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Header - Fixed at top
                    VStack(alignment: .leading, spacing: 6) {
                        Text("How do you use Kissangram?")
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(Color(hex: 0x1B1B1B))
                            .lineSpacing(0)
                            .lineLimit(2)
                            .minimumScaleFactor(0.8)
                        
                        Text("Choose the option that best describes you")
                            .font(.system(size: 14))
                            .foregroundColor(Color(hex: 0x6B6B6B))
                            .lineLimit(2)
                            .minimumScaleFactor(0.85)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, padding)
                    .padding(.horizontal, padding)
                    
                    Spacer()
                        .frame(height: spacing)
                    
                    // Scrollable content
                    ScrollView {
                        VStack(spacing: spacing) {
                        // Farmer card (primary, large)
                        RoleCard(
                            role: .farmer,
                            isSelected: viewModel.selectedRole == .farmer,
                            isLarge: true,
                            title: "Farmer",
                            description: "I grow crops and share field updates",
                            iconName: "leaf.fill",
                            screenWidth: geometry.size.width
                        ) {
                            viewModel.selectRole(.farmer)
                        }
                        .padding(.horizontal, padding)
                        
                        // Or separator
                        HStack(spacing: 12) {
                            Rectangle()
                                .fill(Color(hex: 0x6B6B6B).opacity(0.2))
                                .frame(height: 1)
                            
                            Text("or")
                                .font(.system(size: 14))
                                .foregroundColor(Color(hex: 0x6B6B6B))
                            
                            Rectangle()
                                .fill(Color(hex: 0x6B6B6B).opacity(0.2))
                                .frame(height: 1)
                        }
                        .padding(.horizontal, padding)
                        
                        // Grid of 4 smaller cards
                        VStack(spacing: 10) {
                            HStack(spacing: 10) {
                                RoleCard(
                                    role: .agripreneur,
                                    isSelected: viewModel.selectedRole == .agripreneur,
                                    isLarge: false,
                                    title: "Agripreneur",
                                    description: nil,
                                    iconName: "arrow.up.right.square.fill",
                                    screenWidth: geometry.size.width
                                ) {
                                    viewModel.selectRole(.agripreneur)
                                }
                                
                                RoleCard(
                                    role: .expert,
                                    isSelected: viewModel.selectedRole == .expert,
                                    isLarge: false,
                                    title: "Expert / Advisor",
                                    description: nil,
                                    iconName: "graduationcap.fill",
                                    screenWidth: geometry.size.width
                                ) {
                                    viewModel.selectRole(.expert)
                                }
                            }
                            
                            HStack(spacing: 10) {
                                RoleCard(
                                    role: .inputSeller,
                                    isSelected: viewModel.selectedRole == .inputSeller,
                                    isLarge: false,
                                    title: "Input Seller",
                                    description: nil,
                                    iconName: "bag.fill",
                                    screenWidth: geometry.size.width
                                ) {
                                    viewModel.selectRole(.inputSeller)
                                }
                                
                                RoleCard(
                                    role: .agriLover,
                                    isSelected: viewModel.selectedRole == .agriLover,
                                    isLarge: false,
                                    title: "Agri Lover",
                                    description: nil,
                                    iconName: "heart.fill",
                                    screenWidth: geometry.size.width
                                ) {
                                    viewModel.selectRole(.agriLover)
                                }
                            }
                        }
                        .padding(.horizontal, padding)
                        
                        Spacer(minLength: spacing)
                        
                        // Next Button
                    Button(action: {
                        viewModel.saveRole(
                            onSuccess: {
                                onRoleSelected(viewModel.selectedRole)
                            },
                            onError: { error in
                                viewModel.error = error
                            }
                        )
                    }) {
                        if viewModel.isLoading {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                .frame(maxWidth: .infinity)
                                .frame(height: 64)
                        } else {
                            Text("Next")
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 64)
                        }
                    }
                    .background(Color(hex: 0x2D6A4F))
                    .cornerRadius(16)
                    .disabled(viewModel.isLoading || viewModel.selectedRole == nil)
                    .opacity((viewModel.isLoading || viewModel.selectedRole == nil) ? 0.5 : 1.0)
                            .padding(.horizontal, padding)
                            .padding(.top, 16)
                            .padding(.bottom, 16)
                        
                        // Error message
                        if let error = viewModel.error {
                            Text(error)
                                .font(.system(size: 13))
                                .foregroundColor(.red)
                                .padding(.horizontal, padding)
                                .padding(.bottom, 8)
                                .lineLimit(2)
                        }
                        }
                    }
                }
            }
        }
    }
}

struct RoleCard: View {
    let role: UserRole
    let isSelected: Bool
    let isLarge: Bool
    let title: String
    let description: String?
    let iconName: String
    let screenWidth: CGFloat
    let onTap: () -> Void
    
    private var scaleFactor: CGFloat {
        min(max(screenWidth / 360, 0.9), 1.15)
    }
    
    private var iconSize: CGFloat {
        isLarge ? 90 : 50
    }
    
    private var iconInnerSize: CGFloat {
        isLarge ? 45 : 25
    }
    
    private var titleSize: CGFloat {
        isLarge ? 26 : 14
    }
    
    private var descriptionSize: CGFloat {
        15
    }
    
    private var padding: CGFloat {
        isLarge ? 28 : 16
    }
    
    private var spacing: CGFloat {
        14
    }
    
    var body: some View {
        Button(action: onTap) {
            VStack(spacing: spacing) {
                // Icon
                Circle()
                    .fill(Color(hex: 0x2D6A4F).opacity(isLarge ? 0.15 : 0.1))
                    .frame(width: iconSize, height: iconSize)
                    .overlay(
                        Image(systemName: iconName)
                            .foregroundColor(Color(hex: 0x2D6A4F))
                            .font(.system(size: iconInnerSize))
                    )
                
                // Title
                Text(title)
                    .font(.system(size: titleSize, weight: .bold))
                    .foregroundColor(Color(hex: 0x1B1B1B))
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .minimumScaleFactor(0.85)
                
                // Description (only for large card)
                if let description = description {
                    Text(description)
                        .font(.system(size: descriptionSize, weight: .semibold))
                        .foregroundColor(Color(hex: 0x6B6B6B))
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                        .minimumScaleFactor(0.9)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(padding)
            .background(Color(hex: 0xF8F9F1))
            .overlay(
                RoundedRectangle(cornerRadius: isLarge ? 22 : 16)
                    .stroke(
                        isSelected ? Color(hex: 0x2D6A4F) : Color(hex: 0x2D6A4F).opacity(isLarge ? 0.15 : 0.12),
                        lineWidth: isLarge ? 2 : 1.5
                    )
            )
            .cornerRadius(isLarge ? 22 : 16)
            .shadow(color: isSelected ? Color(hex: 0x2D6A4F).opacity(0.1) : Color.black.opacity(0.05), radius: isSelected ? 3 : 1, x: 0, y: isSelected ? 2 : 1)
        }
        .buttonStyle(PlainButtonStyle())
    }
}
