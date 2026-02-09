# Schema Difference Report

**Generated:** January 2026  
**Purpose:** Compare current codebase schema with FIRESTORE_SCHEMA.md

---

## Summary

This report identifies differences between the actual Firestore schema implementation in the codebase and the documented schema in `FIRESTORE_SCHEMA.md`.

---

## 1. Users Collection

### ✅ Fields Present in Both Code and Schema
- `id`, `phoneNumber`, `name`, `username`, `profileImageUrl`, `bio`
- `role`, `verificationStatus`
- `expertise` (stored as list)
- `followersCount`, `followingCount`, `postsCount`
- `language`, `createdAt`, `lastActiveAt`
- `location` (as map with district, state, country)

### ❌ Fields in Schema but NOT in User Model
These fields are stored in Firestore (per `FirestoreUserRepository.kt`) but not in the `User.kt` model:

1. **`verificationDocUrl`** - Stored in Firestore, missing from model
2. **`verificationSubmittedAt`** - Documented in schema, not in model
3. **`verifiedAt`** - Documented in schema, not in model
4. **`updatedAt`** - Stored in Firestore, missing from model
5. **`searchKeywords`** - Stored in Firestore, missing from model
6. **`isActive`** - Stored in Firestore, missing from model
7. **`notificationsEnabled`** - Documented in schema, not implemented
8. **`fcmToken`** - Documented in schema, not implemented

### ⚠️ Fields in Code but NOT in Schema
1. **`village`** - Stored in `location` map in code, but schema doesn't mention it
   - Location: `shared/src/commonMain/kotlin/com/kissangram/model/User.kt:41`
   - Implementation: `FirestoreUserRepository.kt:159`

### ⚠️ Schema vs Code Mismatch
1. **Location `geoPoint`** - Schema shows `geoPoint: GeoPoint(30.9010, 75.8573)` in location, but code only stores `district`, `state`, `country`, `village` (no geoPoint)
   - Schema line: 115
   - Code: `FirestoreUserRepository.kt:222-232` (comment says "only names, no geoPoint")

---

## 2. Posts Collection

### ✅ Fields Present in Both
- All core fields match: `id`, `authorId`, `authorName`, `authorUsername`, `authorProfileImageUrl`, `authorRole`, `authorVerificationStatus`
- `type`, `text`, `media`, `voiceCaption`, `crops`, `hashtags`, `location`
- `question` (with all subfields)
- `likesCount`, `commentsCount`, `savesCount`
- `createdAt`, `updatedAt`

### ✅ Computed Fields (Not Stored)
- `isLikedByMe`, `isSavedByMe` - These are computed client-side, correctly not in schema

### ⚠️ Location Structure
- **Schema**: Shows `location.geoPoint` as GeoPoint
- **Code**: Model has `PostLocation` with `latitude`/`longitude` (Double?), code converts to `geoPoint` when storing
- **Status**: ✅ Correctly implemented (conversion happens in `FirestorePostRepository.kt:56-69`)

---

## 3. Comments Collection

### ✅ Fields Present in Both
- All fields match schema correctly
- `isLikedByMe` is computed (not stored) ✅

---

## 4. Stories Collection

### ✅ Fields Present in Both
- All core fields match
- `isViewedByMe` is computed (not stored) ✅

### ⚠️ Location Field
- **Schema**: Shows `location: { name: "Ludhiana, Punjab" }`
- **Model**: Has `locationName: String?` (flat field, not nested object)
- **Status**: Minor difference in structure, but functionally equivalent

---

## 5. Conversations Collection

### ✅ Fields Present in Both
- Core fields match: `id`, `participantIds`, `participants`, `lastMessage`, `createdAt`, `updatedAt`

