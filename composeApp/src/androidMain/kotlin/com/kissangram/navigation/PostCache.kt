package com.kissangram.navigation

import com.kissangram.model.Post

/**
 * Simple cache to pass Post objects through navigation
 * Since Navigation Compose doesn't support passing complex objects through routes,
 * we use this cache to temporarily store posts when navigating to detail screens
 */
object PostCache {
    private val cache = mutableMapOf<String, Post>()
    
    fun put(postId: String, post: Post) {
        cache[postId] = post
    }
    
    fun get(postId: String): Post? {
        return cache.remove(postId) // Remove after getting to prevent memory leaks
    }
    
    fun clear() {
        cache.clear()
    }
}
