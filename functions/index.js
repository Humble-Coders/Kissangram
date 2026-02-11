const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();

// Use same named database as the app (Android/iOS use "kissangram")
const db = admin.firestore();
db.settings({ databaseId: "kissangram" });

const BATCH_SIZE = 500;

/**
 * Build feed entry from post data (copy fields; omit client-only fields).
 * @param {object} postData - Raw post document from snap.data()
 * @param {string} postId - Post document ID
 * @returns {object} Feed document for users/{userId}/feed/{postId}
 */
function buildFeedEntry(postData, postId) {
  const entry = { ...postData };
  entry.id = postId;
  delete entry.isLikedByMe;
  delete entry.isSavedByMe;
  return entry;
}

/**
 * Fan-out: when a post is created, copy it to the author's feed and to each follower's feed.
 * Also increments the author's postsCount.
 * Uses 2nd gen so we can attach to the "kissangram" database (1st gen requires (default) only).
 */
exports.onPostCreate = onDocumentCreated(
  {
    document: "posts/{postId}",
    database: "kissangram",
  },
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) {
      console.warn("onPostCreate: no data");
      return null;
    }
    const postId = event.params.postId;
    const postData = snapshot.data();
    const authorId = postData.authorId;

    if (!authorId) {
      console.warn("onPostCreate: post missing authorId", { postId });
      return null;
    }

    try {
      // 1. Increment author's postsCount
      await db.collection("users").doc(authorId).update({
        postsCount: admin.firestore.FieldValue.increment(1),
      });

      const feedEntry = buildFeedEntry(postData, postId);

      // 2. Get all follower IDs from users/{authorId}/followers
      const followersSnap = await db
        .collection("users")
        .doc(authorId)
        .collection("followers")
        .get();

      const recipientIds = followersSnap.docs.map((d) => d.id);
      if (!recipientIds.includes(authorId)) {
        recipientIds.push(authorId);
      }
      if (recipientIds.length === 0) {
        recipientIds.push(authorId);
      }

      // 3. Batch writes: users/{userId}/feed/{postId}.set(feedEntry), max 500 per batch
      for (let i = 0; i < recipientIds.length; i += BATCH_SIZE) {
        const chunk = recipientIds.slice(i, i + BATCH_SIZE);
        const batch = db.batch();
        for (const userId of chunk) {
          const feedRef = db
            .collection("users")
            .doc(userId)
            .collection("feed")
            .doc(postId);
          batch.set(feedRef, feedEntry);
        }
        await batch.commit();
      }

      console.log("onPostCreate: fan-out complete", {
        postId,
        authorId,
        recipientCount: recipientIds.length,
      });
      return null;
    } catch (err) {
      console.error("onPostCreate failed", { postId, authorId, err });
      throw err;
    }
  }
);
