# Firebase Phone Authentication on Android - Complete Setup Guide

## Overview
This guide documents how to configure Firebase Phone Authentication to use **SMS Retriever API** instead of reCAPTCHA on Android, providing a seamless user experience.

---

## Prerequisites

1. Firebase project with Phone Authentication enabled
2. Android app with Firebase SDK integrated
3. Android app signed with a keystore (debug or release)

---

## Step 1: Get Your App's SHA-256 Hash

### For Debug Builds (Development)

Run this command in your terminal:

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

Look for the **SHA256:** line in the output. Copy the entire hash (without spaces or colons, or with colons - Firebase accepts both formats).

**Your current debug SHA-256 hash:**
```
96:73:95:8A:A5:D8:DF:5C:13:70:26:88:E5:0C:7E:BF:9D:E2:BA:18:67:0F:F1:61:F4:A2:4B:31:06:2E:28:D7
```

### Alternative: Using Gradle

You can also use the Gradle signing report:

```bash
./gradlew signingReport
```

This will show SHA-1 and SHA-256 fingerprints for all build variants.

### For Release Builds (Production)

If you have a release keystore:

```bash
keytool -list -v -keystore /path/to/your/release.keystore -alias your_alias_name
```

**Important:** You need to register **both** debug and release SHA-256 hashes if you want to test both build types.

---

## Step 2: Register SHA-256 Hash in Firebase Console

1. Go to [Firebase Console](https://console.firebase.google.com) → Your Project
2. Click on the **⚙️ Settings** icon → **Project settings**
3. Scroll down to **Your apps** section
4. Find your **Android app** (or add it if not added)
5. Click on the Android app
6. Scroll down to **SHA certificate fingerprints**
7. Click **"Add fingerprint"**
8. Paste your SHA-256 hash (you can use either format):
   - With colons: `96:73:95:8A:A5:D8:DF:5C:13:70:26:88:E5:0C:7E:BF:9D:E2:BA:18:67:0F:F1:61:F4:A2:4B:31:06:2E:28:D7`
   - Without colons: `9673958AA5D8DF5C13702688E50C7EBF9DE2BA18670FF161F4A24B31062E28D7`
9. Click **Save**

**Important:** 
- Add **both** debug and release SHA-256 hashes if you use both
- Changes may take a few minutes to propagate
- After adding, rebuild your app

---

## Step 3: Verify Android Implementation

Your `AndroidAuthRepository.kt` should already be configured correctly. Firebase automatically uses SMS Retriever API when the app hash is registered.

The implementation should look like this:

```kotlin
val options = PhoneAuthOptions.newBuilder(firebaseAuth)
    .setPhoneNumber(phoneNumber)
    .setTimeout(60L, java.util.concurrent.TimeUnit.SECONDS)
    .setActivity(activityContext)
    .setCallbacks(callbacks)
    .build()

PhoneAuthProvider.verifyPhoneNumber(options)
```

**No additional code changes needed** - Firebase automatically detects the registered hash and uses SMS Retriever API.

---

## Step 4: Test the Setup

1. **Rebuild your app** after registering the SHA-256 hash
2. Run the app on a physical device or emulator
3. Enter a phone number
4. Tap "Get OTP"
5. **Expected behavior:**
   - ✅ No reCAPTCHA appears
   - ✅ OTP is received directly via SMS
   - ✅ SMS Retriever API automatically reads the OTP (if app has SMS permission)

---

## How It Works

### SMS Retriever API (Preferred - No reCAPTCHA)

When your app hash is registered in Firebase Console:
1. Firebase sends an SMS with a special hash prefix
2. Android's SMS Retriever API automatically detects and reads the SMS
3. The OTP is extracted automatically (if SMS permission is granted)
4. No reCAPTCHA needed

### reCAPTCHA (Fallback)

If the app hash is **not** registered:
1. Firebase shows a reCAPTCHA challenge
2. User must complete the reCAPTCHA
3. Then OTP is sent via SMS

---

## Common Issues and Solutions

### Issue 1: reCAPTCHA Still Appears

**Symptoms:**
- reCAPTCHA shows up when requesting OTP

**Solutions:**
1. **Verify SHA-256 hash is registered:**
   - Check Firebase Console → Project Settings → Your Android app → SHA certificate fingerprints
   - Ensure the hash matches your signing certificate

2. **Check hash format:**
   - Firebase accepts both formats (with or without colons)
   - Make sure there are no extra spaces

3. **Wait for propagation:**
   - Changes may take 5-10 minutes to propagate
   - Try again after waiting

4. **Rebuild the app:**
   - Clean and rebuild after registering the hash
   - Ensure you're using the same keystore that generated the hash

5. **Verify keystore:**
   - Make sure you're using the correct keystore
   - Debug builds use `~/.android/debug.keystore`
   - Release builds use your release keystore

### Issue 2: Hash Mismatch

**Symptoms:**
- Hash is registered but reCAPTCHA still appears

**Solution:**
- Ensure the hash you registered matches the keystore you're using to sign the app
- For debug builds, use the debug keystore hash
- For release builds, use the release keystore hash

### Issue 3: Multiple Build Variants

**Solution:**
- Register SHA-256 hashes for **all** keystores you use
- Debug builds: Register debug keystore hash
- Release builds: Register release keystore hash
- Different flavors: Register hashes for each signing config

### Issue 4: Billing/Quota Errors

**Symptoms:**
- Error messages about billing or quota

**Solution:**
- Ensure Firebase project has billing enabled (Blaze plan required for production)
- Check SMS quota in Firebase Console
- For testing, use Firebase test phone numbers (no quota needed)

---

## Quick Reference: Getting SHA-256 Hash

### Debug Keystore
```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA256
```

### Release Keystore
```bash
keytool -list -v -keystore /path/to/release.keystore -alias your_alias -storepass your_password
```

### Using Gradle
```bash
./gradlew signingReport
```

### Using Android Studio
1. Build → Generate Signed Bundle / APK
2. Select APK → Next
3. Select or create keystore
4. View the SHA-256 fingerprint in the dialog

---

## Verification Checklist

- [ ] SHA-256 hash obtained from keystore
- [ ] SHA-256 hash registered in Firebase Console
- [ ] Both debug and release hashes registered (if using both)
- [ ] App rebuilt after registering hash
- [ ] Testing on device/emulator
- [ ] No reCAPTCHA appears when requesting OTP
- [ ] OTP received successfully

---

## Your Current Setup

**Debug SHA-256 Hash:**
```
96:73:95:8A:A5:D8:DF:5C:13:70:26:88:E5:0C:7E:BF:9D:E2:BA:18:67:0F:F1:61:F4:A2:4B:31:06:2E:28:D7
```

**Next Steps:**
1. Copy the hash above
2. Go to Firebase Console → Project Settings → Your Android app
3. Add this hash under "SHA certificate fingerprints"
4. Rebuild and test

---

## Result

When configured correctly:
- ✅ No reCAPTCHA appears
- ✅ OTP is received directly via SMS
- ✅ SMS Retriever API is used automatically
- ✅ Smooth user experience without web view interruptions

This setup enables Firebase Phone Authentication to use **SMS Retriever API** instead of reCAPTCHA, providing a better user experience on Android.
