package org.dumbtv.launcher.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerTargetsTest {
    @Test fun selectsOnlyExplicitTargetsAndHandlesNoMatches() {
        assertTrue(TrackerTargets.selectInstalled(setOf("org.jellyfin.androidtv", "com.google.android.gms")).isEmpty())
        val selected = TrackerTargets.selectInstalled(setOf("tv.samba.ssm", "org.jellyfin.androidtv", "com.amazon.amazonvideo.livingroom"))
        assertEquals(listOf("tv.samba.ssm"), selected.map { it.packageName })
    }
    @Test fun excludesSystemIdentitiesAndSharedIdentitiesWithOtherApps() {
        assertFalse(TrackerTargets.safeToBlock(1000, listOf("tv.samba.ssm")))
        assertFalse(TrackerTargets.safeToBlock(10123, listOf("tv.samba.ssm", "org.jellyfin.androidtv")))
        assertFalse(TrackerTargets.safeToBlock(10123, emptyList()))
        assertTrue(TrackerTargets.safeToBlock(10123, listOf("tv.samba.ssm")))
    }
}
