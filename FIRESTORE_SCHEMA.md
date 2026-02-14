# Kissangram Firestore Schema & Backend Guide

> Complete database schema and backend architecture for the Kissangram social media app.

---

## Table of Contents

1. [Design Principles](#design-principles)
2. [Schema Overview](#schema-overview)
3. [Collections](#collections)
   - [Users](#1-users-collection)
   - [Posts](#2-posts-collection)
   - [Stories](#3-stories-collection)
   - [Conversations (DMs)](#4-conversations-collection)
   - [Group Chats](#5-group-chats-collection)
   - [Reports](#6-reports-collection)
   - [Crops (Reference Data)](#7-crops-collection)
   - [Hashtags](#8-hashtags-collection)
4. [Denormalization Strategy](#denormalization-strategy)
5. [Firestore Indexes](#firestore-indexes)
6. [Cloud Functions](#cloud-functions)
7. [Security Rules](#security-rules)
8. [Query Patterns](#query-patterns)

---

## Design Principles

### 1. Denormalization
Firestore doesn't support JOINs. We duplicate frequently-accessed data to minimize reads.

### 2. Subcollections for One-to-Many
Use subcollections for data that:
- Needs pagination (messages, comments)
- Can grow unbounded
- Is queried independently

### 3. Counters Over Calculations
Store counts (`likesCount`, `followersCount`) instead of counting documents each time.

### 4. Selective Sync
Only sync critical denormalized data (verification status, role). Accept staleness for non-critical data (profile image, name).

---

## Schema Overview

```
ROOT COLLECTIONS
│
├── users/
│   └── {userId}
│       ├── followers/{followerId}
│       ├── following/{followingId}
│       ├── feed/{postId}
│       ├── storiesFeed/{storyId}
│       ├── savedPosts/{postId}
│       ├── notifications/{notificationId}
│       └── blockedUsers/{blockedUserId}
│
├── posts/
│   └── {postId}
│       ├── likes/{userId}
│       └── comments/{commentId}
│
├── stories/
│   └── {storyId}
│       └── views/{userId}
│
├── conversations/
│   └── {conversationId}
│       └── messages/{messageId}
│
├── groupChats/
│   └── {groupId}
│       ├── members/{userId}
│       └── messages/{messageId}
│
├── reports/{reportId}
│
├── crops/{cropId}
│
└── hashtags/{hashtagId}
```

---

## Collections

### 1. Users Collection

**Path:** `/users/{userId}`

```javascript
{
  // Basic Info
  id: "user123",
  phoneNumber: "+919876543210",
  name: "Rajesh Kumar",
  username: "rajesh_farmer",  // Unique, lowercase, for @mentions
  profileImageUrl: "https://...",
  bio: "Organic farmer from Punjab",
  
  // Role & Verification
  role: "farmer",  // "farmer" | "expert" | "agripreneur" | "input_seller" | "agri_lover"
  verificationStatus: "unverified",  // "unverified" | "pending" | "verified" | "rejected"
  verificationDocUrl: null,  // Only for experts
  verificationSubmittedAt: null,
  verifiedAt: null,
  
  // Location (optional)
  location: {
    district: "Ludhiana",
    state: "Punjab",
    country: "India",
    geoPoint: GeoPoint(30.9010, 75.8573),
  },
  
  // Expertise (for experts only)
  expertise: ["wheat", "rice", "pest_control"],
  
  // Social Counts (denormalized)
  followersCount: 0,
  followingCount: 0,
  postsCount: 0,
  
  // Settings
  notificationsEnabled: true,
  language: "hi",
  
  // Metadata
  createdAt: Timestamp,
  updatedAt: Timestamp,
  lastActiveAt: Timestamp,
  fcmToken: "device_token_for_push",
  
  // Search optimization (lowercase keywords)
  searchKeywords: ["rajesh", "kumar", "rajesh_farmer", "punjab"],
  
  // Status
  isActive: true,
}
```

#### Subcollection: Followers

**Path:** `/users/{userId}/followers/{followerId}`

```javascript
{
  id: "follower123",
  
  // Denormalized user info
  name: "Amit Singh",
  username: "amit_singh",
  profileImageUrl: "https://...",
  role: "farmer",
  verificationStatus: "unverified",
  
  followedAt: Timestamp,
}
```

#### Subcollection: Following

**Path:** `/users/{userId}/following/{followingId}`

```javascript
{
  id: "following456",
  
  // Denormalized user info
  name: "Dr. Sharma",
  username: "dr_sharma",
  profileImageUrl: "https://...",
  role: "expert",
  verificationStatus: "verified",
  
  followedAt: Timestamp,
}
```

#### Subcollection: Saved Posts

**Path:** `/users/{userId}/savedPosts/{postId}`

```javascript
{
  id: "post123",
  
  // Denormalized post preview
  postText: "My wheat crop is ready...",
  postImageUrl: "https://...",
  authorId: "user456",
  authorName: "Rajesh Kumar",
  authorUsername: "rajesh_farmer",
  authorProfileImageUrl: "https://...",
  
  savedAt: Timestamp,
}
```

#### Subcollection: Notifications

**Path:** `/users/{userId}/notifications/{notificationId}`

```javascript
{
  id: "notif123",
  
  // Type: "like" | "comment" | "follow" | "mention" | "question" | "answer" | "best_answer" | "group_invite"
  type: "like",
  
  // Actor (who triggered this)
  actorId: "user456",
  actorName: "Dr. Sharma",
  actorUsername: "dr_sharma",
  actorProfileImageUrl: "https://...",
  actorRole: "expert",
  actorVerificationStatus: "verified",
  
  // Related content
  postId: "post123",
  commentId: null,
  postImageUrl: "https://...",
  
  // State
  isRead: false,
  
  createdAt: Timestamp,
}
```

#### Subcollection: Blocked Users

**Path:** `/users/{userId}/blockedUsers/{blockedUserId}`

```javascript
{
  id: "user789",
  blockedAt: Timestamp,
}
```

#### Subcollection: Feed

**Path:** `/users/{userId}/feed/{postId}`

Populated by the `onPostCreate` Cloud Function (fan-out). Each document is a copy of a post for the home feed. Document ID = postId.

```javascript
{
  id: "post123",
  authorId: "user123",
  authorName: "Rajesh Kumar",
  authorUsername: "rajesh_farmer",
  authorProfileImageUrl: "https://...",
  authorRole: "farmer",
  authorVerificationStatus: "unverified",
  type: "normal",
  text: "My wheat crop is ready for harvest! 🌾",
  media: [...],
  voiceCaption: null,
  crops: ["wheat"],
  hashtags: ["#wheat", "#harvest"],
  location: { name: "...", geoPoint: GeoPoint(...) },
  question: null,
  likesCount: 0,
  commentsCount: 0,
  savesCount: 0,
  createdAt: Timestamp,
  updatedAt: Timestamp,
  isActive: true,
}
```

- **Read**: Owner only (each user reads their own feed).
- **Create/Update/Delete**: Cloud Functions only (client cannot write).

#### Subcollection: Stories Feed

**Path:** `/users/{userId}/storiesFeed/{storyId}`

Populated by the `onStoryCreate` Cloud Function (fan-out). Each document is a copy of a story for the stories feed. Document ID = storyId.

```javascript
{
  id: "story123",
  authorId: "user123",
  authorName: "Rajesh Kumar",
  authorUsername: "rajesh_farmer",
  authorProfileImageUrl: "https://...",
  authorRole: "farmer",
  authorVerificationStatus: "unverified",
  media: {
    url: "https://...",
    type: "image",  // "image" | "video"
    thumbnailUrl: null,
  },
  textOverlay: {
    text: "Morning at the farm!",
    position: { x: 0.5, y: 0.8 },
  },
  location: {
    name: "Ludhiana, Punjab",
  },
  visibility: "public",  // "public" | "followers"
  viewsCount: 0,
  likesCount: 0,
  createdAt: Timestamp,
  expiresAt: Timestamp,  // createdAt + 24 hours
  isActive: true,
}
```

- **Read**: Owner only (each user reads their own stories feed).
- **Create/Update/Delete**: Cloud Functions only (client cannot write).
- **Note**: Both "public" and "followers" visibility stories are distributed to followers' feeds. "Public" means the story is viewable on the author's profile by anyone, but still only appears in followers' feeds.

---

### 2. Posts Collection

**Path:** `/posts/{postId}`

```javascript
{
  id: "post123",
  
  // Author (denormalized)
  authorId: "user123",
  authorName: "Rajesh Kumar",
  authorUsername: "rajesh_farmer",
  authorProfileImageUrl: "https://...",
  authorRole: "farmer",
  authorVerificationStatus: "unverified",
  
  // Post Type
  type: "normal",  // "normal" | "question"
  
  // Content
  text: "My wheat crop is ready for harvest! 🌾",
  
  // Media
  media: [
    {
      url: "https://...",
      type: "image",  // "image" | "video"
      thumbnailUrl: null,  // For videos only
    }
  ],
  
  // Voice Caption (optional)
  voiceCaption: {
    url: "https://...",
    durationSeconds: 15,
  },
  
  // Crops & Hashtags
  crops: ["wheat"],
  hashtags: ["#wheat", "#harvest", "#punjab"],
  
  // Location (optional)
  location: {
    name: "Ludhiana, Punjab",
    geoPoint: GeoPoint(30.9010, 75.8573),
  },
  
  // Question-specific (only if type == "question")
  question: {
    targetExpertise: ["wheat", "pest_control"],
    targetExpertIds: ["expert123"],
    targetExperts: [
      {
        id: "expert123",
        name: "Dr. Sharma",
        username: "dr_sharma",
        profileImageUrl: "https://...",
      }
    ],
    isAnswered: false,
    bestAnswerCommentId: null,
  },
  
  // Engagement Counts
  likesCount: 0,
  commentsCount: 0,
  savesCount: 0,
  
  // Metadata
  createdAt: Timestamp,
  updatedAt: Timestamp,
  isActive: true,
}
```

#### Subcollection: Likes

**Path:** `/posts/{postId}/likes/{userId}`

```javascript
{
  id: "user789",
  
  name: "Amit Singh",
  username: "amit_singh",
  profileImageUrl: "https://...",
  
  likedAt: Timestamp,
}
```

#### Subcollection: Comments

**Path:** `/posts/{postId}/comments/{commentId}`

```javascript
{
  id: "comment123",
  
  // Author (denormalized)
  authorId: "user456",
  authorName: "Dr. Sharma",
  authorUsername: "dr_sharma",
  authorProfileImageUrl: "https://...",
  authorRole: "expert",
  authorVerificationStatus: "verified",
  
  // Content
  text: "Great harvest! Store properly to avoid moisture damage.",
  
  // Voice Comment (optional)
  voiceComment: {
    url: "https://...",
    durationSeconds: 10,
  },
  
  // For nested replies
  parentCommentId: null,  // null = top-level
  repliesCount: 0,
  
  // Engagement
  likesCount: 0,
  
  // Question-specific
  isExpertAnswer: true,
  isBestAnswer: false,
  
  // Metadata
  createdAt: Timestamp,
  isActive: true,
  deletionReason: null,  // Set when comment is soft-deleted (isActive = false)
}
```

---

### 3. Stories Collection

**Path:** `/stories/{storyId}`

```javascript
{
  id: "story123",
  
  // Author (denormalized)
  authorId: "user123",
  authorName: "Rajesh Kumar",
  authorUsername: "rajesh_farmer",
  authorProfileImageUrl: "https://...",
  authorRole: "farmer",
  authorVerificationStatus: "unverified",
  
  // Content
  media: {
    url: "https://...",
    type: "image",  // "image" | "video"
    thumbnailUrl: null,
  },
  
  // Text overlay (optional)
  textOverlay: {
    text: "Morning at the farm!",
    position: { x: 0.5, y: 0.8 },
  },
  
  // Location (optional)
  location: {
    name: "Ludhiana, Punjab",
  },
  
  // Visibility
  visibility: "public" | "followers",  // Who can see this story
  
  // Engagement
  viewsCount: 0,
  likesCount: 0,
  
  // Metadata
  createdAt: Timestamp,
  expiresAt: Timestamp,  // createdAt + 24 hours
  isActive: true,
}
```

#### Subcollection: Views

**Path:** `/stories/{storyId}/views/{userId}`

```javascript
{
  id: "user456",
  viewedAt: Timestamp,
}
```

#### Subcollection: Likes

**Path:** `/stories/{storyId}/likes/{userId}`

```javascript
{
  id: "user789",
  
  name: "Amit Singh",
  username: "amit_singh",
  profileImageUrl: "https://...",
  
  likedAt: Timestamp,
}
```

---

### 4. Conversations Collection

**Path:** `/conversations/{conversationId}`

> **conversationId format:** Sorted user IDs joined with underscore  
> Example: Users `abc123` and `xyz789` → `abc123_xyz789`

```javascript
{
  id: "abc123_xyz789",
  type: "direct",
  
  // Participants
  participantIds: ["abc123", "xyz789"],
  
  // Denormalized participant info
  participants: {
    "abc123": {
      name: "Rajesh Kumar",
      username: "rajesh_farmer",
      profileImageUrl: "https://...",
      role: "farmer",
      verificationStatus: "unverified",
    },
    "xyz789": {
      name: "Dr. Sharma",
      username: "dr_sharma",
      profileImageUrl: "https://...",
      role: "expert",
      verificationStatus: "verified",
    }
  },
  
  // Last message preview
  lastMessage: {
    senderId: "abc123",
    senderName: "Rajesh Kumar",
    type: "text",
    preview: "Thanks for the advice!",
    sentAt: Timestamp,
  },
  
  // Unread count per user
  unreadCount: {
    "abc123": 0,
    "xyz789": 2,
  },
  
  // Metadata
  createdAt: Timestamp,
  updatedAt: Timestamp,
  isActive: true,
}
```

#### Subcollection: Messages

**Path:** `/conversations/{conversationId}/messages/{messageId}`

```javascript
{
  id: "msg123",
  
  // Sender
  senderId: "abc123",
  senderName: "Rajesh Kumar",
  senderProfileImageUrl: "https://...",
  
  // Message Type: "text" | "image" | "video" | "voice" | "post"
  type: "text",
  
  // Content (based on type)
  text: "Hello! Can you help me?",  // For text
  
  image: {  // For image
    url: "https://...",
    thumbnailUrl: "https://...",
  },
  
  video: {  // For video
    url: "https://...",
    thumbnailUrl: "https://...",
    durationSeconds: 30,
  },
  
  voice: {  // For voice
    url: "https://...",
    durationSeconds: 15,
  },
  
  sharedPost: {  // For post
    postId: "post456",
    postText: "My wheat crop...",
    postImageUrl: "https://...",
    authorName: "Someone",
    authorUsername: "someone",
  },
  
  // Read receipts
  readBy: {
    "xyz789": Timestamp,
  },
  
  // Metadata
  sentAt: Timestamp,
  isDeleted: false,
}
```

#### Chat Flow Explained

```
1. STARTING A NEW CHAT
   ├── Calculate conversationId = sort([userA, userB]).join('_')
   ├── Check if conversation exists
   ├── If not, create conversation document
   └── Navigate to chat screen

2. SENDING A MESSAGE
   ├── Add message to /conversations/{id}/messages
   ├── Update conversation.lastMessage
   ├── Increment conversation.unreadCount for recipient
   └── (Cloud Function) Send push notification

3. FETCHING CONVERSATION LIST
   └── Query: conversations where participantIds contains currentUserId
              orderBy updatedAt desc

4. OPENING A CHAT
   ├── Listen to messages subcollection (real-time)
   ├── Set unreadCount[currentUserId] = 0
   └── Update readBy on messages as they're viewed
```

---

### 5. Group Chats Collection

**Path:** `/groupChats/{groupId}`

```javascript
{
  id: "group123",
  
  // Group Info
  name: "Wheat Farmers Punjab",
  description: "Discussion group for wheat farmers",
  imageUrl: "https://...",
  
  // Settings
  isPublic: true,
  
  // Creator & Admins
  creatorId: "user123",
  adminIds: ["user123", "user456"],
  
  // Members
  memberIds: ["user123", "user456", "user789"],
  membersCount: 250,
  
  // Topics
  topics: ["wheat", "punjab"],
  
  // Last message
  lastMessage: {
    senderId: "user789",
    senderName: "Amit",
    type: "text",
    preview: "What's the best fertilizer?",
    sentAt: Timestamp,
  },
  
  // Metadata
  createdAt: Timestamp,
  updatedAt: Timestamp,
  isActive: true,
}
```

#### Subcollection: Members

**Path:** `/groupChats/{groupId}/members/{userId}`

```javascript
{
  id: "user123",
  
  name: "Rajesh Kumar",
  username: "rajesh_farmer",
  profileImageUrl: "https://...",
  role: "farmer",
  verificationStatus: "unverified",
  
  joinedAt: Timestamp,
  isAdmin: true,
  lastReadAt: Timestamp,
}
```

#### Subcollection: Messages

**Path:** `/groupChats/{groupId}/messages/{messageId}`

```javascript
{
  id: "msg456",
  
  senderId: "user123",
  senderName: "Rajesh Kumar",
  senderProfileImageUrl: "https://...",
  senderRole: "farmer",
  senderVerificationStatus: "unverified",
  
  type: "text",  // "text" | "image" | "video" | "voice" | "post"
  
  text: "Hello everyone!",
  image: null,
  video: null,
  voice: null,
  sharedPost: null,
  
  sentAt: Timestamp,
  isDeleted: false,
}
```

---

### 6. Reports Collection

**Path:** `/reports/{reportId}`

```javascript
{
  id: "report123",
  
  // Reporter
  reporterId: "user123",
  reporterName: "Rajesh Kumar",
  
  // Target
  targetType: "post",  // "post" | "comment" | "user" | "story" | "message" | "group"
  targetId: "post456",
  targetAuthorId: "user789",
  
  // Details
  reason: "spam",  // "spam" | "harassment" | "misinformation" | "inappropriate" | "other"
  description: "This is promotional spam",
  
  // Admin review
  status: "pending",  // "pending" | "reviewed" | "action_taken" | "dismissed"
  reviewedBy: null,
  reviewNotes: null,
  actionTaken: null,  // "warning" | "content_removed" | "user_banned"
  
  // Metadata
  createdAt: Timestamp,
  reviewedAt: null,
}
```

---

### 7. Crops Collection

**Path:** `/crops/{cropId}`

> Reference data for crop selection in posts.

```javascript
{
  id: "wheat",
  
  name: {
    en: "Wheat",
    hi: "गेहूं",
    pa: "ਕਣਕ",
  },
  
  imageUrl: "https://...",
  hashtags: ["#wheat", "#गेहूं", "#rabi"],
  category: "cereal",  // "cereal" | "pulse" | "vegetable" | "fruit" | "oilseed" | "spice"
  season: "rabi",  // "rabi" | "kharif" | "zaid"
  
  isActive: true,
}
```

---

### 8. Hashtags Collection

**Path:** `/hashtags/{hashtagId}`

```javascript
{
  id: "wheat",  // Normalized: lowercase, no #
  displayName: "#wheat",
  
  relatedCrops: ["wheat"],
  
  postsCount: 15000,
  trendingScore: 85,
  
  lastUsedAt: Timestamp,
}
```

---

## Denormalization Strategy

### What to Sync (via Cloud Functions)

| Field | Locations | Priority |
|-------|-----------|----------|
| `verificationStatus` | posts, comments, followers, following, conversations, group members | **High** (trust indicator) |
| `role` | posts, comments, followers, following, conversations, group members | **High** |
| `username` | posts, comments, mentions | **High** (rarely changes) |
| `name` | posts, comments, followers, following, conversations | Medium |
| `profileImageUrl` | posts, comments, followers, following, conversations | Low (accept staleness) |

### Sync Trigger

```javascript
// Cloud Function: onUserProfileUpdate
exports.onUserProfileUpdate = functions.firestore
    .document('users/{userId}')
    .onUpdate(async (change, context) => {
        const userId = context.params.userId;
        const before = change.before.data();
        const after = change.after.data();
        
        // Only sync if critical fields changed
        const fieldsToCheck = ['name', 'username', 'profileImageUrl', 'role', 'verificationStatus'];
        const changedFields = fieldsToCheck.filter(f => before[f] !== after[f]);
        
        if (changedFields.length === 0) return null;
        
        const updateData = {};
        changedFields.forEach(f => {
            updateData[`author${f.charAt(0).toUpperCase() + f.slice(1)}`] = after[f];
        });
        
        // Update posts (batch, limit to recent)
        // Update conversations.participants
        // Update followers/following lists
        // ... etc
    });
```

---

## Firestore Indexes

Create these composite indexes in Firebase Console:

```
// Conversations by participant
Collection: conversations
Fields: participantIds (Arrays), updatedAt (Descending)

// Posts by author
Collection: posts
Fields: authorId (Ascending), createdAt (Descending), isActive (Ascending)

// Posts by crop
Collection: posts
Fields: crops (Arrays), createdAt (Descending), isActive (Ascending)

// Questions by expertise
Collection: posts
Fields: type (Ascending), question.targetExpertise (Arrays), createdAt (Descending)

// Questions for specific expert
Collection: posts
Fields: type (Ascending), question.targetExpertIds (Arrays), createdAt (Descending)

// User search
Collection: users
Fields: searchKeywords (Arrays), followersCount (Descending)

// Active stories by author
Collection: stories
Fields: authorId (Ascending), expiresAt (Descending), isActive (Ascending)

// Unread notifications
Collection Group: notifications
Fields: isRead (Ascending), createdAt (Descending)

// Public groups by size
Collection: groupChats
Fields: isPublic (Ascending), membersCount (Descending)

// Groups by topic
Collection: groupChats
Fields: topics (Arrays), membersCount (Descending)
```

---

## Cloud Functions

### Required Functions

| Function | Trigger | Purpose |
|----------|---------|---------|
| `onUserProfileUpdate` | `users/{userId}` update | Sync denormalized user data across collections |
| `onFollow` | `users/{userId}/followers/{followerId}` create | Update follower/following counts, send notification |
| `onUnfollow` | `users/{userId}/followers/{followerId}` delete | Update follower/following counts |
| `onPostCreate` | `posts/{postId}` create | Update user's postsCount, fan-out to followers' feeds |
| `onPostDelete` | `posts/{postId}` delete/update | Update user's postsCount |
| `onStoryCreate` | `stories/{storyId}` create | Fan-out story to followers' storiesFeed |
| `onLike` | `posts/{postId}/likes/{userId}` create | Update likesCount, send notification |
| `onUnlike` | `posts/{postId}/likes/{userId}` delete | Update likesCount |
| `onComment` | `posts/{postId}/comments/{commentId}` create | Update commentsCount/repliesCount, send notification |
| `onCommentUpdate` | `posts/{postId}/comments/{commentId}` update | Handle soft delete (isActive = false), update counts |
| `onCommentDelete` | `posts/{postId}/comments/{commentId}` delete | Update counts (hard delete - rarely used) |
| `onSavePost` | `users/{userId}/savedPosts/{postId}` create | Update post's savesCount |
| `onUnsavePost` | `users/{userId}/savedPosts/{postId}` delete | Update post's savesCount |
| `onMessageSend` | `conversations/{id}/messages/{msgId}` create | Update lastMessage, unreadCount, send push |
| `onGroupMessageSend` | `groupChats/{id}/messages/{msgId}` create | Update lastMessage |
| `onGroupJoin` | `groupChats/{id}/members/{userId}` create | Update membersCount |
| `onGroupLeave` | `groupChats/{id}/members/{userId}` delete | Update membersCount |
| `cleanupExpiredStories` | Scheduled (every hour) | Delete stories where expiresAt < now |
| `updateTrendingHashtags` | Scheduled (every hour) | Calculate trending scores based on recent posts |
| `sendScheduledNotifications` | Scheduled | Batch notification delivery |

### Example: onLike Function

```javascript
exports.onLike = functions.firestore
    .document('posts/{postId}/likes/{userId}')
    .onCreate(async (snap, context) => {
        const { postId, userId } = context.params;
        const likeData = snap.data();
        
        // 1. Increment likes count
        await admin.firestore()
            .collection('posts')
            .doc(postId)
            .update({
                likesCount: admin.firestore.FieldValue.increment(1)
            });
        
        // 2. Get post to find author
        const postDoc = await admin.firestore()
            .collection('posts')
            .doc(postId)
            .get();
        
        const post = postDoc.data();
        
        // 3. Don't notify if user liked their own post
        if (post.authorId === userId) return;
        
        // 4. Create notification
        await admin.firestore()
            .collection('users')
            .doc(post.authorId)
            .collection('notifications')
            .add({
                type: 'like',
                actorId: userId,
                actorName: likeData.name,
                actorUsername: likeData.username,
                actorProfileImageUrl: likeData.profileImageUrl,
                postId: postId,
                postImageUrl: post.media?.[0]?.url || null,
                isRead: false,
                createdAt: admin.firestore.FieldValue.serverTimestamp(),
            });
        
        // 5. Send push notification
        const authorDoc = await admin.firestore()
            .collection('users')
            .doc(post.authorId)
            .get();
        
        const author = authorDoc.data();
        if (author.fcmToken && author.notificationsEnabled) {
            await admin.messaging().send({
                token: author.fcmToken,
                notification: {
                    title: 'New Like',
                    body: `${likeData.name} liked your post`,
                },
                data: {
                    type: 'like',
                    postId: postId,
                },
            });
        }
    });
```

### Example: onComment Function

```javascript
exports.onComment = functions.firestore.v2.firestore
    .onDocumentCreated({
        document: 'posts/{postId}/comments/{commentId}',
        database: 'kissangram',
    }, async (event) => {
        const { postId, commentId } = event.params;
        const commentData = event.data.data();
        const commenterId = commentData.authorId;
        const parentCommentId = commentData.parentCommentId || null;

        // 1. Update counts based on whether it's a reply or top-level comment
        if (parentCommentId) {
            // It's a reply - increment repliesCount on parent comment
            await db
                .collection('posts')
                .doc(postId)
                .collection('comments')
                .doc(parentCommentId)
                .update({
                    repliesCount: admin.firestore.FieldValue.increment(1),
                });
        } else {
            // It's a top-level comment - increment commentsCount on post
            await db.collection('posts').doc(postId).update({
                commentsCount: admin.firestore.FieldValue.increment(1),
            });
        }

        // 2. Get post to find author
        const postDoc = await db.collection('posts').doc(postId).get();
        const post = postDoc.data();
        const postAuthorId = post.authorId;

        // 3. Don't notify if user commented on their own post
        if (postAuthorId === commenterId) {
            // But still notify parent comment author if it's a reply
            if (parentCommentId) {
                const parentCommentDoc = await db
                    .collection('posts')
                    .doc(postId)
                    .collection('comments')
                    .doc(parentCommentId)
                    .get();
                if (parentCommentDoc.exists) {
                    const parentComment = parentCommentDoc.data();
                    const parentCommentAuthorId = parentComment.authorId;
                    if (parentCommentAuthorId && parentCommentAuthorId !== commenterId) {
                        // Notify parent comment author about the reply
                        await createReplyNotification(
                            parentCommentAuthorId,
                            commenterId,
                            commentData,
                            postId,
                            commentId,
                            post
                        );
                    }
                }
            }
            return null;
        }

        // 4. Create notification for post author (top-level comment or reply)
        await createCommentNotification(
            postAuthorId,
            commenterId,
            commentData,
            postId,
            commentId,
            post
        );

        // 5. If it's a reply, also notify parent comment author (if different from post author)
        if (parentCommentId && parentCommentId !== postAuthorId) {
            const parentCommentDoc = await db
                .collection('posts')
                .doc(postId)
                .collection('comments')
                .doc(parentCommentId)
                .get();
            if (parentCommentDoc.exists) {
                const parentComment = parentCommentDoc.data();
                const parentCommentAuthorId = parentComment.authorId;
                if (
                    parentCommentAuthorId &&
                    parentCommentAuthorId !== postAuthorId &&
                    parentCommentAuthorId !== commenterId
                ) {
                    await createReplyNotification(
                        parentCommentAuthorId,
                        commenterId,
                        commentData,
                        postId,
                        commentId,
                        post
                    );
                }
            }
        }

        return null;
    });
```

### Example: onCommentUpdate Function (Soft Delete)

```javascript
exports.onCommentUpdate = functions.firestore.v2.firestore
    .onDocumentUpdated({
        document: 'posts/{postId}/comments/{commentId}',
        database: 'kissangram',
    }, async (event) => {
        const before = event.data.before;
        const after = event.data.after;
        const { postId, commentId } = event.params;

        const beforeData = before.data();
        const afterData = after.data();

        // Check if comment was soft-deleted (isActive changed from true to false)
        if (beforeData.isActive === true && afterData.isActive === false) {
            const parentCommentId = afterData.parentCommentId || null;

            if (parentCommentId) {
                // It was a reply - decrement repliesCount on parent comment
                await db
                    .collection('posts')
                    .doc(postId)
                    .collection('comments')
                    .doc(parentCommentId)
                    .update({
                        repliesCount: admin.firestore.FieldValue.increment(-1),
                    });
            } else {
                // It was a top-level comment - decrement commentsCount on post
                await db.collection('posts').doc(postId).update({
                    commentsCount: admin.firestore.FieldValue.increment(-1),
                });
            }
        }

        return null;
    });
```

---

## Security Rules

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // Helper functions
    function isAuthenticated() {
      return request.auth != null;
    }
    
    function isOwner(userId) {
      return request.auth.uid == userId;
    }
    
    function getUserData() {
      return get(/databases/$(database)/documents/users/$(request.auth.uid)).data;
    }
    
    // Users
    match /users/{userId} {
      allow read: if isAuthenticated();
      allow create: if isOwner(userId);
      allow update: if isOwner(userId);
      allow delete: if false;  // Users can't delete accounts directly
      
      // Followers
      match /followers/{followerId} {
        allow read: if isAuthenticated();
        allow create: if isAuthenticated() && isOwner(followerId);
        allow delete: if isAuthenticated() && isOwner(followerId);
      }
      
      // Following
      match /following/{followingId} {
        allow read: if isAuthenticated();
        allow create: if isAuthenticated() && isOwner(userId);
        allow delete: if isAuthenticated() && isOwner(userId);
      }
      
      // Saved Posts
      match /savedPosts/{postId} {
        allow read: if isOwner(userId);
        allow write: if isOwner(userId);
      }
      
      // Notifications
      match /notifications/{notificationId} {
        allow read: if isOwner(userId);
        allow update: if isOwner(userId);  // Mark as read
        allow delete: if isOwner(userId);
        allow create: if false;  // Only Cloud Functions create notifications
      }
      
      // Blocked Users
      match /blockedUsers/{blockedId} {
        allow read, write: if isOwner(userId);
      }
      
      // Feed (fan-out from onPostCreate; read-only for owner, write by Cloud Functions only)
      match /feed/{postId} {
        allow read: if isOwner(userId);
        allow create, update, delete: if false;
      }
      
      // Stories Feed (fan-out from onStoryCreate; read-only for owner, write by Cloud Functions only)
      match /storiesFeed/{storyId} {
        allow read: if isOwner(userId);
        allow create, update, delete: if false;
      }
    }
    
    // Posts
    match /posts/{postId} {
      allow read: if isAuthenticated();
      allow create: if isAuthenticated() 
                    && request.resource.data.authorId == request.auth.uid;
      allow update: if isAuthenticated() 
                    && resource.data.authorId == request.auth.uid;
      allow delete: if isAuthenticated() 
                    && resource.data.authorId == request.auth.uid;
      
      // Likes
      match /likes/{userId} {
        allow read: if isAuthenticated();
        allow create: if isAuthenticated() && isOwner(userId);
        allow delete: if isAuthenticated() && isOwner(userId);
      }
      
      // Comments
      match /comments/{commentId} {
        allow read: if isAuthenticated();
        allow create: if isAuthenticated();
        allow update: if isAuthenticated() 
                      && resource.data.authorId == request.auth.uid;
        allow delete: if isAuthenticated() 
                      && resource.data.authorId == request.auth.uid;
      }
    }
    
    // Stories
    match /stories/{storyId} {
      allow read: if isAuthenticated();
      allow create: if isAuthenticated() 
                    && request.resource.data.authorId == request.auth.uid;
      allow delete: if isAuthenticated() 
                    && resource.data.authorId == request.auth.uid;
      
      match /views/{userId} {
        allow read: if isAuthenticated() && isOwner(userId);  // Users can read their own view status
        allow create: if isAuthenticated() && isOwner(userId);
      }
      
      match /likes/{userId} {
        allow read: if isAuthenticated();  // Anyone can check if a story is liked
        allow create: if isAuthenticated() && isOwner(userId);
        allow delete: if isAuthenticated() && isOwner(userId);
      }
    }
    
    // Conversations
    match /conversations/{conversationId} {
      allow read: if isAuthenticated() 
                  && request.auth.uid in resource.data.participantIds;
      allow create: if isAuthenticated() 
                    && request.auth.uid in request.resource.data.participantIds;
      allow update: if isAuthenticated() 
                    && request.auth.uid in resource.data.participantIds;
      
      match /messages/{messageId} {
        allow read: if isAuthenticated() 
                    && request.auth.uid in get(/databases/$(database)/documents/conversations/$(conversationId)).data.participantIds;
        allow create: if isAuthenticated() 
                      && request.auth.uid in get(/databases/$(database)/documents/conversations/$(conversationId)).data.participantIds;
      }
    }
    
    // Group Chats
    match /groupChats/{groupId} {
      allow read: if isAuthenticated();
      allow create: if isAuthenticated();
      allow update: if isAuthenticated() 
                    && request.auth.uid in resource.data.adminIds;
      
      match /members/{userId} {
        allow read: if isAuthenticated();
        allow create: if isAuthenticated() 
                      && (resource == null || get(/databases/$(database)/documents/groupChats/$(groupId)).data.isPublic == true);
        allow delete: if isAuthenticated() 
                      && (isOwner(userId) || request.auth.uid in get(/databases/$(database)/documents/groupChats/$(groupId)).data.adminIds);
      }
      
      match /messages/{messageId} {
        allow read: if isAuthenticated() 
                    && exists(/databases/$(database)/documents/groupChats/$(groupId)/members/$(request.auth.uid));
        allow create: if isAuthenticated() 
                      && exists(/databases/$(database)/documents/groupChats/$(groupId)/members/$(request.auth.uid));
      }
    }
    
    // Reports
    match /reports/{reportId} {
      allow create: if isAuthenticated();
      allow read, update, delete: if false;  // Admin only via console
    }
    
    // App config (reference data: appConfig/crops, appConfig/locations)
    match /appConfig/{docId} {
      allow read: if isAuthenticated();
      allow write: if isAuthenticated();  // Dev upload from app; restrict in production if needed
    }
    
    // Crops (read-only reference data)
    match /crops/{cropId} {
      allow read: if isAuthenticated();
      allow write: if false;  // Admin only
    }
    
    // Hashtags
    match /hashtags/{hashtagId} {
      allow read: if isAuthenticated();
      allow write: if false;  // Cloud Functions only
    }
  }
}
```

---

## Query Patterns

### Home Feed

```kotlin
// Get posts from users I follow
// Note: Requires fan-out pattern for scale (Cloud Function copies posts to user feeds)

// Simple approach (works for small follower counts):
val followingIds = getFollowingIds(currentUserId)
firestore.collection("posts")
    .whereIn("authorId", followingIds.take(10))  // Firestore limit: 10 items in whereIn
    .orderBy("createdAt", Query.Direction.DESCENDING)
    .limit(20)
```

### Questions for Expert

```kotlin
// Questions in my expertise area
firestore.collection("posts")
    .whereEqualTo("type", "question")
    .whereArrayContainsAny("question.targetExpertise", myExpertise)
    .orderBy("createdAt", Query.Direction.DESCENDING)

// Questions specifically for me
firestore.collection("posts")
    .whereEqualTo("type", "question")
    .whereArrayContains("question.targetExpertIds", myUserId)
    .orderBy("createdAt", Query.Direction.DESCENDING)
```

### User Search

```kotlin
firestore.collection("users")
    .whereArrayContains("searchKeywords", searchTerm.lowercase())
    .orderBy("followersCount", Query.Direction.DESCENDING)
    .limit(20)
```

### My Conversations

```kotlin
firestore.collection("conversations")
    .whereArrayContains("participantIds", currentUserId)
    .orderBy("updatedAt", Query.Direction.DESCENDING)
    .limit(20)
```

### Posts by Crop

```kotlin
firestore.collection("posts")
    .whereArrayContains("crops", "wheat")
    .whereEqualTo("isActive", true)
    .orderBy("createdAt", Query.Direction.DESCENDING)
    .limit(20)
```

### Trending Hashtags

```kotlin
firestore.collection("hashtags")
    .orderBy("trendingScore", Query.Direction.DESCENDING)
    .limit(10)
```

---

## Cost Optimization Tips

1. **Limit denormalization updates** - Only sync last 100 posts when profile updates
2. **Use pagination** - Always use `.limit()` and implement cursor-based pagination
3. **Cache aggressively** - Use client-side caching for user profiles, crops list
4. **Batch writes** - Use batched writes for bulk operations
5. **Avoid deep listeners** - Don't listen to entire collections, use specific queries
6. **TTL for stories** - Use scheduled function to clean up expired stories

---

## Next Steps

1. Create Kotlin models in `shared/src/commonMain/kotlin/com/kissangram/model/`
2. Create repository interfaces in `shared/src/commonMain/kotlin/com/kissangram/`
3. Implement Android/iOS repositories
4. Set up Cloud Functions project
5. Configure Firestore indexes in Firebase Console
6. Deploy security rules

---

*Last updated: February 2026 (Added comment feature with replies, delete with reason, and Cloud Functions)*
