package com.droidforge.gamepadmouse.input

/**
 * Detects a multi-button chord (e.g. Start+Select) from a stream of key down/up events.
 *
 * Fires once when all chord keys have been freshly pressed (each must have a new
 * onKeyDown since the last fire/re-arm). Re-arms only after all keys that were held
 * when the chord fired have been released.
 *
 * Supports optional hold duration: if holdDurationMs > 0, the chord must be held for
 * that long before firing.
 */
class ChordDetector(chord: Set<Int>, var holdDurationMs: Long = 0L) {
    var chord: Set<Int> = chord
        set(value) { field = value; reset() }

    /** Keys from chord still pending release after the last fire. Empty = armed. */
    private val pendingRelease = HashSet<Int>()
    /** Chord keys freshly pressed since the last re-arm. */
    private val freshPressed = HashSet<Int>()
    /** Timestamp when the full chord was first completed (for hold duration check). */
    private var chordCompletedAt = 0L

    /** @return true if this key event completed the chord (caller should consume it). */
    fun onKeyDown(keyCode: Int): Boolean {
        if (keyCode !in chord) return false
        if (pendingRelease.isNotEmpty()) return false   // not yet re-armed
        
        // Track fresh presses
        if (!freshPressed.contains(keyCode)) {
            freshPressed.add(keyCode)
        }
        
        if (freshPressed.containsAll(chord)) {
            // Chord is complete
            if (holdDurationMs <= 0L) {
                // Instant toggle
                pendingRelease.addAll(freshPressed)
                freshPressed.clear()
                chordCompletedAt = 0L
                return true
            } else {
                // Start hold timer on first completion
                if (chordCompletedAt == 0L) {
                    chordCompletedAt = System.currentTimeMillis()
                }
                // Check if held long enough
                if (System.currentTimeMillis() - chordCompletedAt >= holdDurationMs) {
                    pendingRelease.addAll(freshPressed)
                    freshPressed.clear()
                    chordCompletedAt = 0L
                    return true
                }
                // Still holding, not ready yet - consume the event but don't toggle
                return false
            }
        }
        return false
    }

    /** @return true if this key belongs to the chord (caller may want to consume the release). */
    fun onKeyUp(keyCode: Int): Boolean {
        if (keyCode !in chord) return false
        pendingRelease.remove(keyCode)
        // freshPressed tracks only post-rearm presses; a key-up clears its fresh status
        freshPressed.remove(keyCode)
        // Reset hold timer if user releases before completing hold duration
        chordCompletedAt = 0L
        return true
    }

    fun reset() { pendingRelease.clear(); freshPressed.clear(); chordCompletedAt = 0L }
}
