package org.dumbtv.launcher.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JellyfinIdsTest {
    @Test fun normalizesCompactAndDashedIdsForTheTvClient() {
        val canonical = "00112233-4455-6677-8899-aabbccddeeff"
        assertEquals(canonical, JellyfinIds.uuid("00112233445566778899AABBCCDDEEFF"))
        assertEquals(canonical, JellyfinIds.uuid(canonical))
    }
    @Test fun rejectsPathsQueriesAndMalformedIdsFromServerData() {
        listOf("", "../Users", "abc?api_key=secret", "00112233/4455/6677/8899/aabbccddeeff",
            "----00112233445566778899aabbccddeeff", "00112233445566778899aabbccddeeff00").forEach {
            assertNull(JellyfinIds.uuid(it))
        }
    }
}
