package org.dumbtv.launcher.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsTlsProbeTest {
    @Test fun probeAsksForAnOrdinaryDomain() {
        assertEquals("example.com", DnsPackets.questionName(DnsTlsProbe.query()))
    }

    @Test fun requiresAMatchingSuccessfulAnswer() {
        val response = DnsTlsProbe.query().apply { this[2] = 0x81.toByte(); this[3] = 0x80.toByte(); this[7] = 1 }
        assertTrue(DnsTlsProbe.validResponse(response))
        assertFalse(DnsTlsProbe.validResponse(response.copyOf().apply { this[0] = 0 }))
        assertFalse(DnsTlsProbe.validResponse(response.copyOf().apply { this[3] = 0x82.toByte() }))
        assertFalse(DnsTlsProbe.validResponse(response.copyOf().apply { this[7] = 0 }))
        assertFalse(DnsTlsProbe.validResponse(response.copyOf().apply { this[2] = 0x83.toByte() }))
        assertFalse(DnsTlsProbe.validResponse(DnsTlsProbe.query()))
        assertFalse(DnsTlsProbe.validResponse(byteArrayOf()))
    }
}
