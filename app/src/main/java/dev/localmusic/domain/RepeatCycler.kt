package dev.localmusic.domain

enum class RepeatMode { OFF, ALL, ONE }

/**
 * Repeat button cycle OFF -> ALL -> ONE -> OFF (spec §5/§6), plus mapping to Media3's int constants
 * (Player.REPEAT_MODE_OFF = 0, REPEAT_MODE_ONE = 1, REPEAT_MODE_ALL = 2) without importing Media3.
 */
object RepeatCycler {
    const val PLAYER_REPEAT_MODE_OFF = 0
    const val PLAYER_REPEAT_MODE_ONE = 1
    const val PLAYER_REPEAT_MODE_ALL = 2

    fun next(mode: RepeatMode): RepeatMode = when (mode) {
        RepeatMode.OFF -> RepeatMode.ALL
        RepeatMode.ALL -> RepeatMode.ONE
        RepeatMode.ONE -> RepeatMode.OFF
    }

    /** Media3 int -> enum; unknown values -> OFF. */
    fun fromPlayerMode(playerMode: Int): RepeatMode = when (playerMode) {
        PLAYER_REPEAT_MODE_ONE -> RepeatMode.ONE
        PLAYER_REPEAT_MODE_ALL -> RepeatMode.ALL
        else -> RepeatMode.OFF
    }

    fun toPlayerMode(mode: RepeatMode): Int = when (mode) {
        RepeatMode.OFF -> PLAYER_REPEAT_MODE_OFF
        RepeatMode.ONE -> PLAYER_REPEAT_MODE_ONE
        RepeatMode.ALL -> PLAYER_REPEAT_MODE_ALL
    }

    /** Next mode in Media3 int form (unknown input is treated as OFF, so it returns ALL = 2). */
    fun nextPlayerMode(playerMode: Int): Int = toPlayerMode(next(fromPlayerMode(playerMode)))

    /** contentDescription for the Repeat button (spec §11). */
    fun label(mode: RepeatMode): String = when (mode) {
        RepeatMode.OFF -> "Repeat off"
        RepeatMode.ALL -> "Repeat all"
        RepeatMode.ONE -> "Repeat one"
    }
}
