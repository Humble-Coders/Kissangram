const { onDocumentCreated, onDocumentDeleted, onDocumentUpdated } = require("firebase-functions/v2/firestore");
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

/**
 * When a post is liked, increment likesCount and send notification.
 * Uses 2nd gen so we can attach to the "kissangram" database.
 */
exports.onLike = onDocumentCreated(
  {
    document: "posts/{postId}/likes/{userId}",
    database: "kissangram",
  },
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) {
      console.warn("onLike: no data");
      return null;
    }
    const { postId, userId } = event.params;
    const likeData = snapshot.data();

    try {
      // 1. Increment likes count on the post document
      await db.collection("posts").doc(postId).update({
        likesCount: admin.firestore.FieldValue.increment(1),
      });

      // 2. Get post to find author
      const postDoc = await db.collection("posts").doc(postId).get();
      if (!postDoc.exists) {
        console.warn("onLike: post not found", { postId });
        return null;
      }

      const post = postDoc.data();
      const authorId = post.authorId;

      if (!authorId) {
        console.warn("onLike: post missing authorId", { postId });
        return null;
      }

      // 3. Don't notify if user liked their own post
      if (authorId === userId) {
        console.log("onLike: user liked own post, skipping notification", {
          postId,
          userId,
        });
        return null;
      }

      // 4. Create notification document
      const notificationData = {
        type: "like",
        actorId: userId,
        actorName: likeData.name || "",
        actorUsername: likeData.username || "",
        actorProfileImageUrl: likeData.profileImageUrl || null,
        actorRole: likeData.role || "farmer",
        actorVerificationStatus: likeData.verificationStatus || "unverified",
        postId: postId,
        commentId: null,
        postImageUrl: post.media?.[0]?.url || null,
        isRead: false,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      };

      await db
        .collection("users")
        .doc(authorId)
        .collection("notifications")
        .add(notificationData);

      // 5. Send push notification if FCM token exists and notifications enabled
      const authorDoc = await db.collection("users").doc(authorId).get();
      if (authorDoc.exists) {
        const author = authorDoc.data();
        if (author.fcmToken && author.notificationsEnabled !== false) {
          try {
            await admin.messaging().send({
              token: author.fcmToken,
              notification: {
                title: "New Like",
                body: `${likeData.name || "Someone"} liked your post`,
              },
              data: {
                type: "like",
                postId: postId,
              },
            });
          } catch (messagingError) {
            console.error("onLike: Failed to send push notification", {
              postId,
              authorId,
              error: messagingError,
            });
            // Don't throw - notification creation succeeded
          }
        }
      }

      console.log("onLike: success", { postId, userId, authorId });
      return null;
    } catch (err) {
      console.error("onLike failed", { postId, userId, err });
      throw err;
    }
  }
);

/**
 * When a post is unliked, decrement likesCount.
 * Uses 2nd gen so we can attach to the "kissangram" database.
 */
exports.onUnlike = onDocumentDeleted(
  {
    document: "posts/{postId}/likes/{userId}",
    database: "kissangram",
  },
  async (event) => {
    const { postId } = event.params;

    try {
      // Decrement likes count on the post document
      await db.collection("posts").doc(postId).update({
        likesCount: admin.firestore.FieldValue.increment(-1),
      });

      console.log("onUnlike: success", { postId });
      return null;
    } catch (err) {
      console.error("onUnlike failed", { postId, err });
      throw err;
    }
  }
);

/**
 * When a comment is created, update counts and send notifications.
 * Handles both top-level comments and replies.
 * Uses 2nd gen so we can attach to the "kissangram" database.
 */
