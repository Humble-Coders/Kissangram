# Kissangram Cloud Functions

Uses **Cloud Functions (2nd gen)** so the Firestore trigger can target the **`kissangram`** named database. You do **not** need to create a "(default)" Firestore database for deploy to succeed.

## Setup

1. Install dependencies: `npm install`
2. Link to your Firebase project: `firebase use <your-project-id>` (from repo root; create `.firebaserc` if needed)

## Deploy

From the **repo root**:

```bash
firebase deploy --only functions
```

## Functions

- **onPostCreate**: Triggered when a document is created in `posts/{postId}` in the **kissangram** database. Increments the author's `postsCount` and copies the post into `users/{userId}/feed/{postId}` for the author and every follower (feed fan-out). Ensure the Firestore database with ID `kissangram` exists in your project.
