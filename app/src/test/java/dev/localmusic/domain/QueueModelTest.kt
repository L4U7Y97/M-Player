package dev.localmusic.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueModelTest {

    private val q = listOf("a", "b", "c", "d", "e")

    // ---- move -----------------------------------------------------------------------------------------

    @Test
    fun move_currentItemFollowsToNewIndex() {
        val s = QueueModel.move(q, currentIndex = 1, from = 1, to = 3)
        assertEquals(listOf("a", "c", "d", "b", "e"), s.items)
        assertEquals(3, s.currentIndex)
        assertEquals("b", s.items[s.currentIndex])
    }

    @Test
    fun move_currentItemToFirst() {
        val s = QueueModel.move(q, currentIndex = 4, from = 4, to = 0)
        assertEquals(listOf("e", "a", "b", "c", "d"), s.items)
        assertEquals(0, s.currentIndex)
    }

    @Test
    fun move_itemFromBeforeCurrentToAfter_currentShiftsDown() {
        val s = QueueModel.move(q, currentIndex = 2, from = 0, to = 4)
        assertEquals(listOf("b", "c", "d", "e", "a"), s.items)
        assertEquals(1, s.currentIndex)
        assertEquals("c", s.items[s.currentIndex])
    }

    @Test
    fun move_itemFromBeforeCurrentOntoCurrentIndex_currentShiftsDown() {
        val s = QueueModel.move(q, currentIndex = 2, from = 0, to = 2)
        assertEquals(listOf("b", "c", "a", "d", "e"), s.items)
        assertEquals("c", s.items[s.currentIndex])
    }

    @Test
    fun move_itemFromAfterCurrentToBefore_currentShiftsUp() {
        val s = QueueModel.move(q, currentIndex = 2, from = 4, to = 0)
        assertEquals(listOf("e", "a", "b", "c", "d"), s.items)
        assertEquals(3, s.currentIndex)
        assertEquals("c", s.items[s.currentIndex])
    }

    @Test
    fun move_itemFromAfterCurrentOntoCurrentIndex_currentShiftsUp() {
        val s = QueueModel.move(q, currentIndex = 2, from = 3, to = 2)
        assertEquals(listOf("a", "b", "d", "c", "e"), s.items)
        assertEquals("c", s.items[s.currentIndex])
    }

    @Test
    fun move_bothSidesOfCurrentUnaffected() {
        val before = QueueModel.move(q, currentIndex = 3, from = 0, to = 1)
        assertEquals(listOf("b", "a", "c", "d", "e"), before.items)
        assertEquals(3, before.currentIndex)
        val after = QueueModel.move(q, currentIndex = 1, from = 4, to = 2)
        assertEquals(listOf("a", "b", "e", "c", "d"), after.items)
        assertEquals(1, after.currentIndex)
    }

    @Test
    fun move_currentAlwaysPointsAtSameItem_exhaustive() {
        for (cur in q.indices) for (from in q.indices) for (to in q.indices) {
            val s = QueueModel.move(q, cur, from, to)
            assertEquals("cur=$cur from=$from to=$to", q[cur], s.items[s.currentIndex])
            assertEquals(q[from], s.items[to])
            assertEquals(q.size, s.items.size)
        }
    }

    @Test
    fun move_outOfRangeOrSame_noOp() {
        assertEquals(QueueState(q, 2), QueueModel.move(q, 2, -1, 0))
        assertEquals(QueueState(q, 2), QueueModel.move(q, 2, 0, 5))
        assertEquals(QueueState(q, 2), QueueModel.move(q, 2, 5, 0))
        assertEquals(QueueState(q, 2), QueueModel.move(q, 2, 3, 3))
    }

    @Test
    fun move_emptyQueue_noOp() {
        assertEquals(QueueState(emptyList<String>(), -1), QueueModel.move(emptyList<String>(), -1, 0, 0))
    }

    @Test
    fun move_doesNotMutateInput() {
        val input = mutableListOf("a", "b", "c")
        QueueModel.move(input, 0, 0, 2)
        assertEquals(listOf("a", "b", "c"), input)
    }

    // ---- remove ---------------------------------------------------------------------------------------

    @Test
    fun remove_beforeCurrent_currentShiftsDown() {
        val s = QueueModel.remove(q, currentIndex = 3, index = 0)
        assertEquals(listOf("b", "c", "d", "e"), s.items)
        assertEquals(2, s.currentIndex)
        assertEquals("d", s.items[s.currentIndex])
    }

    @Test
    fun remove_afterCurrent_currentUnchanged() {
        val s = QueueModel.remove(q, currentIndex = 1, index = 4)
        assertEquals(listOf("a", "b", "c", "d"), s.items)
        assertEquals(1, s.currentIndex)
    }

    @Test
    fun remove_currentMiddle_nextItemBecomesCurrent() {
        val s = QueueModel.remove(q, currentIndex = 2, index = 2)
        assertEquals(listOf("a", "b", "d", "e"), s.items)
        assertEquals(2, s.currentIndex)
        assertEquals("d", s.items[s.currentIndex])
    }

    @Test
    fun remove_currentFirst_nextItemBecomesCurrent() {
        val s = QueueModel.remove(q, currentIndex = 0, index = 0)
        assertEquals(0, s.currentIndex)
        assertEquals("b", s.items[s.currentIndex])
    }

    @Test
    fun remove_currentLast_wrapsToFirst() {
        val s = QueueModel.remove(q, currentIndex = 4, index = 4)
        assertEquals(listOf("a", "b", "c", "d"), s.items)
        assertEquals(0, s.currentIndex)
    }

    @Test
    fun remove_onlyItem_emptyWithMinusOne() {
        assertEquals(QueueState(emptyList<String>(), -1), QueueModel.remove(listOf("a"), 0, 0))
    }

    @Test
    fun remove_outOfRange_noOp() {
        assertEquals(QueueState(q, 1), QueueModel.remove(q, 1, 5))
        assertEquals(QueueState(q, 1), QueueModel.remove(q, 1, -1))
        assertEquals(QueueState(emptyList<String>(), -1), QueueModel.remove(emptyList<String>(), -1, 0))
    }

    @Test
    fun remove_untilEmpty() {
        var s = QueueState(listOf("a", "b", "c"), 1)
        while (s.items.isNotEmpty()) {
            s = QueueModel.remove(s.items, s.currentIndex, s.currentIndex.coerceAtLeast(0))
            if (s.items.isNotEmpty()) assertEquals(true, s.currentIndex in s.items.indices)
        }
        assertEquals(-1, s.currentIndex)
    }

    // ---- append ---------------------------------------------------------------------------------------

    @Test
    fun append_addsAtEndKeepsCurrent() {
        val s = QueueModel.append(listOf("a", "b"), 1, listOf("c", "d"))
        assertEquals(QueueState(listOf("a", "b", "c", "d"), 1), s)
    }

    @Test
    fun append_toEmptyQueue_currentZero() {
        assertEquals(QueueState(listOf("x"), 0), QueueModel.append(emptyList(), -1, listOf("x")))
    }

    @Test
    fun append_negativeCurrent_becomesZero() {
        assertEquals(0, QueueModel.append(listOf("a"), -1, listOf("b")).currentIndex)
    }

    @Test
    fun append_emptyNewItems_noOp() {
        assertEquals(QueueState(listOf("a"), 0), QueueModel.append(listOf("a"), 0, emptyList()))
        assertEquals(QueueState(emptyList<String>(), -1), QueueModel.append(emptyList<String>(), -1, emptyList()))
    }

    @Test
    fun append_duplicatesAllowed() {
        assertEquals(listOf("a", "a"), QueueModel.append(listOf("a"), 0, listOf("a")).items)
    }

    // ---- play order / up next -------------------------------------------------------------------------

    @Test
    fun playOrder_linearWithoutShuffle() {
        assertEquals(listOf(0, 1, 2, 3), QueueModel.playOrder(4))
    }

    @Test
    fun playOrder_usesValidShuffledPermutation() {
        assertEquals(listOf(2, 0, 3, 1), QueueModel.playOrder(4, listOf(2, 0, 3, 1)))
    }

    @Test
    fun playOrder_invalidShuffle_fallsBackToLinear() {
        assertEquals(listOf(0, 1, 2), QueueModel.playOrder(3, listOf(0, 1)))
        assertEquals(listOf(0, 1, 2), QueueModel.playOrder(3, listOf(0, 0, 1)))
        assertEquals(listOf(0, 1, 2), QueueModel.playOrder(3, listOf(0, 1, 3)))
    }

    @Test
    fun playOrder_zeroOrNegativeCount_empty() {
        assertEquals(emptyList<Int>(), QueueModel.playOrder(0))
        assertEquals(emptyList<Int>(), QueueModel.playOrder(-2, listOf()))
    }

    @Test
    fun upNext_excludesCurrentAndEarlier() {
        assertEquals(listOf(3, 4), QueueModel.upNext(5, 2))
        assertEquals(emptyList<Int>(), QueueModel.upNext(5, 4))
    }

    @Test
    fun upNext_followsShuffledOrder() {
        assertEquals(listOf(3, 1), QueueModel.upNext(4, 0, listOf(2, 0, 3, 1)))
        assertEquals(listOf(0, 3, 1), QueueModel.upNext(4, 2, listOf(2, 0, 3, 1)))
    }

    @Test
    fun upNext_currentNotPresent_wholeOrder() {
        assertEquals(listOf(0, 1, 2), QueueModel.upNext(3, -1))
        assertEquals(emptyList<Int>(), QueueModel.upNext(0, 0))
    }

    // ---- round 3: "Shuffle all" order starts at the chosen item and contains every item once ----

    @Test
    fun shuffledOrderStartingWith_isPermutationStartingAtFirst() {
        for (seed in 0 until 50) {
            for (count in 1..8) {
                val first = seed % count
                val order = QueueModel.shuffledOrderStartingWith(count, first, kotlin.random.Random(seed))
                assertEquals(first, order.first())
                assertEquals((0 until count).toList(), order.sorted())
            }
        }
    }

    @Test
    fun shuffledOrderStartingWith_edgeCases() {
        assertEquals(emptyList<Int>(), QueueModel.shuffledOrderStartingWith(0, 0))
        assertEquals(listOf(0), QueueModel.shuffledOrderStartingWith(1, 0))
        val noFirst = QueueModel.shuffledOrderStartingWith(4, 9, kotlin.random.Random(1))
        assertEquals(listOf(0, 1, 2, 3), noFirst.sorted())
    }
}
