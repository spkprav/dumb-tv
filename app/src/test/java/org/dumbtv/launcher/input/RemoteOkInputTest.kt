package org.dumbtv.launcher.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteOkInputTest {
    private val input = RemoteOkInput()
    private val tile = Any()
    private val menu = Any()
    private fun down(time: Long, owner: Any = tile, repeat: Int = 0) = input.down(owner, time, 23, 1, repeat)
    private fun up(time: Long, owner: Any = tile, cancelled: Boolean = false) = input.up(owner, time, 23, 1, cancelled)

    @Test fun nativeControlReceivesOneDownAndOneRelease() {
        assertTrue(down(1000))
        assertFalse(down(1000))
        assertTrue(up(1000))
        assertFalse(up(1000))
        assertFalse(down(1000))
    }
    @Test fun distinctRapidTapsAreNotDropped() {
        for (time in 1000L..1100L step 10) {
            assertTrue(down(time))
            assertTrue(up(time))
        }
    }
    @Test fun onlyFirstLongPressRepeatReachesNativeControl() {
        assertTrue(down(1000))
        assertTrue(down(1000, repeat = 1))
        assertFalse(down(1000, repeat = 1))
        assertFalse(down(1000, repeat = 2))
        assertTrue(up(1000)) // Native Card resets its long-press state on release.
    }
    @Test fun heldPressCannotActivateNewDialog() {
        down(1000)
        assertFalse(down(1000, menu, repeat = 1))
        assertFalse(up(1000, menu))
        assertTrue(down(1001, menu))
        assertTrue(up(1001, menu))
    }
    @Test fun focusLossAndCancellationCannotClick() {
        down(1000)
        input.cancel(tile)
        assertFalse(down(1000, repeat = 1))
        assertFalse(up(1000))
        down(2000)
        assertFalse(up(2000, cancelled = true))
    }
    @Test fun orphanEventsAreRejectedAndMissingReleasesRecover() {
        assertFalse(up(1000))
        assertFalse(down(1000, repeat = 1))
        down(2000)
        input.cancel(tile)
        assertTrue(down(3000))
        assertTrue(up(3000))
    }
    @Test fun otherDevicesCannotCompleteOrRepeatAPress() {
        down(1000)
        assertFalse(input.down(tile, 1000, 66, 2, 1))
        assertFalse(input.up(tile, 1000, 66, 2, false))
        assertTrue(up(1000))
    }
}
