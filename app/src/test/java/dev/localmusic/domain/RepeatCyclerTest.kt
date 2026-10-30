package dev.localmusic.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatCyclerTest {

    @Test
    fun next_cyclesOffAllOneOff() {
        assertEquals(RepeatMode.ALL, RepeatCycler.next(RepeatMode.OFF))
        assertEquals(RepeatMode.ONE, RepeatCycler.next(RepeatMode.ALL))
        assertEquals(RepeatMode.OFF, RepeatCycler.next(RepeatMode.ONE))
    }

    @Test
    fun next_threeStepsReturnToStart() {
        for (m in RepeatMode.values()) {
            assertEquals(m, RepeatCycler.next(RepeatCycler.next(RepeatCycler.next(m))))
        }
    }

    @Test
    fun playerConstants_matchMedia3() {
        // Player.REPEAT_MODE_OFF = 0, REPEAT_MODE_ONE = 1, REPEAT_MODE_ALL = 2
        assertEquals(0, RepeatCycler.PLAYER_REPEAT_MODE_OFF)
        assertEquals(1, RepeatCycler.PLAYER_REPEAT_MODE_ONE)
        assertEquals(2, RepeatCycler.PLAYER_REPEAT_MODE_ALL)
    }

    @Test
    fun playerMode_roundTrip() {
        for (m in RepeatMode.values()) {
            assertEquals(m, RepeatCycler.fromPlayerMode(RepeatCycler.toPlayerMode(m)))
        }
    }

    @Test
    fun fromPlayerMode_unknownIsOff() {
        assertEquals(RepeatMode.OFF, RepeatCycler.fromPlayerMode(7))
        assertEquals(RepeatMode.OFF, RepeatCycler.fromPlayerMode(-1))
    }

    @Test
    fun nextPlayerMode_cyclesZeroTwoOneZero() {
        assertEquals(2, RepeatCycler.nextPlayerMode(0))
        assertEquals(1, RepeatCycler.nextPlayerMode(2))
        assertEquals(0, RepeatCycler.nextPlayerMode(1))
        assertEquals(2, RepeatCycler.nextPlayerMode(99))
    }

    @Test
    fun label_matchesQaContract() {
        assertEquals("Repeat off", RepeatCycler.label(RepeatMode.OFF))
        assertEquals("Repeat all", RepeatCycler.label(RepeatMode.ALL))
        assertEquals("Repeat one", RepeatCycler.label(RepeatMode.ONE))
    }
}
