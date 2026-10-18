package org.dumbtv.launcher.ads

import org.junit.Assert.*
import org.junit.Test

class FrameTagsTest {
    @Test fun normalizesOnlyBadgeCountdownAndSupportsMinutes() {
        for ((text, expected) in listOf("O:22" to 22, "O:18" to 18, "2:35" to 155,
            "3 : O5" to 185, "12:34" to 754, "1OO:OO" to 6000)) {
            val tag = FrameTags.classify("", "Ad $text", 0.0)
            assertEquals(text, tag.countdownRaw)
            assertEquals(expected, tag.remainingSeconds)
            assertFalse(tag.countdownNormalized!!.contains('O'))
        }
        assertNull(FrameTags.classify("", "Ad 2:6O", 0.0).remainingSeconds)
        assertNull(FrameTags.classify("", "Ad 1:02:35", 0.0).remainingSeconds)
        assertNull(FrameTags.classify("", "Ad wordO:22", 0.0).remainingSeconds)
        assertFalse(FrameTags.classify("Ad O:22", "", 0.0).adDetected)
    }
    @Test fun readsPrimeBadgeAndCountdownAcrossLines() {
        val tag = FrameTags.classify("Lenin Pandiyan\nAd 0:14\nGo ad free", "Ad\n0:14", 0.95)
        assertEquals("ad", tag.screen)
        assertTrue(tag.adDetected)
        assertEquals(14, tag.remainingSeconds)
        assertEquals(125, FrameTags.classify("", "Ad 2:05", 0.0).remainingSeconds)
    }
    @Test fun doesNotTreatAdFreeButtonOrMovieDialogueAsAnAdBadge() {
        assertFalse(FrameTags.classify("Go ad free", "", 0.0).adDetected)
        assertFalse(FrameTags.classify("An ad for something", "", 0.0).adDetected)
        assertFalse(FrameTags.classify("", "Ad free", 0.0).adDetected)
        assertFalse(FrameTags.classify("", "Adchoices", 0.0).adDetected)
    }
    @Test fun unreadableTimerRemainsUnknownInsteadOfInventingDuration() {
        val tag = FrameTags.classify("", "Ad 0:99", 0.0)
        assertTrue(tag.adDetected)
        assertNull(tag.remainingSeconds)
        assertTrue(FrameTags.classify("", "Ad0:14", 0.0).adDetected)
    }
    @Test fun tagsProfileBrowseAndProtectedFramesAsObservations() {
        assertEquals("profile_selection", FrameTags.classify("Who's watching?", "", 0.0).screen)
        assertEquals("browse", FrameTags.classify("Continue watching", "", 0.0).screen)
        assertEquals("blank_or_protected", FrameTags.classify("", "", 1.0).screen)
        assertEquals("unclassified", FrameTags.classify("Movie title", "", 0.0).screen)
    }
    @Test fun schedulesExactlyThirtySamplesEveryThreeSeconds() {
        val times = (0 until CaptureSequence.COUNT).map { CaptureSequence.scheduledMs(it) }
        assertEquals(30, times.size)
        assertEquals(0L, times.first())
        assertEquals(87000L, times.last())
        assertEquals(90000L, CaptureSequence.scheduledMs(30))
        assertEquals(180000L, CaptureSequence.scheduledMs(60))
        assertEquals(3600000L, CaptureSequence.scheduledMs(1200))
        assertTrue(times.zipWithNext().all { (a,b) -> b-a == 3000L })
    }
}
