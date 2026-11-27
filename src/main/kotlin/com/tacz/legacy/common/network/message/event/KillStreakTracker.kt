package com.tacz.legacy.common.network.message.event

import java.util.LinkedHashMap

/**
 * 多杀（multi-kill）计数状态机：同一攻击者在时间窗口内连续击杀，第 2 次起计数递增；
 * 超过窗口未再击杀则重置为首杀。纯逻辑，时间戳由外部传入，便于服务端调用与 JVM 单测。
 */
internal object KillStreakTracker {
    private data class Record(var lastKillMs: Long, var count: Int)

    private const val MAX_TRACKED = 256

    private val records = LinkedHashMap<String, Record>()

    /** 记录一次击杀，返回当前连续计数（1 = 首杀，2 = 双杀，依次递增）。 */
    @Synchronized
    internal fun record(attackerKey: String, nowMs: Long, windowMs: Long): Int {
        val existing = records[attackerKey]
        val count = if (existing != null && nowMs - existing.lastKillMs <= windowMs) {
            existing.count + 1
        } else {
            1
        }
        records[attackerKey] = Record(nowMs, count)
        evict(nowMs, windowMs)
        return count
    }

    @Synchronized
    internal fun reset() {
        records.clear()
    }

    private fun evict(nowMs: Long, windowMs: Long) {
        if (records.size <= MAX_TRACKED) return
        records.entries.removeIf { nowMs - it.value.lastKillMs > windowMs }
    }
}