### ⚠️ Unread Count Structure
- **Schema**: Shows `unreadCount` as a Map per user: `{ "abc123": 0, "xyz789": 2 }`
- **Model**: Has `unreadCount: Int` (single value for current user)
- **Status**: ⚠️ **MISMATCH** - Schema expects per-user map, model has single Int
- **Location**: `shared/src/commonMain/kotlin/com/kissangram/model/Conversation.kt:20`

### ✅ Computed Fields
- `otherParticipant` - Convenience field, not stored ✅

---

## 6. Messages Subcollection

### ✅ Fields Present in Both
- All message fields match correctly
- Content types (text, image, video, voice, sharedPost) all match

### ⚠️ Read Status
- **Schema**: Shows `readBy: { "xyz789": Timestamp }` (map of userId to timestamp)
- **Model**: Has `isReadByMe: Boolean` (single boolean)
- **Status**: ⚠️ **MISMATCH** - Schema expects per-user read receipts, model has single boolean
- **Location**: `shared/src/commonMain/kotlin/com/kissangram/model/Conversation.kt:57`

---

## 7. Group Chats Collection

### ❌ Not Implemented
- **Schema**: Documents complete `groupChats` collection with members and messages subcollections
- **Codebase**: No model, no repository, no implementation found
- **Status**: ⚠️ **MISSING** - Schema documents feature that doesn't exist in code

---

## 8. Reports Collection

### ❌ Not Implemented
- **Schema**: Documents `reports` collection
- **Codebase**: No model, no repository, no implementation found
- **Status**: ⚠️ **MISSING** - Schema documents feature that doesn't exist in code

---

## 9. Crops Collection

### ✅ Fields Match
- All fields match correctly
- Note: Model uses `nameEn`, `nameHi`, `namePa` while schema shows `name: { en, hi, pa }` - but this is just a representation difference

---

## 10. Hashtags Collection

### ✅ Model Exists
- Model matches schema correctly
- No repository implementation found, but model is defined

---

## Critical Issues to Fix

### 🔴 High Priority

1. **User Model Missing Fields**
   - Add: `verificationDocUrl`, `verificationSubmittedAt`, `verifiedAt`, `updatedAt`, `searchKeywords`, `isActive`
   - Consider: `notificationsEnabled`, `fcmToken` (if needed)

2. **Conversation Unread Count Mismatch**
   - Schema expects: `Map<String, Int>` (per user)
   - Model has: `Int` (single value)
   - **Action**: Update model or update schema to match implementation

3. **Message Read Receipts Mismatch**
   - Schema expects: `Map<String, Timestamp>` (per user)
   - Model has: `Boolean` (single value)
   - **Action**: Update model or update schema to match implementation

### 🟡 Medium Priority

4. **User Location Village Field**
   - Code stores `village` but schema doesn't document it
   - **Action**: Add `village` to schema documentation

5. **User Location GeoPoint**
   - Schema shows `geoPoint` but code doesn't store it
   - **Action**: Either remove from schema or implement in code

6. **Story Location Structure**
   - Schema shows nested object, model has flat string
   - **Action**: Document the actual structure used

### 🟢 Low Priority

7. **Group Chats Not Implemented**
   - Schema documents feature but no code exists
   - **Action**: Either implement or remove from schema

8. **Reports Not Implemented**
   - Schema documents feature but no code exists
   - **Action**: Either implement or remove from schema

---

## Recommendations

1. **Update User Model** to include all fields stored in Firestore
2. **Clarify Conversation/Message read status** - Decide on per-user map vs single value
3. **Update FIRESTORE_SCHEMA.md** to reflect actual implementation:
   - Add `village` to User location
   - Clarify if `geoPoint` is used or not
   - Update Conversation unreadCount structure
   - Update Message readBy structure
   - Mark Group Chats and Reports as "Planned" if not implemented
4. **Add missing fields** to User model for complete data access

---

## Next Steps

1. Review this report with the team
2. Decide on data structure for unread counts and read receipts
3. Update either code or schema to match
4. Add missing User model fields
5. Update FIRESTORE_SCHEMA.md per the cursor rule
