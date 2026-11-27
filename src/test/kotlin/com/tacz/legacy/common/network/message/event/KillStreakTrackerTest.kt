package com.tacz.legacy.common.network.message.event

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class KillStreakTrackerTest {

    @Before
    fun setUp() {
        KillStreakTracker.reset()
    }

    @Test
    fun `consecutive kills within window increment count`() {
        val t = 1000L
        assertEquals(1, KillStreakTracker.record("a", t, 5000))
        assertEquals(2, KillStreakTracker.record("a", t + 1000, 5000))
        assertEquals(3, KillStreakTracker.record("a", t + 2000, 5000))
    }

    @Test
    fun `kill outside window resets count`() {
        val t = 1000L
        assertEquals(1, KillStreakTracker.record("a", t, 5000))
        assertEquals(2, KillStreakTracker.record("a", t + 1000, 5000))
        assertEquals(1, KillStreakTracker.record("a", t + 6001, 5000))
    }

    @Test
    fun `different attackers tracked independently`() {
        val t = 1000L
        assertEquals(1, KillStreakTracker.record("a", t, 5000))
        assertEquals(1, KillStreakTracker.record("b", t, 5000))
        assertEquals(2, KillStreakTracker.record("a", t + 500, 5000))
        assertEquals(2, KillStreakTracker.record("b", t + 500, 5000))
    }

    @Test
    fun `kill exactly at window boundary continues streak`() {
        val t = 1000L
        assertEquals(1, KillStreakTracker.record("a", t, 5000))
        assertEquals(2, KillStreakTracker.record("a", t + 5000, 5000))
    }
}