exports.onComment = onDocumentCreated(
  {
    document: "posts/{postId}/comments/{commentId}",
    database: "kissangram",
  },
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) {
      console.warn("onComment: no data");
      return null;
    }
    const { postId, commentId } = event.params;
    const commentData = snapshot.data();
    const commenterId = commentData.authorId;
    const parentCommentId = commentData.parentCommentId || null;

    try {
      // 1. Update counts based on whether it's a reply or top-level comment
      if (parentCommentId) {
        // It's a reply - increment repliesCount on parent comment
        await db
          .collection("posts")
          .doc(postId)
          .collection("comments")
          .doc(parentCommentId)
          .update({
            repliesCount: admin.firestore.FieldValue.increment(1),
          });
      } else {
        // It's a top-level comment - increment commentsCount on post
        await db.collection("posts").doc(postId).update({
          commentsCount: admin.firestore.FieldValue.increment(1),
        });
      }

      // 2. Get post to find author
      const postDoc = await db.collection("posts").doc(postId).get();
      if (!postDoc.exists) {
        console.warn("onComment: post not found", { postId });
        return null;
      }

      const post = postDoc.data();
      const postAuthorId = post.authorId;

      if (!postAuthorId) {
        console.warn("onComment: post missing authorId", { postId });
        return null;
      }

      // 3. Don't notify if user commented on their own post
      if (postAuthorId === commenterId) {
        console.log("onComment: user commented on own post, skipping notification", {
          postId,
          commentId,
          commenterId,
        });
        // But still notify parent comment author if it's a reply
        if (parentCommentId) {
          const parentCommentDoc = await db
            .collection("posts")
            .doc(postId)
            .collection("comments")
            .doc(parentCommentId)
            .get();
          if (parentCommentDoc.exists) {
            const parentComment = parentCommentDoc.data();
            const parentCommentAuthorId = parentComment.authorId;
            if (
              parentCommentAuthorId &&
              parentCommentAuthorId !== commenterId
            ) {
              // Notify parent comment author about the reply
              const replyNotificationData = {
                type: "comment",
                actorId: commenterId,
                actorName: commentData.authorName || "",
                actorUsername: commentData.authorUsername || "",
                actorProfileImageUrl: commentData.authorProfileImageUrl || null,
                actorRole: commentData.authorRole || "farmer",
                actorVerificationStatus:
                  commentData.authorVerificationStatus || "unverified",
                postId: postId,
                commentId: commentId,
                postImageUrl: post.media?.[0]?.url || null,
                isRead: false,
                createdAt: admin.firestore.FieldValue.serverTimestamp(),
              };

              await db
                .collection("users")
                .doc(parentCommentAuthorId)
                .collection("notifications")
                .add(replyNotificationData);

              // Send push notification
              const parentAuthorDoc = await db
                .collection("users")
                .doc(parentCommentAuthorId)
                .get();
              if (parentAuthorDoc.exists) {
                const parentAuthor = parentAuthorDoc.data();
                if (
                  parentAuthor.fcmToken &&
                  parentAuthor.notificationsEnabled !== false
                ) {
                  try {
                    await admin.messaging().send({
                      token: parentAuthor.fcmToken,
                      notification: {
                        title: "New Reply",
                        body: `${commentData.authorName || "Someone"} replied to your comment`,
                      },
                      data: {
                        type: "comment",
                        postId: postId,
                        commentId: commentId,
                      },
                    });
                  } catch (messagingError) {
                    console.error(
                      "onComment: Failed to send push notification to parent comment author",
                      {
                        postId,
                        commentId,
                        parentCommentAuthorId,
                        error: messagingError,
                      }
                    );
                  }
                }
              }
            }
          }
        }
        return null;
      }

      // 4. Create notification for post author (top-level comment or reply)
      const notificationData = {
        type: "comment",
        actorId: commenterId,
        actorName: commentData.authorName || "",
        actorUsername: commentData.authorUsername || "",
        actorProfileImageUrl: commentData.authorProfileImageUrl || null,
        actorRole: commentData.authorRole || "farmer",
        actorVerificationStatus:
          commentData.authorVerificationStatus || "unverified",
        postId: postId,
        commentId: commentId,
        postImageUrl: post.media?.[0]?.url || null,
        isRead: false,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      };

      await db
        .collection("users")
        .doc(postAuthorId)
        .collection("notifications")
        .add(notificationData);

      // 5. If it's a reply, also notify parent comment author (if different from post author)
      if (parentCommentId && parentCommentId !== postAuthorId) {
        const parentCommentDoc = await db
          .collection("posts")
          .doc(postId)
          .collection("comments")
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
            // Notify parent comment author about the reply
            const replyNotificationData = {
              type: "comment",
              actorId: commenterId,
              actorName: commentData.authorName || "",
              actorUsername: commentData.authorUsername || "",
              actorProfileImageUrl: commentData.authorProfileImageUrl || null,
              actorRole: commentData.authorRole || "farmer",
              actorVerificationStatus:
                commentData.authorVerificationStatus || "unverified",
              postId: postId,
              commentId: commentId,
              postImageUrl: post.media?.[0]?.url || null,
              isRead: false,
              createdAt: admin.firestore.FieldValue.serverTimestamp(),
            };

            await db
              .collection("users")
              .doc(parentCommentAuthorId)
              .collection("notifications")
              .add(replyNotificationData);

            // Send push notification
            const parentAuthorDoc = await db
              .collection("users")
              .doc(parentCommentAuthorId)
              .get();
            if (parentAuthorDoc.exists) {
              const parentAuthor = parentAuthorDoc.data();
              if (
                parentAuthor.fcmToken &&
                parentAuthor.notificationsEnabled !== false
              ) {
                try {
                  await admin.messaging().send({
                    token: parentAuthor.fcmToken,
                    notification: {
                      title: "New Reply",
                      body: `${commentData.authorName || "Someone"} replied to your comment`,
                    },
                    data: {
                      type: "comment",
                      postId: postId,
                      commentId: commentId,
                    },
                  });
                } catch (messagingError) {
                  console.error(
                    "onComment: Failed to send push notification to parent comment author",
                    {
                      postId,
                      commentId,
                      parentCommentAuthorId,
                      error: messagingError,
                    }
                  );
                }
              }
            }
          }
        }
      }

      // 6. Send push notification to post author if FCM token exists and notifications enabled
      const authorDoc = await db.collection("users").doc(postAuthorId).get();
      if (authorDoc.exists) {
        const author = authorDoc.data();
        if (author.fcmToken && author.notificationsEnabled !== false) {
          try {
            await admin.messaging().send({
              token: author.fcmToken,
              notification: {
                title: "New Comment",
                body: `${commentData.authorName || "Someone"} commented on your post`,
              },
              data: {
                type: "comment",
                postId: postId,
                commentId: commentId,
              },
            });
          } catch (messagingError) {
            console.error("onComment: Failed to send push notification", {
              postId,
              commentId,
              postAuthorId,
              error: messagingError,
            });
            // Don't throw - notification creation succeeded
          }
        }
      }

      console.log("onComment: success", {
        postId,
        commentId,
        commenterId,
        parentCommentId,
        postAuthorId,
      });
      return null;
    } catch (err) {
      console.error("onComment failed", { postId, commentId, err });
      throw err;
    }
  }
);

