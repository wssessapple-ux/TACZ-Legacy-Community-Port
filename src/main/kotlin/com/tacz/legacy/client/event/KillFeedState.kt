package com.tacz.legacy.client.event

/**
 * 左上角击杀播报（kill feed）的客户端状态。
 *
 * 条目只在攻击者与受害者都是玩家时入列（名字由服务端解析后随报文带来），
 * 生物/靶矿车击杀不进播报。新条目排在最前，超过停留时长淡出后移除。
 */
internal object KillFeedState {
    private data class Entry(
        val attacker: String,
        val killed: String,
        val gunName: String,
        val distance: Float,
        val headShot: Boolean,
        val multiKillCount: Int,
        val timestamp: Long,
    )

    /** Hard cap on the backing queue; the display already truncates via [snapshot]'s maxEntries. */
    private const val MAX_ENTRIES = 32

    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun add(
        attacker: String,
        killed: String,
        gunName: String,
        distance: Float,
        headShot: Boolean,
        multiKillCount: Int = 1,
        now: Long = System.currentTimeMillis(),
    ) {
        if (attacker.isBlank() || killed.isBlank()) {
            return
        }
        entries.addFirst(Entry(attacker, killed, gunName, distance, headShot, multiKillCount, now))
        while (entries.size > MAX_ENTRIES) {
            entries.removeLast()
        }
    }

    /** 返回当前应显示的条目（新的在前），并清掉已过期的。 */
    @Synchronized
    fun snapshot(now: Long, durationMs: Long, maxEntries: Int): List<FeedEntry> {
        val alive = entries.filter { now - it.timestamp <= durationMs }
        entries.clear()
        entries.addAll(alive)
        val fadeMs = 1000L
        return alive.take(maxEntries).map {
            val remain = durationMs - (now - it.timestamp)
            val alpha = if (remain >= fadeMs) 1f else (remain.toFloat() / fadeMs).coerceIn(0f, 1f)
            FeedEntry(it.attacker, it.killed, it.gunName, it.distance, it.headShot, it.multiKillCount, alpha)
        }
    }

    @Synchronized
    fun resetForTests() {
        entries.clear()
    }

    internal data class FeedEntry(
        val attacker: String,
        val killed: String,
        val gunName: String,
        val distance: Float,
        val headShot: Boolean,
        val multiKillCount: Int,
        val alpha: Float,
    )
}
