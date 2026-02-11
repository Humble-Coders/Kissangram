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
