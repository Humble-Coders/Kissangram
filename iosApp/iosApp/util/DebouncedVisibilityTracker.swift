import Foundation
import SwiftUI

/// Batches onAppear/onDisappear events so we don't trigger a full view re-render on every
/// cell appear/disappear during scroll. During fast scroll this can be 20-40+ times/second;
/// batching reduces that to ~10-12 updates/second (every 80ms).
/// Critical for smooth scrolling when feed has videos—each re-render re-evaluates all
/// PostCardViews including their FeedVideoPlayer hierarchy.
@MainActor
final class DebouncedVisibilityTracker: ObservableObject {
    @Published private(set) var visibleIndices: Set<Int> = []
    
    private var pendingAdd: Set<Int> = []
    private var pendingRemove: Set<Int> = []
    private var applyTask: Task<Void, Never>?
    
    private let batchInterval: UInt64
    
    init(batchIntervalMs: Int = 80) {
        self.batchInterval = UInt64(batchIntervalMs) * 1_000_000
    }
    
    func markAppeared(_ index: Int) {
        pendingAdd.insert(index)
        pendingRemove.remove(index)
        scheduleApply()
    }
    
    func markDisappeared(_ index: Int) {
        pendingRemove.insert(index)
        pendingAdd.remove(index)
        scheduleApply()
    }
    
    private func scheduleApply() {
        guard applyTask == nil else { return }
        applyTask = Task {
            do {
                try await Task.sleep(nanoseconds: batchInterval)
            } catch {
                applyTask = nil
                return
            }
            guard !Task.isCancelled else {
                applyTask = nil
                return
            }
            apply()
        }
    }
    
    private func apply() {
        visibleIndices = visibleIndices.union(pendingAdd).subtracting(pendingRemove)
        pendingAdd.removeAll()
        pendingRemove.removeAll()
        applyTask = nil
    }
}
