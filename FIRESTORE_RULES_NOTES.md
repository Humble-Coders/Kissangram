# Firestore Rules Notes

## Named Database: kissangram

The app uses a **named Firestore database** `kissangram` (not the default `(default)` database). Security rules must be configured **separately** for this database.

### How to configure rules for the kissangram database

1. Open [Firebase Console](https://console.firebase.google.com) → your project
2. Go to **Firestore Database**
3. In the database dropdown (top left), select **kissangram** (not "(default)")
4. Go to the **Rules** tab
5. Ensure the rules match your intended security policy

### Users collection – profile updates

For profile image URL (and other profile fields) to save correctly, the rules must allow the **owner** to update their own document:

```
match /users/{userId} {
  allow read: if isAuthenticated();
  allow create: if isOwner(userId);
  allow update: if isOwner(userId) 
                || (isAuthenticated() 
                    && request.resource.data.diff(resource.data).affectedKeys()
                       .hasOnly(['followersCount', 'followingCount']));
  allow delete: if false;
}
```

With `isOwner(userId)` defined as:
```
function isOwner(userId) {
  return request.auth != null && request.auth.uid == userId;
}
```

**Important:** If you only configured rules for the default database, the `kissangram` database may have no rules or different rules. Profile image URL (and other updates) will fail silently or with permission errors until the `kissangram` database rules are set.
