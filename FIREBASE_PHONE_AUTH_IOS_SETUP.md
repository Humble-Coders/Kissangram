# Firebase Phone Authentication on iOS - Complete Setup Guide

## Overview
This guide documents how to configure Firebase Phone Authentication to use **silent push notifications (APNs)** instead of reCAPTCHA on iOS, providing a seamless user experience.

---

## Prerequisites

1. Firebase project with Phone Authentication enabled
2. iOS app with Firebase SDK integrated
3. Apple Developer account
4. Physical iOS device for testing (simulators don't support push notifications)

---

## Step 1: Enable Push Notifications Capability in Xcode

1. Open your project in Xcode
2. Select your app target
3. Go to **Signing & Capabilities** tab
4. Click **"+ Capability"**
5. Add **"Push Notifications"**
6. Verify it shows a green checkmark

---

## Step 2: Configure Entitlements File

Create or update `iosApp/iosAppRelease.entitlements`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>aps-environment</key>
    <string>development</string>
</dict>
</plist>
```

**Note:** For production builds, use `"production"` instead of `"development"`.

**Important:** Ensure the entitlements file is linked in Build Settings:
- Build Settings → Code Signing Entitlements → Set to your entitlements file path

---

## Step 3: Create APNs Authentication Key

1. Go to [Apple Developer Portal](https://developer.apple.com) → **Certificates, Identifiers & Profiles**
2. Navigate to **Keys** → Click **Create a new key** (+)
3. **Key Name:** e.g., "Firebase APNs Key"
4. **Enable:**
   - ✅ **Apple Push Notifications service (APNs)** - **REQUIRED**
   - ✅ **App Attest** (for App Check) - Optional but recommended
5. Click **Continue** → **Register**
6. **Download the `.p8` file** (you can only download it once!)
7. **Note the Key ID**

---

## Step 4: Upload APNs Key to Firebase Console

1. Go to [Firebase Console](https://console.firebase.google.com) → Your Project
2. **Project Settings** → **Cloud Messaging** tab
3. Under **"Apple app configuration"**
4. Click **"Upload"** under **APNs Authentication Key**
5. Enter:
   - **Key ID** (from Step 3)
   - **Upload the `.p8` file**
6. Click **Save**

---

## Step 5: Configure iOS App Delegate

Implement notification setup in `iOSApp.swift`:

```swift
import SwiftUI
import FirebaseCore
import FirebaseAuth
import UserNotifications

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
        FirebaseApp.configure()
        
        // Set up notification center delegate FIRST
        UNUserNotificationCenter.current().delegate = self
        
        // Try to register immediately first (in case authorization was already granted)
        DispatchQueue.main.async {
            application.registerForRemoteNotifications()
            print("📱 Attempting to register for remote notifications...")
        }
        
        // Request notification authorization
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            if let error = error {
                print("⚠️ Failed to request notification authorization: \(error.localizedDescription)")
                print("⚠️ Firebase Phone Auth will fall back to reCAPTCHA")
            } else {
                print("✅ Notification authorization granted: \(granted)")
            }
            
            // Register for remote notifications - this MUST be called to get APNs token
            DispatchQueue.main.async {
                application.registerForRemoteNotifications()
                print("📱 Registered for remote notifications - waiting for APNs token...")
            }
        }
        
        return true
    }
    
    // Handle APNs token registration - CRITICAL for Phone Auth
    func application(_ application: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        // Convert token to hex string for logging
        let tokenString = deviceToken.map { String(format: "%02.2hhx", $0) }.joined()
        print("🔑 APNs Token received: \(tokenString)")
        
        // Determine APNs token type: production vs sandbox (development)
        #if DEBUG
        let tokenType: AuthAPNSTokenType = .sandbox
        #else
        let tokenType: AuthAPNSTokenType = .prod
        #endif
        
        // Set APNs token in Firebase Auth
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
```

---

## Step 6: Configure Info.plist

Add URL scheme for reCAPTCHA fallback (if needed):

```xml
<key>CFBundleURLTypes</key>
<array>
    <dict>
        <key>CFBundleURLSchemes</key>
        <array>
            <string>app-1-401191691760-ios-a9d3f8e249154cf4dea935</string>
        </array>
    </dict>
</array>
```

**Note:** The URL scheme is derived from your `GOOGLE_APP_ID` in `GoogleService-Info.plist`.

Add App Transport Security exceptions (if needed):

```xml
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsArbitraryLoads</key>
    <false/>
    <key>NSAllowsArbitraryLoadsInWebContent</key>
    <true/>
    <key>NSExceptionDomains</key>
    <dict>
        <!-- Add Firebase/Google domains as needed -->
    </dict>
</dict>
```

---

## Step 7: Implement Auth Repository (Kotlin Multiplatform)

In `IOSAuthRepository.kt`, ensure proper async handling:

```kotlin
override suspend fun sendOtp(phoneNumber: String) {
    suspendCancellableCoroutine { continuation ->
        try {
            val authInstance = auth
            val provider = FIRPhoneAuthProvider.providerWithAuth(authInstance)
                ?: throw IllegalStateException("Failed to get PhoneAuthProvider")
            
            provider.verifyPhoneNumber(
                phoneNumber = phoneNumber,
                UIDelegate = null
            ) { verificationID: String?, error: NSError? ->
                if (error != null) {
                    if (!continuation.isCompleted) {
                        continuation.resumeWithException(
                            Exception(error.localizedDescription() ?: "Verification failed")
                        )
                    }
                } else {
                    // Store verification ID asynchronously to avoid blocking
                    if (verificationID != null) {
                        CoroutineScope(Dispatchers.Default).launch {
                            try {
                                preferencesRepository.setVerificationId(verificationID)
                            } catch (e: Exception) {
                                // Log error but continue
                            }
                        }
                    }
                    // Resume continuation immediately - don't wait for storage
                    if (!continuation.isCompleted) {
                        continuation.resume(Unit)
                    }
                }
            }
        } catch (e: Exception) {
            if (!continuation.isCompleted) {
                continuation.resumeWithException(e)
            }
        }
    }
}
```

**Important:** Store the verification ID **asynchronously** using `CoroutineScope.launch` instead of `runBlocking` to avoid blocking the callback thread.

---

## Step 8: Verify Setup

### Check Console Logs

When the app launches, you should see:
```
✅ Notification authorization granted: true
📱 Registered for remote notifications - waiting for APNs token...
🔑 APNs Token received: [hex string]
✅ APNs token set in Firebase Auth with type: sandbox
✅ Verified: Firebase Auth has APNs token
```

### Test Phone Authentication

1. Enter a phone number
2. Tap "Get OTP"
3. You should receive OTP directly (no reCAPTCHA)
4. Navigation should proceed to OTP screen

---

## Common Issues and Solutions

### Issue 1: APNs Token Not Received

**Symptoms:**
- No `🔑 APNs Token received` log
- reCAPTCHA appears

**Solutions:**
1. Verify Push Notifications capability is enabled in Xcode
2. Check provisioning profile includes Push Notifications
3. Ensure testing on a **physical device** (not simulator)
4. Clean build folder and rebuild
5. Verify entitlements file is linked in Build Settings

### Issue 2: Screen Stuck After "Get OTP"

**Symptoms:**
- OTP received but screen stays on loading

**Solution:**
- Use **async storage** (`CoroutineScope.launch`) instead of `runBlocking` for storing verification ID
- Resume continuation immediately, don't wait for storage

### Issue 3: "Unable to load external reCAPTCHA dependencies!"

**Symptoms:**
- Error when APNs token is not available

**Solution:**
- Ensure APNs key is uploaded to Firebase Console
- Verify APNs token is being received and set in Firebase Auth
- Check console logs for token registration

### Issue 4: Provisioning Profile Issues

**Solution:**
1. In Xcode → Signing & Capabilities
2. Uncheck and recheck "Automatically manage signing"
3. Or manually regenerate profile in Apple Developer Portal

---

## Key Points

1. **APNs token is required:** Firebase needs the APNs token to use silent push instead of reCAPTCHA
2. **Physical device required:** Simulators don't support push notifications
3. **Async storage:** Don't block callbacks with `runBlocking`; use async coroutines
4. **Proper token type:** Use `.sandbox` for debug builds, `.prod` for release
5. **APNs key in Firebase:** Upload the APNs Authentication Key to Firebase Console

---

## Verification Checklist

- [ ] Push Notifications capability enabled in Xcode
- [ ] Entitlements file created with `aps-environment`
- [ ] APNs Authentication Key created in Apple Developer Portal
- [ ] APNs key uploaded to Firebase Console
- [ ] AppDelegate properly configured with notification handlers
- [ ] APNs token received and logged in console
- [ ] Firebase Auth has APNs token set
- [ ] Phone authentication works without reCAPTCHA
- [ ] Navigation works after OTP is sent

---

## Result

When configured correctly:
- ✅ No reCAPTCHA appears
- ✅ OTP is received directly via SMS
- ✅ Silent push notifications are used for verification
- ✅ Smooth user experience without web view interruptions

This setup enables Firebase Phone Authentication to use **silent push notifications (APNs)** instead of reCAPTCHA, providing a better user experience.
