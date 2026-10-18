package org.dumbtv.launcher.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class DnsAndBlocklistTest {
    @Test
    fun suffixMatchStopsAtTheDomainBoundary() {
        val list = Blocklist(
            listOf(
                "doubleclick.net",
                "# comment",
                "",
                "*.fwmrm.net",
                "not a domain",
            ),
        )
        assertEquals(2, list.size)
        assertTrue(list.blocked("doubleclick.net"))
        assertTrue(list.blocked("pubads.g.doubleclick.net"))
        assertTrue(list.blocked("ADS.FWMRM.NET"))
        assertFalse(list.blocked("notdoubleclick.net"))
        assertFalse(list.blocked("doubleclick.net.evil.com"))
        assertFalse(list.blocked("example.com"))
    }

    @Test
    fun queryRoundTripsThroughAnIpv4Packet() {
        val query = dnsQuery("ads.example.com")
        val packet = DnsPackets.buildUdp4(
            srcIp = byteArrayOf(10, 10, 10, 2),
            dstIp = byteArrayOf(10, 10, 10, 1),
            srcPort = 53,
            dstPort = 44123,
            payload = query,
        )
        assertTrue(headerChecksumsToOnesComplement(packet))
        val parsed = DnsPackets.parseUdp4(packet, packet.size)
        requireNotNull(parsed)
        assertEquals(53, parsed.srcPort)
        assertEquals(44123, parsed.dstPort)
        assertEquals("ads.example.com", DnsPackets.questionName(parsed.payload))
        val blocked = DnsPackets.nxdomain(parsed.payload)
        assertEquals(0x80, blocked[2].toInt() and 0x80)
        assertEquals(3, blocked[3].toInt() and 0x0F)
        assertEquals("ads.example.com", DnsPackets.questionName(blocked))
    }

    @Test
    fun ignoresPacketsThatAreNotUdpDns() {
        val packet = ByteArray(40)
        packet[0] = 0x45
        packet[2] = 0
        packet[3] = 40
        packet[9] = 6
        assertNull(DnsPackets.parseUdp4(packet, packet.size))
    }

    private fun dnsQuery(host: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
        host.split('.').forEach { label ->
            out.write(label.length)
            out.write(label.toByteArray())
        }
        out.write(0)
        out.write(byteArrayOf(0x00, 0x01, 0x00, 0x01))
        return out.toByteArray()
    }

    private fun headerChecksumsToOnesComplement(packet: ByteArray): Boolean {
        var sum = 0
        for (index in 0 until 20 step 2) {
            sum += ((packet[index].toInt() and 0xFF) shl 8) or (packet[index + 1].toInt() and 0xFF)
        }
        while (sum ushr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum == 0xFFFF
    }
}
