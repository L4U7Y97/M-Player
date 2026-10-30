package dev.localmusic.domain

import kotlin.random.Random

/** A queue snapshot: items in timeline (insertion) order plus the current index (-1 = none / empty). */
data class QueueState<T>(val items: List<T>, val currentIndex: Int)

/**
 * Pure mirror of ExoPlayer's queue-edit semantics (spec §6), used for optimistic UI updates.
 * Indices are timeline indices (not shuffle order). Invalid indices -> the input is returned unchanged.
 */
object QueueModel {

    /**
     * Like `Player.moveMediaItem(from, to)`: the item at [from] ends up at index [to].
     * Current index: the moved item was current -> [to]; an item moved from before current to at/after it ->
     * current - 1; from after current to at/before it -> current + 1; otherwise unchanged.
     * No-op when [from] or [to] is outside `items.indices`, or from == to.
     */
    fun <T> move(items: List<T>, currentIndex: Int, from: Int, to: Int): QueueState<T> {
        if (from !in items.indices || to !in items.indices || from == to) return QueueState(items, currentIndex)
        val result = items.toMutableList()
        val moved = result.removeAt(from)
        result.add(to, moved)
        val newCurrent = when {
            currentIndex == from -> to
            from < currentIndex && to >= currentIndex -> currentIndex - 1
            from > currentIndex && to <= currentIndex -> currentIndex + 1
            else -> currentIndex
        }
        return QueueState(result, newCurrent)
    }

    /**
     * Like `Player.removeMediaItem(index)` with repeat off.
     * index < current -> current - 1; index > current -> unchanged; index == current -> the following item becomes
     * current (same index) or, if the removed item was the last one, current wraps to 0 (ExoPlayer moves to the
     * first item and ENDs). Resulting empty queue -> currentIndex -1. No-op when [index] is out of range.
     */
    fun <T> remove(items: List<T>, currentIndex: Int, index: Int): QueueState<T> {
        if (index !in items.indices) return QueueState(items, currentIndex)
        val result = items.toMutableList().apply { removeAt(index) }
        val newCurrent = when {
            result.isEmpty() -> -1
            index < currentIndex -> currentIndex - 1
            index > currentIndex -> currentIndex
            index < result.size -> index
            else -> 0
        }
        return QueueState(result, newCurrent)
    }

    /** Like `Player.addMediaItems(list)`: appends at the end; an empty queue (or current < 0) gets current 0. */
    fun <T> append(items: List<T>, currentIndex: Int, newItems: List<T>): QueueState<T> {
        if (newItems.isEmpty()) return QueueState(items, currentIndex)
        val result = items + newItems
        val newCurrent = if (items.isEmpty() || currentIndex < 0) 0 else currentIndex
        return QueueState(result, newCurrent)
    }

    /**
     * Full play order of a queue of [count] items (repeat off): `0 until count` when [shuffledOrder] is null,
     * otherwise [shuffledOrder] itself. A shuffledOrder that is not a permutation of `0 until count` is ignored
     * (linear order returned). count <= 0 -> empty list.
     */
    fun playOrder(count: Int, shuffledOrder: List<Int>? = null): List<Int> {
        if (count <= 0) return emptyList()
        val linear = (0 until count).toList()
        if (shuffledOrder == null) return linear
        val valid = shuffledOrder.size == count && shuffledOrder.toSet() == linear.toSet()
        return if (valid) shuffledOrder.toList() else linear
    }

    /**
     * Indices that play after [current] in [playOrder] (repeat off), excluding current.
     * [current] not in the order -> the whole play order.
     */
    fun upNext(count: Int, current: Int, shuffledOrder: List<Int>? = null): List<Int> {
        val order = playOrder(count, shuffledOrder)
        val pos = order.indexOf(current)
        return if (pos < 0) order else order.subList(pos + 1, order.size).toList()
    }

    /**
     * A random play order for a shuffled queue of [count] items that starts with [first] and contains every index
     * exactly once, so with repeat off every item plays once before playback ends ("Shuffle all" fix).
     * [first] outside `0 until count` -> a plain random permutation. count <= 0 -> empty list.
     */
    fun shuffledOrderStartingWith(count: Int, first: Int, random: Random = Random.Default): List<Int> {
        if (count <= 0) return emptyList()
        val rest = (0 until count).filter { it != first }.shuffled(random)
        return if (first in 0 until count) listOf(first) + rest else rest
    }
}
