package com.tacz.legacy.client.model.mesh

import java.util.Locale

/**
 * Process-wide counters for the static-mesh VBO path, surfaced on the F3 debug
 * screen so rendering regressions can be diagnosed in-game.
 *
 * Counters are incremented from the render thread; reads from the debug overlay
 * are benign races.
 */
public object MeshVboStats {

    /** Frames drawn from an uploaded VBO. */
    @Volatile public var vboHits: Int = 0
        private set

    /** Frames drawn through the immediate-mode fallback. */
    @Volatile public var immediateFallbacks: Int = 0
        private set

    /** VBO upload / rebuild events. */
    @Volatile public var rebuilds: Int = 0
        private set

    /** Recoverable failures: buffer dropped, scheduled for retry next frame. */
    @Volatile public var recoverableFailures: Int = 0
        private set

    /** Persistent failures: permanently degraded to immediate mode, no retry. */
    @Volatile public var persistentFailures: Int = 0
        private set

    /** Currently uploaded VBOs and their combined GPU memory. */
    @Volatile public var liveVboCount: Int = 0
        private set

    @Volatile public var liveVboBytes: Long = 0L
        private set

    public fun recordHit(): Unit { vboHits++ }

    public fun recordFallback(): Unit { immediateFallbacks++ }

    public fun recordRebuild(): Unit { rebuilds++ }

    public fun recordRecoverableFailure(): Unit { recoverableFailures++ }

    public fun recordPersistentFailure(): Unit { persistentFailures++ }

    public fun addLive(bytes: Long): Unit {
        liveVboCount++
        liveVboBytes += bytes
    }

    public fun removeLive(bytes: Long): Unit {
        liveVboCount = maxOf(0, liveVboCount - 1)
        liveVboBytes = maxOf(0L, liveVboBytes - bytes)
    }

    /** Lines appended to the F3 right-hand debug list. */
    public fun debugLines(): List<String> {
        val total = vboHits + immediateFallbacks
        val rate = if (total == 0) 0.0 else vboHits * 100.0 / total
        return listOf(
            "TACZ VBO: hit $vboHits / fallback $immediateFallbacks (${String.format(Locale.ROOT, "%.1f", rate)}%)",
            "TACZ VBO: rebuild $rebuilds | recoverable $recoverableFailures | failed $persistentFailures",
            "TACZ VBO: buffers $liveVboCount, ${String.format(Locale.ROOT, "%.1f", liveVboBytes / 1024.0)} KB"
        )
    }
}
