package org.dumbtv.launcher.usage

import org.junit.Assert.assertEquals
import org.junit.Test

class TvUsageTest {
    @Test fun countsOnlyTimeTodayWithScreenOn() {
        val timeline = TvUsage.Timeline(midnight = 100)
        timeline.resume("player", "Movie", 50)
        timeline.screenOff(120)
        timeline.screenOn(200)
        timeline.resume("home", "Main", 230)
        timeline.pause("player", "Movie", 235)

        val result = timeline.checkpoint(250)
        assertEquals(mapOf("player" to 50L, "home" to 20L), result.appMillis)
        assertEquals(listOf(200L), result.screenOns)
    }

    @Test fun keepsThreeLatestWakesAndClosesForegroundAtShutdown() {
        val timeline = TvUsage.Timeline(midnight = 0)
        for (time in listOf(10L, 20L, 30L, 40L)) {
            timeline.screenOn(time)
            timeline.screenOff(time + 1)
        }
        timeline.screenOn(50)
        timeline.resume("player", "Movie", 55)
        timeline.reset(60)
        timeline.reset(200)

        val result = timeline.checkpoint(220)
        assertEquals(mapOf("player" to 5L), result.appMillis)
        assertEquals(listOf(50L, 40L, 30L), result.screenOns)
    }

    @Test fun checkpointsContinueAcrossRefreshesAndRetainTotalsAfterRestart() {
        val first = TvUsage.Timeline(midnight = 0)
        first.screenOn(5)
        first.resume("player", "Movie", 10)
        val saved = first.checkpoint(100)

        val second = TvUsage.Timeline(midnight = 0, start = 100, initial = saved)
        second.pause("player", "Movie", 110)
        second.resume("sports", "Stream", 120)
        val beforeRestart = second.checkpoint(150)

        val restarted = TvUsage.Timeline(midnight = 0, start = 250, initial = beforeRestart.afterRestart())
        restarted.screenOn(260)
        restarted.resume("sports", "Stream", 280)
        val result = restarted.checkpoint(300)

        assertEquals(mapOf("player" to 100L, "sports" to 50L), result.appMillis)
        assertEquals(listOf(260L, 5L), result.screenOns)
    }
}
