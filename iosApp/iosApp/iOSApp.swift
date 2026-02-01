import SwiftUI
import FirebaseCore
import FirebaseAuth
import FirebaseStorage
import UserNotifications

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
        FirebaseApp.configure()
        
        // Set up notification center delegate FIRST
        UNUserNotificationCenter.current().delegate = self
        
        // Request notification authorization and register for remote notifications
        // This is REQUIRED for Firebase Phone Auth to use silent push instead of reCAPTCHA
        // Even though silent push doesn't require user authorization, requesting it ensures
        // the notification system is properly initialized and APNs token is generated
        
        // Try to register immediately first (in case authorization was already granted)
        DispatchQueue.main.async {
            application.registerForRemoteNotifications()
            print("📱 Attempting to register for remote notifications...")
        }
        
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            if let error = error {
                print("⚠️ Failed to request notification authorization: \(error.localizedDescription)")
                print("⚠️ Firebase Phone Auth will fall back to reCAPTCHA")
            } else {
                print("✅ Notification authorization granted: \(granted)")
            }
            
            // Register for remote notifications - this MUST be called to get APNs token
            // The APNs token is required for Firebase Phone Auth silent push
            // This works even if user denied authorization (for silent push)
            DispatchQueue.main.async {
                application.registerForRemoteNotifications()
                print("📱 Registered for remote notifications - waiting for APNs token...")
                print("📱 If you don't see '🔑 APNs Token received' next, check:")
                print("   1. Push Notifications capability is enabled in Xcode (Signing & Capabilities)")
                print("   2. Your provisioning profile includes Push Notifications")
                print("   3. You're testing on a physical device (simulators don't support push)")
            }
        }
        
        return true
    }
    
    // Handle APNs token registration - required for Phone Auth to use silent push
    func application(_ application: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        // Convert token to hex string for logging
        let tokenString = deviceToken.map { String(format: "%02.2hhx", $0) }.joined()
        print("🔑 APNs Token received: \(tokenString)")
        
        // Determine APNs token type: production vs sandbox (development)
        // For development builds, use .sandbox; for production, use .prod
        // Using .unknown works but determining type is better for reliability
        #if DEBUG
        let tokenType: AuthAPNSTokenType = .sandbox
        #else
        let tokenType: AuthAPNSTokenType = .prod
        #endif
        
        // Firebase Auth automatically handles this if swizzling is enabled
        // But we set it explicitly to ensure it works
        Auth.auth().setAPNSToken(deviceToken, type: tokenType)
        print("✅ APNs token set in Firebase Auth with type: \(tokenType == .sandbox ? "sandbox" : "production")")
        
        // Verify token is actually set
        if let setToken = Auth.auth().apnsToken {
            print("✅ Verified: Firebase Auth has APNs token")
        } else {
            print("❌ ERROR: Firebase Auth does NOT have APNs token!")
        }
    }
    
    func application(_ application: UIApplication,
                     didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("❌ ERROR: Failed to register for remote notifications: \(error.localizedDescription)")
        print("❌ This usually means:")
        print("   1. Push Notifications capability is not enabled in Xcode")
        print("   2. Provisioning profile doesn't have Push Notifications")
        print("   3. APNs key/certificate is not uploaded to Firebase Console")
        print("❌ Firebase Phone Auth will fall back to reCAPTCHA")
    }
    
    // Handle remote notifications for Firebase Phone Auth
    func application(_ application: UIApplication,
                     didReceiveRemoteNotification userInfo: [AnyHashable : Any],
                     fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
        if Auth.auth().canHandleNotification(userInfo) {
            completionHandler(.noData)
            return
        }
        completionHandler(.failed)
    }
    
    // Handle URL opening for Firebase Phone Auth reCAPTCHA callback (fallback)
    func application(_ application: UIApplication,
                     open url: URL,
                     options: [UIApplication.OpenURLOptionsKey : Any] = [:]) -> Bool {
        if Auth.auth().canHandle(url) {
            return true
        }
        return false
    }
}

extension AppDelegate: UNUserNotificationCenterDelegate {
    // Handle notifications when app is in foreground
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        // For Firebase Phone Auth silent push, we don't need to show the notification
        completionHandler([])
    }
    
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        completionHandler()
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}