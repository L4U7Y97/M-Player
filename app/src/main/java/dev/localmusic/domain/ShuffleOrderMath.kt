package dev.localmusic.domain

/**
 * Pure index math for the player's shuffle order (BUG-1). A shuffle order is the play order: a permutation of the
 * timeline indices `0 until n`. These functions mirror Media3's `ShuffleOrder.cloneAndInsert / cloneAndRemove /
 * cloneAndMove` contracts, but keep the existing play order intact instead of re-randomising it:
 * - inserted items go to the **end** of the play order (in insertion order), so with shuffle on, "Add to queue"
 *   always plays the new tracks after the current one (and after everything already queued);
 * - removed items simply drop out; moved items keep their place in the play order.
 */
object ShuffleOrderMath {

    /**
     * Timeline items `[insertionIndex, insertionIndex + insertionCount)` were inserted. Existing indices at or after
     * [insertionIndex] shift up by [insertionCount]; the new indices are appended to the end of the play order.
     * [insertionIndex] is clamped to `0..order.size`; insertionCount <= 0 -> unchanged copy.
     */
    fun insert(order: List<Int>, insertionIndex: Int, insertionCount: Int): List<Int> {
        if (insertionCount <= 0) return order.toList()
        val at = insertionIndex.coerceIn(0, order.size)
        val shifted = order.map { if (it >= at) it + insertionCount else it }
        return shifted + (at until at + insertionCount)
    }

    /**
     * Timeline items `[fromIndex, toIndexExclusive)` were removed. They drop out of the play order and indices after
     * the range shift down. An empty or invalid range -> unchanged copy.
     */
    fun remove(order: List<Int>, fromIndex: Int, toIndexExclusive: Int): List<Int> {
        if (fromIndex < 0 || toIndexExclusive <= fromIndex) return order.toList()
        val count = toIndexExclusive - fromIndex
        return order
            .filter { it < fromIndex || it >= toIndexExclusive }
            .map { if (it >= toIndexExclusive) it - count else it }
    }

    /**
     * Timeline items `[fromIndex, toIndexExclusive)` were moved so the range now starts at [newFromIndex]
     * (`Player.moveMediaItems` semantics). Every item keeps its position in the play order; only the indices are
     * renumbered. An invalid range -> unchanged copy.
     */
    fun move(order: List<Int>, fromIndex: Int, toIndexExclusive: Int, newFromIndex: Int): List<Int> {
        val n = order.size
        val count = toIndexExclusive - fromIndex
        if (fromIndex < 0 || count <= 0 || toIndexExclusive > n || newFromIndex < 0 || newFromIndex + count > n) {
            return order.toList()
        }
        val timeline = (0 until n).toMutableList()
        val moved = timeline.subList(fromIndex, toIndexExclusive).toList()
        repeat(count) { timeline.removeAt(fromIndex) }
        timeline.addAll(newFromIndex, moved)
        val newIndexOf = IntArray(n)
        timeline.forEachIndexed { newIndex, oldIndex -> newIndexOf[oldIndex] = newIndex }
        return order.map { if (it in 0 until n) newIndexOf[it] else it }
    }
}
