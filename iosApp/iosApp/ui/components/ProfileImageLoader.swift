import SwiftUI
import Kingfisher
import Shared

private var profileImageCache: [String: String?] = [:]

/// Profile/avatar image loader. Uses Kingfisher for memory+disk cache—no reload when scrolling back.
struct ProfileImageLoader: View {
    let authorId: String
    let authorName: String
    let authorProfileImageUrl: String?
    var size: CGFloat = 50
    
    @State private var displayUrl: String?
    
    private var placeholder: some View {
        Text(String(authorName.prefix(1)).uppercased())
            .font(.system(size: size * 0.4, weight: .semibold))
            .foregroundColor(.white)
    }
    
    var body: some View {
        ZStack {
            Circle()
                .fill(LinearGradient(
                    colors: [.primaryGreen, .accentYellow],
                    startPoint: .top,
                    endPoint: .bottom
                ))
                .frame(width: size, height: size)
            
            if let urlString = displayUrl, !urlString.isEmpty, let url = URL(string: ensureHttps(urlString)) {
                KFImage(url)
                    .placeholder { placeholder }
                    .loadDiskFileSynchronously()
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: size - 4, height: size - 4)
                    .clipShape(Circle())
            } else {
                placeholder
            }
        }
        .task(id: authorId) {
            if let initial = authorProfileImageUrl, !initial.isEmpty {
                displayUrl = initial
                profileImageCache[authorId] = initial
                return
            }
            if let cached = profileImageCache[authorId], let c = cached, !c.isEmpty {
                displayUrl = c
                return
            }
            do {
                let prefs = IOSPreferencesRepository()
                let authRepo = IOSAuthRepository(preferencesRepository: prefs)
                let userRepo = FirestoreUserRepository(authRepository: authRepo)
                let user = try await userRepo.getUser(userId: authorId)
                let url = user?.profileImageUrl.flatMap { $0.isEmpty ? nil : $0 }
                profileImageCache[authorId] = url
                await MainActor.run {
                    displayUrl = url
                }
            } catch {
                await MainActor.run {
                    displayUrl = nil
                }
                profileImageCache[authorId] = nil
            }
        }
        .onAppear {
            if let initial = authorProfileImageUrl, !initial.isEmpty {
                displayUrl = initial
            } else if let cached = profileImageCache[authorId], let c = cached, !c.isEmpty {
                displayUrl = c
            }
        }
    }
}
