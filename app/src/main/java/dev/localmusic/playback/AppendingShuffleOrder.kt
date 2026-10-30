package dev.localmusic.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import dev.localmusic.domain.ShuffleOrderMath

/**
 * Fixed shuffle order (BUG-1). Unlike Media3's `DefaultShuffleOrder`, which puts inserted items at random positions
 * (possibly before the current item, so with repeat off they never play), this order appends inserted items to the
 * end of the play order and otherwise keeps the order unchanged. The index math lives in [ShuffleOrderMath].
 *
 * [shuffled] must be a permutation of `0 until shuffled.size`.
 */
@OptIn(UnstableApi::class)
class AppendingShuffleOrder(shuffled: List<Int>) : ShuffleOrder {
    private val order: IntArray = shuffled.toIntArray()
    private val positionOf: IntArray = IntArray(order.size).also { pos ->
        order.forEachIndexed { position, index -> pos[index] = position }
    }

    override fun getLength(): Int = order.size

    override fun getNextIndex(index: Int): Int {
        val pos = positionOf[index] + 1
        return if (pos < order.size) order[pos] else C.INDEX_UNSET
    }

    override fun getPreviousIndex(index: Int): Int {
        val pos = positionOf[index] - 1
        return if (pos >= 0) order[pos] else C.INDEX_UNSET
    }

    override fun getLastIndex(): Int = if (order.isEmpty()) C.INDEX_UNSET else order[order.size - 1]

    override fun getFirstIndex(): Int = if (order.isEmpty()) C.INDEX_UNSET else order[0]

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder =
        AppendingShuffleOrder(ShuffleOrderMath.insert(order.asList(), insertionIndex, insertionCount))

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder =
        AppendingShuffleOrder(ShuffleOrderMath.remove(order.asList(), indexFrom, indexToExclusive))

    override fun cloneAndMove(indexFrom: Int, indexToExclusive: Int, newIndexFrom: Int): ShuffleOrder =
        AppendingShuffleOrder(ShuffleOrderMath.move(order.asList(), indexFrom, indexToExclusive, newIndexFrom))

    override fun cloneAndClear(): ShuffleOrder = AppendingShuffleOrder(emptyList())
}
