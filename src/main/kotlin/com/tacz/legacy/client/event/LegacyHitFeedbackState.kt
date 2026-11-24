package com.tacz.legacy.client.event

import java.awt.Color
import kotlin.math.min

internal object LegacyHitFeedbackState {
    internal const val HIT_MARKER_KEEP_TIME_MS: Long = 300L

    internal data class HitMarkerSnapshot(
        val offset: Float,
        val alpha: Float,
        val headShotTint: Boolean,
        val isKill: Boolean,
        val progress: Float,
        val scale: Float,
    )

    internal data class KillAmountSnapshot(
        val text: String,
        val color: Int,
    )

    @Volatile
    private var hitTimestamp: Long = -1L

    @Volatile
    private var killTimestamp: Long = -1L

    @Volatile
    private var headShotTimestamp: Long = -1L

    @Volatile
    private var killAmount: Int = 0

    @Synchronized
    internal fun markHit(now: Long = System.currentTimeMillis()) {
        hitTimestamp = now
    }

    @Synchronized
    internal fun markKill(killAmountTimeoutMs: Long, now: Long = System.currentTimeMillis()): Int {
        if (killAmountTimeoutMs <= 0L || now - killTimestamp > killAmountTimeoutMs) {
            killAmount = 0
        }
        killTimestamp = now
        killAmount += 1
        return killAmount
    }

    @Synchronized
    internal fun markHeadShot(now: Long = System.currentTimeMillis()) {
        headShotTimestamp = now
    }

    @Synchronized
    internal fun currentKillAmount(): Int = killAmount

    @Synchronized
    internal fun currentHitMarkerSnapshot(now: Long, startPosition: Float): HitMarkerSnapshot? {
        val remainHitTime = now - hitTimestamp
        val remainKillTime = now - killTimestamp
        val remainHeadShotTime = now - headShotTimestamp

        val killActive = remainKillTime in 0..HIT_MARKER_KEEP_TIME_MS
        val hitActive = remainHitTime in 0..HIT_MARKER_KEEP_TIME_MS
        if (!killActive && !hitActive) {
            return null
        }

        val isKill = killActive
        val remain = if (isKill) remainKillTime else remainHitTime
        val progress = (remain.toFloat() / HIT_MARKER_KEEP_TIME_MS).coerceIn(0f, 1f)
        val headShot = remainHeadShotTime in 0..HIT_MARKER_KEEP_TIME_MS

        // 前半段保持满亮，后半段线性淡出，避免刚命中就开始掉透明度。
        val alpha = if (progress < 0.5f) 1f else (1f - (progress - 0.5f) / 0.5f)
        // 外扩走 ease-out：起步快、收尾慢。
        val easeOut = 1f - (1f - progress) * (1f - progress)
        val offset = startPosition + easeOut * 4f
        // 弹出回弹：刚触发略放大并随进度回落，击杀再大一档。
        val popAmount = if (isKill) 0.22f else 0.15f
        val scale = 1f + (1f - progress) * popAmount

        return HitMarkerSnapshot(
            offset = offset,
            alpha = alpha,
            headShotTint = headShot,
            isKill = isKill,
            progress = progress,
            scale = scale,
        )
    }

    @Synchronized
    internal fun currentKillAmountSnapshot(now: Long, killAmountTimeoutMs: Long): KillAmountSnapshot? {
        if (killAmountTimeoutMs <= 0L || killAmount <= 0) {
            return null
        }
        val remainTime = now - killTimestamp
        if (remainTime > killAmountTimeoutMs) {
            return null
        }
        val text = if (killAmount < 10) "☠ x 0$killAmount" else "☠ x $killAmount"
        val colorCount = 30.0f
        val fadeOutTime = killAmountTimeoutMs / 3.0 * 2.0
        val hue = (1.0f - min(killAmount / colorCount, 1.0f)) * 0.15f
        var alpha = 0xFF
        if (remainTime > fadeOutTime) {
            val denominator = (killAmountTimeoutMs - fadeOutTime).coerceAtLeast(1.0)
            alpha = 0xFF - (((remainTime - fadeOutTime) / denominator) * 0xF0).toInt().coerceIn(0, 0xF0)
        }
        val rgb = Color.HSBtoRGB(hue, 0.75f, 1.0f) and 0x00FFFFFF
        return KillAmountSnapshot(text = text, color = rgb or (alpha.coerceIn(0, 0xFF) shl 24))
    }

    @Synchronized
    internal fun resetForTests() {
        hitTimestamp = -1L
        killTimestamp = -1L
        headShotTimestamp = -1L
        killAmount = 0
    }
}