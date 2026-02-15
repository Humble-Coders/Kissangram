import AVFoundation
import Foundation

/// LRU cache for AVPlayer instances.
/// Reuses players when scrolling back to videos—avoids re-buffering.
/// Thread-safe; max capacity prevents unbounded memory growth.
final class VideoPlayerCache {
    static let shared = VideoPlayerCache()
    
    private let lock = NSLock()
    private var cache: [String: CachedPlayer] = [:]
    private var accessOrder: [String] = []
    
    /// Max number of cached players. Videos are memory-heavy; keep small for scalability.
    private let maxCapacity: Int = 5
    
    private struct CachedPlayer {
        let player: AVPlayer
        var lastAccess: Date
    }
    
    private init() {}
    
    /// Returns a player for the given URL. Reuses cached player if available.
    /// AVPlayer creation happens outside the lock to avoid blocking scroll when
    /// multiple deferred setups complete around the same time.
    func player(for url: URL) -> AVPlayer {
        let key = url.absoluteString
        lock.lock()
        if var cached = cache[key] {
            cached.lastAccess = Date()
            cache[key] = cached
            moveToFront(key: key)
            let player = cached.player
            lock.unlock()
            return player
        }
        // Evict if at capacity before releasing lock
        if cache.count >= maxCapacity, let oldest = accessOrder.last {
            if let old = cache.removeValue(forKey: oldest) {
                old.player.pause()
                old.player.replaceCurrentItem(with: nil)
            }
            accessOrder.removeAll { $0 == oldest }
        }
        lock.unlock()
        
        // Create AVPlayer outside lock—can block on I/O; avoids contention during scroll
        let newPlayer = AVPlayer(url: url)
        newPlayer.actionAtItemEnd = .none
        
        lock.lock()
        cache[key] = CachedPlayer(player: newPlayer, lastAccess: Date())
        accessOrder.insert(key, at: 0)
        lock.unlock()
        return newPlayer
    }
    
    private func moveToFront(key: String) {
        accessOrder.removeAll { $0 == key }
        accessOrder.insert(key, at: 0)
    }
    
    /// Call when view disappears to pause (player stays in cache for reuse).
    func pausePlayer(_ player: AVPlayer) {
        player.pause()
    }
}