/**
 * When a comment is deleted, decrement counts.
 * Uses soft delete approach (isActive = false), so we can read the comment data.
 * Uses 2nd gen so we can attach to the "kissangram" database.
 */
exports.onCommentDelete = onDocumentDeleted(
  {
    document: "posts/{postId}/comments/{commentId}",
    database: "kissangram",
  },
  async (event) => {
    const { postId, commentId } = event.params;

    try {
      // Note: onDocumentDeleted doesn't provide the deleted document data
      // We need to use onDocumentUpdated to detect soft deletes (isActive = false)
      // For now, we'll handle soft deletes in onCommentUpdate
      // This function will only handle hard deletes
      console.log("onCommentDelete: hard delete detected", { postId, commentId });
      // We can't determine if it was a reply or top-level comment without the document data
      // So we'll need to handle this differently - either:
      // 1. Use soft delete (isActive = false) and trigger on update
      // 2. Store parentCommentId in a separate collection before deletion
      // For now, we'll implement soft delete approach
      return null;
    } catch (err) {
      console.error("onCommentDelete failed", { postId, commentId, err });
      throw err;
    }
  }
);

/**
 * When a comment is updated, check if it was soft-deleted (isActive = false).
 * This handles the soft delete approach where we set isActive = false instead of deleting.
 */
exports.onCommentUpdate = onDocumentUpdated(
  {
    document: "posts/{postId}/comments/{commentId}",
    database: "kissangram",
  },
  async (event) => {
    const before = event.data.before;
    const after = event.data.after;
    if (!before || !after) {
      return null;
    }

    const beforeData = before.data();
    const afterData = after.data();
    const { postId, commentId } = event.params;

    // Check if comment was soft-deleted (isActive changed from true to false)
    if (beforeData.isActive === true && afterData.isActive === false) {
      try {
        const parentCommentId = afterData.parentCommentId || null;

        if (parentCommentId) {
          // It was a reply - decrement repliesCount on parent comment
          await db
            .collection("posts")
            .doc(postId)
            .collection("comments")
            .doc(parentCommentId)
            .update({
              repliesCount: admin.firestore.FieldValue.increment(-1),
            });
        } else {
          // It was a top-level comment - decrement commentsCount on post
          await db.collection("posts").doc(postId).update({
            commentsCount: admin.firestore.FieldValue.increment(-1),
          });
        }

        console.log("onCommentUpdate: soft delete processed", {
          postId,
          commentId,
          parentCommentId,
        });
        return null;
      } catch (err) {
        console.error("onCommentUpdate: soft delete failed", {
          postId,
          commentId,
          err,
        });
        throw err;
      }
    }

    return null;
  }
);
