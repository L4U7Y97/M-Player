package dev.localmusic.domain

/**
 * Saved play config, restored on the next launch: the queue (track ids in timeline order), the current item and
 * position, shuffle (with its play order, as timeline indices) and repeat. Pure Kotlin.
 */
data class PlaybackSnapshot(
    val trackIds: List<String>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    /** Play order while shuffled; empty when shuffle is off. */
    val shuffleOrder: List<Int>,
    val repeatMode: RepeatMode,
)

/** A snapshot mapped onto the tracks still playable. [kept] are the snapshot's timeline indices that survived. */
data class RestoredQueue(
    val kept: List<Int>,
    val currentIndex: Int,
    val positionMs: Long,
    /** Shuffle play order over the restored queue (new indices); empty when shuffle is off. */
    val shuffleOrder: List<Int>,
)

object PlaybackRestore {

    /**
     * Drops the snapshot items for which [playable] is false and remaps the current index and shuffle order.
     * If the current item was dropped, the next surviving item in play order becomes current (else the first one in
     * play order) and the position resets to 0. An invalid saved shuffle order falls back to a fresh one starting at
     * the current item. Returns null when nothing survives.
     */
    fun restore(snapshot: PlaybackSnapshot, playable: (Int) -> Boolean): RestoredQueue? {
        val count = snapshot.trackIds.size
        val kept = (0 until count).filter(playable)
        if (kept.isEmpty()) return null
        val newIndexOf = HashMap<Int, Int>(kept.size * 2)
        kept.forEachIndexed { new, old -> newIndexOf[old] = new }

        val oldOrder = QueueModel.playOrder(count, snapshot.shuffleOrder.takeIf { snapshot.shuffle })
        val savedCurrent = snapshot.currentIndex
        val currentKept = savedCurrent in newIndexOf
        val current = if (currentKept) {
            newIndexOf.getValue(savedCurrent)
        } else {
            val pos = oldOrder.indexOf(savedCurrent)
            val after = if (pos < 0) null else oldOrder.subList(pos + 1, count).firstOrNull { it in newIndexOf }
            newIndexOf.getValue(after ?: oldOrder.first { it in newIndexOf })
        }

        val shuffleOrder = when {
            !snapshot.shuffle -> emptyList()
            QueueModel.playOrder(count, snapshot.shuffleOrder) == snapshot.shuffleOrder ->
                snapshot.shuffleOrder.mapNotNull { newIndexOf[it] }
            else -> QueueModel.shuffledOrderStartingWith(kept.size, current)
        }
        return RestoredQueue(
            kept = kept,
            currentIndex = current,
            positionMs = if (currentKept) snapshot.positionMs.coerceAtLeast(0) else 0L,
            shuffleOrder = shuffleOrder,
        )
    }
}
