package dev.localmusic.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackRestoreTest {

    private fun snapshot(
        current: Int = 2,
        position: Long = 42_000,
        shuffle: Boolean = false,
        order: List<Int> = emptyList(),
    ) = PlaybackSnapshot(
        trackIds = listOf("a", "b", "c", "d", "e"),
        currentIndex = current,
        positionMs = position,
        shuffle = shuffle,
        shuffleOrder = order,
        repeatMode = RepeatMode.OFF,
    )

    @Test
    fun allPlayable_keepsEverything() {
        val r = PlaybackRestore.restore(snapshot()) { true }!!
        assertEquals(listOf(0, 1, 2, 3, 4), r.kept)
        assertEquals(2, r.currentIndex)
        assertEquals(42_000, r.positionMs)
        assertEquals(emptyList<Int>(), r.shuffleOrder)
    }

    @Test
    fun droppedItemBeforeCurrent_currentShiftsAndKeepsPosition() {
        val r = PlaybackRestore.restore(snapshot()) { it != 0 }!!
        assertEquals(listOf(1, 2, 3, 4), r.kept)
        assertEquals(1, r.currentIndex)
        assertEquals(42_000, r.positionMs)
    }

    @Test
    fun droppedCurrent_nextSurvivingBecomesCurrentAtZero() {
        val r = PlaybackRestore.restore(snapshot()) { it != 2 && it != 3 }!!
        assertEquals(listOf(0, 1, 4), r.kept)
        assertEquals(2, r.currentIndex) // old index 4
        assertEquals(0, r.positionMs)
    }

    @Test
    fun droppedCurrentAtEnd_firstSurvivingBecomesCurrent() {
        val r = PlaybackRestore.restore(snapshot(current = 4)) { it != 4 }!!
        assertEquals(0, r.currentIndex)
        assertEquals(0, r.positionMs)
    }

    @Test
    fun shuffle_orderRemappedOntoSurvivors() {
        val s = snapshot(current = 3, shuffle = true, order = listOf(3, 0, 4, 1, 2))
        val r = PlaybackRestore.restore(s) { it != 0 }!!
        assertEquals(listOf(1, 2, 3, 4), r.kept)
        assertEquals(2, r.currentIndex) // old 3
        assertEquals(listOf(2, 3, 0, 1), r.shuffleOrder) // old 3, 4, 1, 2
    }

    @Test
    fun shuffle_droppedCurrent_followsShuffleOrder() {
        val s = snapshot(current = 3, shuffle = true, order = listOf(3, 0, 4, 1, 2))
        val r = PlaybackRestore.restore(s) { it != 3 && it != 0 }!!
        assertEquals(listOf(1, 2, 4), r.kept)
        assertEquals(2, r.currentIndex) // old 4 is next after 3 (and 0) in the shuffle order
    }

    @Test
    fun shuffle_invalidOrder_freshOrderStartsAtCurrent() {
        val s = snapshot(current = 1, shuffle = true, order = listOf(0, 0, 1))
        val r = PlaybackRestore.restore(s) { true }!!
        assertEquals(5, r.shuffleOrder.size)
        assertEquals(setOf(0, 1, 2, 3, 4), r.shuffleOrder.toSet())
        assertEquals(1, r.shuffleOrder.first())
    }

    @Test
    fun nothingPlayable_null() {
        assertNull(PlaybackRestore.restore(snapshot()) { false })
    }
}
