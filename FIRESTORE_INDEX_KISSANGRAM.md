# Firestore index for feed (kissangram database)

`firebase deploy --only firestore:indexes` only deploys to the **(default)** database. This project uses the **kissangram** database, so indexes must be created for that database separately.

## Index required

- **Collection group**: `feed` (subcollection under `users/{userId}/feed`)
- **Query scope**: Collection
- **Field**: `createdAt` — Descending

## Option 1: Firebase Console (simplest)

1. Open [Firebase Console](https://console.firebase.google.com) → project **kissangram-19531**.
2. Go to **Firestore Database**.
3. In the database dropdown at the top, select the **kissangram** database (not “(default)”).
4. Open the **Indexes** tab → **Composite** → **Create index**.
5. Set:
   - **Collection group ID**: `feed`
   - **Query scope**: Collection
   - **Fields**: add one field — Field path: `createdAt`, Order: **Descending**
6. Click **Create**. Wait until the index status is **Enabled**.

## Option 2: gcloud CLI

With [Google Cloud SDK](https://cloud.google.com/sdk) installed and project set (`gcloud config set project kissangram-19531`):

```bash
gcloud firestore indexes composite create \
  --database=kissangram \
  --collection-group=feed \
  --query-scope=collection \
  --field-config=field-path=createdAt,order=descending
```

The index may take a few minutes to build. After it is **Enabled**, the home feed query will use it.

---

## Comment replies index

Required for `getReplies(postId, parentCommentId)` — querying replies under a parent comment.

- **Collection group**: `comments` (subcollection under `posts/{postId}/comments`)
- **Query scope**: Collection
- **Fields**:
  - `parentCommentId` — Ascending
  - `isActive` — Ascending
  - `createdAt` — Ascending

### Firebase Console

1. Open Firebase Console → Firestore Database.
2. Select the **kissangram** database.
3. Indexes → Composite → Create index.
4. Collection ID: `comments` (ensure you're indexing the subcollection under posts)
5. Add fields: `parentCommentId` (Ascending), `isActive` (Ascending), `createdAt` (Ascending)
6. Create.

---

## Search Screen Suggestions Indexes

### 1. Random Posts Index

Required for `getRandomPosts()` — fetching random posts for discovery feed.

- **Collection**: `posts`
- **Query scope**: Collection
- **Fields**:
  - `isActive` — Ascending
  - `createdAt` — Descending

**When you run the app and this query executes, Firebase will show an error in logcat with an exact link like:**
```
https://console.firebase.google.com/v1/r/project/kissangram-19531/firestore/indexes?create_composite=ClAKDnBvc3RzGg4KBGlzQWN0aXZlEAEaDAoKY3JlYXRlZEF0EAIaDAoIaXNBY3RpdmUQARoMCgpjcmVhdGVkQXQQAhgC
```

**Or manually create in Firebase Console:**
1. Open Firebase Console → Firestore Database.
2. Select the **kissangram** database.
3. Indexes → Composite → Create index.
4. Collection ID: `posts`
5. Add fields: `isActive` (Ascending), `createdAt` (Descending)
6. Create.

### 2. Suggested Users Index

Required for `getSuggestedUsers()` — fetching suggested users to follow.

- **Collection**: `users`
- **Query scope**: Collection
- **Fields**:
  - `isActive` — Ascending
  - `followersCount` — Descending

**When you run the app and this query executes, Firebase will show an error in logcat with an exact link like:**
```
https://console.firebase.google.com/v1/r/project/kissangram-19531/firestore/indexes?create_composite=ClAKDXBvc3RzGg4KBGlzQWN0aXZlEAEaEAoOZm9sbG93ZXJzQ291bnQQAhgC
```

**Or manually create in Firebase Console:**
1. Open Firebase Console → Firestore Database.
2. Select the **kissangram** database.
3. Indexes → Composite → Create index.
4. Collection ID: `users`
5. Add fields: `isActive` (Ascending), `followersCount` (Descending)
6. Create.

---

## Public Posts Fallback Index (Home Feed)

Required when the user's feed is empty — the app falls back to fetching paginated public posts from the `posts` collection.

- **Collection**: `posts`
- **Query scope**: Collection
- **Fields**:
  - `visibility` — Ascending
  - `isActive` — Ascending
  - `createdAt` — Descending

**Firebase Console:**
1. Open Firebase Console → Firestore Database.
2. Select the **kissangram** database.
3. Indexes → Composite → Create index.
4. Collection ID: `posts`
5. Add fields: `visibility` (Ascending), `isActive` (Ascending), `createdAt` (Descending)
6. Create.
