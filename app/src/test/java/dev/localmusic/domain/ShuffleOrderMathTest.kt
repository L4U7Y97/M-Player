package dev.localmusic.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuffleOrderMathTest {

    // ---- insert ---------------------------------------------------------------------------------------

    @Test
    fun insert_atEnd_appendsNewIndicesAfterWholePlayOrder() {
        // BUG-1 repro shape: 6 shuffled items, current = last in play order (index 4), 3 items added at the end.
        val order = listOf(2, 0, 5, 1, 3, 4)
        val result = ShuffleOrderMath.insert(order, insertionIndex = 6, insertionCount = 3)
        assertEquals(listOf(2, 0, 5, 1, 3, 4, 6, 7, 8), result)
    }

    @Test
    fun insert_newItemsPlayAfterCurrent_whereverCurrentIs() {
        val order = listOf(3, 1, 4, 0, 2)
        for (current in order) {
            val result = ShuffleOrderMath.insert(order, insertionIndex = 5, insertionCount = 2)
            val after = QueueModel.upNext(result.size, current, result)
            assertTrue("current=$current", after.containsAll(listOf(5, 6)))
        }
    }

    @Test
    fun insert_inMiddle_shiftsLaterIndicesAndKeepsPlayOrder() {
        val order = listOf(2, 0, 3, 1)
        val result = ShuffleOrderMath.insert(order, insertionIndex = 2, insertionCount = 2)
        // old 2 -> 4, old 3 -> 5; new 2, 3 appended.
        assertEquals(listOf(4, 0, 5, 1, 2, 3), result)
    }

    @Test
    fun insert_intoEmpty_isLinear() {
        assertEquals(listOf(0, 1, 2), ShuffleOrderMath.insert(emptyList(), 0, 3))
    }

    @Test
    fun insert_resultIsPermutation() {
        val result = ShuffleOrderMath.insert(listOf(1, 3, 0, 2), 1, 3)
        assertEquals((0 until 7).toSet(), result.toSet())
        assertEquals(7, result.size)
    }

    @Test
    fun insert_zeroCount_unchanged() {
        assertEquals(listOf(1, 0), ShuffleOrderMath.insert(listOf(1, 0), 2, 0))
    }

    // ---- remove ---------------------------------------------------------------------------------------

    @Test
    fun remove_dropsRangeAndShiftsDown() {
        val order = listOf(4, 0, 5, 1, 2, 3)
        val result = ShuffleOrderMath.remove(order, fromIndex = 1, toIndexExclusive = 3)
        // removed 1, 2; 3 -> 1, 4 -> 2, 5 -> 3
        assertEquals(listOf(2, 0, 3, 1), result)
    }

    @Test
    fun remove_all_isEmpty() {
        assertEquals(emptyList<Int>(), ShuffleOrderMath.remove(listOf(1, 0, 2), 0, 3))
    }

    @Test
    fun remove_emptyRange_unchanged() {
        assertEquals(listOf(1, 0), ShuffleOrderMath.remove(listOf(1, 0), 1, 1))
    }

    // ---- move -----------------------------------------------------------------------------------------

    @Test
    fun move_singleItem_keepsPlayPositions() {
        // timeline a b c d (0..3), play order c a d b = 2 0 3 1. Move a (0) to index 2 -> timeline b c a d.
        val result = ShuffleOrderMath.move(listOf(2, 0, 3, 1), fromIndex = 0, toIndexExclusive = 1, newFromIndex = 2)
        // c -> 1, a -> 2, d -> 3, b -> 0: play order still c a d b.
        assertEquals(listOf(1, 2, 3, 0), result)
    }

    @Test
    fun move_invalidRange_unchanged() {
        assertEquals(listOf(1, 0), ShuffleOrderMath.move(listOf(1, 0), 0, 1, 5))
    }
}
