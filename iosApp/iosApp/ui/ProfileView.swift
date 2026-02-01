import SwiftUI
import Shared

private let profileBackground = Color(red: 0.984, green: 0.973, blue: 0.941)
private let expertGreen = Color(red: 0.455, green: 0.765, blue: 0.396)

struct ProfileView: View {
    @StateObject private var viewModel = ProfileViewModel()
    @State private var showMenu = false

    var onBackClick: () -> Void = {}
    var onEditProfile: () -> Void = {}
    var onSignOut: () -> Void = {}

    var body: some View {
        ZStack {
            profileBackground.ignoresSafeArea()

            VStack(spacing: 0) {
                // Header
                HStack {
                    Button(action: onBackClick) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundColor(.textPrimary)
                    }
                    Spacer()
                    Text("Profile")
                        .font(.custom("Georgia", size: 20))
                        .fontWeight(.bold)
                        .foregroundColor(.textPrimary)
                    Spacer()
                    Menu {
                        Button(role: .destructive, action: {
                            viewModel.signOut { onSignOut() }
                        }) {
                            Text("Sign out")
                        }
                    } label: {
                        Image(systemName: "ellipsis")
                            .font(.system(size: 20))
                            .foregroundColor(.textPrimary)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(profileBackground)

                if viewModel.isLoading {
                    Spacer()
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .primaryGreen))
                    Spacer()
                } else if let error = viewModel.error {
                    Spacer()
                    VStack(spacing: 16) {
                        Text(error)
                            .foregroundColor(.textSecondary)
                            .multilineTextAlignment(.center)
                        Button("Retry") {
                            viewModel.loadProfile()
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 12)
                        .background(Color.primaryGreen)
                        .cornerRadius(8)
                    }
                    Spacer()
                } else if let user = viewModel.user {
                    ScrollView {
                        ProfileContent(user: user, onEditProfile: onEditProfile)
                    }
                } else {
                    Spacer()
                    Text("No profile found")
                        .foregroundColor(.textSecondary)
                    Spacer()
                }
            }
        }
    }
}

struct ProfileContent: View {
    let user: User
    let onEditProfile: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            VStack(spacing: 16) {
                // Avatar
                ZStack {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [.primaryGreen, .accentYellow],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                        .frame(width: 120, height: 120)

                    if let urlString = user.profileImageUrl, let url = URL(string: urlString) {
                        AsyncImage(url: url) { image in
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                        } placeholder: {
                            Text(String(user.name.prefix(1)).uppercased())
                                .font(.system(size: 48, weight: .semibold))
                                .foregroundColor(.white)
                        }
                        .frame(width: 114, height: 114)
                        .clipShape(Circle())
                    } else {
                        Text(String(user.name.prefix(1)).uppercased())
                            .font(.system(size: 48, weight: .semibold))
                            .foregroundColor(.white)
                    }
                }

                Text(user.name)
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(.textPrimary)
                    .lineLimit(1)

                Text(roleLabel(user.role))
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.primaryGreen)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(expertGreen.opacity(0.15))
                    .cornerRadius(16)

                if let loc = user.location {
                    HStack(spacing: 6) {
                        Image(systemName: "location.fill")
                            .font(.system(size: 14))
                            .foregroundColor(.textSecondary)
                        Text([loc.district, loc.state, loc.country].compactMap { $0 }.joined(separator: ", "))
                            .font(.system(size: 14))
                            .foregroundColor(.textSecondary)
                    }
                }
            }
            .frame(maxWidth: .infinity)

            Button(action: onEditProfile) {
                Text("Edit Profile")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(Color.primaryGreen)
                    .cornerRadius(24)
            }

            // Stats
            HStack {
                Spacer()
                StatItem(count: Int(user.postsCount), label: "Posts")
                Spacer()
                StatItem(count: Int(user.followersCount), label: "Followers")
                Spacer()
                StatItem(count: Int(user.followingCount), label: "Following")
                Spacer()
                StatItem(count: 0, label: "Groups")
                Spacer()
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("About")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.textPrimary)

                Text(user.bio ?? "No bio yet.")
                    .font(.system(size: 15))
                    .foregroundColor(.textSecondary)
                    .lineSpacing(4)
            }

            VStack(alignment: .leading, spacing: 12) {
                Text("Posts")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.textPrimary)

                VStack(spacing: 8) {
                    HStack(spacing: 8) {
                        ForEach(0..<3, id: \.self) { _ in
                            RoundedRectangle(cornerRadius: 12)
                                .fill(Color(red: 0.898, green: 0.902, blue: 0.859))
                                .aspectRatio(1, contentMode: .fit)
                        }
                    }
                    HStack(spacing: 8) {
                        ForEach(0..<3, id: \.self) { _ in
                            RoundedRectangle(cornerRadius: 12)
                                .fill(Color(red: 0.898, green: 0.902, blue: 0.859))
                                .aspectRatio(1, contentMode: .fit)
                        }
                    }
                }
            }

            Spacer(minLength: 32)
        }
        .padding(24)
    }
}

struct StatItem: View {
    let count: Int
    let label: String

    var body: some View {
        VStack(spacing: 4) {
            Text("\(count)")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.textPrimary)
            Text(label)
                .font(.system(size: 12))
                .foregroundColor(.textSecondary)
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

#Preview {
    ProfileView()
}
