package dev.localmusic.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistOrderingTest {

    private fun items(vararg ids: String) = ids.mapIndexed { i, id -> PlaylistItem(entryId = i + 1L, trackId = id, position = i) }

    private fun ids(list: List<PlaylistItem>) = list.map { it.trackId }

    private fun assertRenumbered(list: List<PlaylistItem>) =
        assertEquals(list.indices.toList(), list.map { it.position })

    @Test
    fun move_down() {
        val r = PlaylistOrdering.move(items("a", "b", "c", "d"), 0, 2)
        assertEquals(listOf("b", "c", "a", "d"), ids(r))
        assertRenumbered(r)
    }

    @Test
    fun move_up() {
        val r = PlaylistOrdering.move(items("a", "b", "c", "d"), 3, 1)
        assertEquals(listOf("a", "d", "b", "c"), ids(r))
        assertRenumbered(r)
    }

    @Test
    fun move_firstToLastAndLastToFirst() {
        assertEquals(listOf("b", "c", "a"), ids(PlaylistOrdering.move(items("a", "b", "c"), 0, 2)))
        assertEquals(listOf("c", "a", "b"), ids(PlaylistOrdering.move(items("a", "b", "c"), 2, 0)))
    }

    @Test
    fun move_keepsEntryIds() {
        val r = PlaylistOrdering.move(items("a", "b", "c"), 0, 1)
        assertEquals(listOf(2L, 1L, 3L), r.map { it.entryId })
    }

    @Test
    fun move_outOfRangeOrSame_noOpButRenumbered() {
        val base = items("a", "b", "c")
        assertEquals(base, PlaylistOrdering.move(base, -1, 1))
        assertEquals(base, PlaylistOrdering.move(base, 0, 3))
        assertEquals(base, PlaylistOrdering.move(base, 1, 1))
        val gappy = listOf(PlaylistItem(1, "a", 5), PlaylistItem(2, "b", 9))
        val r = PlaylistOrdering.move(gappy, 0, 7)
        assertEquals(listOf("a", "b"), ids(r))
        assertRenumbered(r)
    }

    @Test
    fun move_usesListOrderNotPositionField() {
        val shuffledPositions = listOf(PlaylistItem(1, "a", 2), PlaylistItem(2, "b", 0), PlaylistItem(3, "c", 1))
        val r = PlaylistOrdering.move(shuffledPositions, 0, 2)
        assertEquals(listOf("b", "c", "a"), ids(r))
        assertRenumbered(r)
    }

    @Test
    fun remove_middleFirstLast() {
        assertEquals(listOf("a", "c"), ids(PlaylistOrdering.remove(items("a", "b", "c"), 1)))
        assertEquals(listOf("b", "c"), ids(PlaylistOrdering.remove(items("a", "b", "c"), 0)))
        val r = PlaylistOrdering.remove(items("a", "b", "c"), 2)
        assertEquals(listOf("a", "b"), ids(r))
        assertRenumbered(r)
    }

    @Test
    fun remove_renumbersRemaining() {
        val r = PlaylistOrdering.remove(items("a", "b", "c", "d"), 0)
        assertEquals(listOf(0, 1, 2), r.map { it.position })
        assertEquals(listOf(2L, 3L, 4L), r.map { it.entryId })
    }

    @Test
    fun remove_onlyEntry_empty() {
        assertEquals(emptyList<PlaylistItem>(), PlaylistOrdering.remove(items("a"), 0))
    }

    @Test
    fun remove_outOfRange_noOp() {
        val base = items("a", "b")
        assertEquals(base, PlaylistOrdering.remove(base, 2))
        assertEquals(base, PlaylistOrdering.remove(base, -1))
        assertEquals(emptyList<PlaylistItem>(), PlaylistOrdering.remove(emptyList(), 0))
    }

    @Test
    fun remove_duplicateRemovesOnlyThatIndex() {
        assertEquals(listOf("a", "b"), ids(PlaylistOrdering.remove(items("a", "b", "a"), 2)))
        assertEquals(listOf("b", "a"), ids(PlaylistOrdering.remove(items("a", "b", "a"), 0)))
    }

    @Test
    fun append_toEmpty() {
        val r = PlaylistOrdering.append(emptyList(), listOf("x", "y"))
        assertEquals(listOf(PlaylistItem(0, "x", 0), PlaylistItem(0, "y", 1)), r)
    }

    @Test
    fun append_afterExisting_newEntriesHaveIdZero() {
        val r = PlaylistOrdering.append(items("a", "b"), listOf("c"))
        assertEquals(listOf("a", "b", "c"), ids(r))
        assertEquals(listOf(1L, 2L, 0L), r.map { it.entryId })
        assertRenumbered(r)
    }

    @Test
    fun append_duplicatesAllowed() {
        val r = PlaylistOrdering.append(items("a"), listOf("a", "a"))
        assertEquals(listOf("a", "a", "a"), ids(r))
        assertRenumbered(r)
    }

    @Test
    fun append_nothing_renumbersOnly() {
        assertEquals(items("a", "b"), PlaylistOrdering.append(items("a", "b"), emptyList()))
    }

    @Test
    fun normalize_sortsByPositionStableThenRenumbers() {
        val raw = listOf(PlaylistItem(1, "c", 10), PlaylistItem(2, "a", 3), PlaylistItem(3, "b", 3), PlaylistItem(4, "d", 42))
        val r = PlaylistOrdering.normalize(raw)
        assertEquals(listOf("a", "b", "c", "d"), ids(r))
        assertRenumbered(r)
    }

    @Test
    fun renumber_setsIndexKeepsOrder() {
        val r = PlaylistOrdering.renumber(listOf(PlaylistItem(1, "x", 7), PlaylistItem(2, "y", 7)))
        assertEquals(listOf(PlaylistItem(1, "x", 0), PlaylistItem(2, "y", 1)), r)
    }

    @Test
    fun moveUpMoveDown_buttonsSequence() {
        // UI "Move up"/"Move down" = move(i, i-1) / move(i, i+1); first-up and last-down are no-ops
        var list = items("a", "b", "c")
        list = PlaylistOrdering.move(list, 0, -1)
        assertEquals(listOf("a", "b", "c"), ids(list))
        list = PlaylistOrdering.move(list, 2, 3)
        assertEquals(listOf("a", "b", "c"), ids(list))
        list = PlaylistOrdering.move(list, 2, 1)
        list = PlaylistOrdering.move(list, 0, 1)
        assertEquals(listOf("c", "a", "b"), ids(list))
        assertRenumbered(list)
    }
}
