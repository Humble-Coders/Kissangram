package com.kissangram.util

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.util.LinkedHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * LRU cache for ExoPlayer instances.
 * Reuses players when scrolling back to videos—avoids re-buffering.
 * Thread-safe; max capacity prevents unbounded memory growth.
 */
object ExoPlayerCache {
    private const val MAX_CAPACITY = 5
    private val lock = ReentrantLock()
    private val cache = LinkedHashMap<String, CachedPlayer>(MAX_CAPACITY, 0.75f, true)
    
    private data class CachedPlayer(
        val player: ExoPlayer,
        var lastAccess: Long = System.currentTimeMillis()
    )
    
    fun getPlayer(context: Context, url: String): ExoPlayer {
        lock.withLock {
            cache[url]?.let { cached ->
                cached.lastAccess = System.currentTimeMillis()
                return cached.player
            }
            
            if (cache.size >= MAX_CAPACITY) {
                val oldest = cache.keys.firstOrNull()
                if (oldest != null) {
                    cache.remove(oldest)?.player?.apply {
                        stop()
                        release()
                    }
                }
            }
            
            val player = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(url))
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
            }
            cache[url] = CachedPlayer(player)
            return player
        }
    }
}
