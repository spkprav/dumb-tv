package org.dumbtv.launcher.dns

/**
 * Just enough IPv4/UDP and DNS to read a query on the tunnel and write an answer back.
 * UDP checksum is left at zero, which IPv4 allows. The IP header checksum is required.
 */
object DnsPackets {
    data class Udp4(
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val payload: ByteArray,
    )

    fun parseUdp4(packet: ByteArray, length: Int): Udp4? {
        if (length < 28 || packet.size < length) return null
        val version = (packet[0].toInt() ushr 4) and 0xF
        if (version != 4) return null
        val ihl = (packet[0].toInt() and 0xF) * 4
        if (ihl < 20 || length < ihl + 8) return null
        val flagsAndFragment = ((packet[6].toInt() and 0xFF) shl 8) or (packet[7].toInt() and 0xFF)
        if (flagsAndFragment and 0x1FFF != 0) return null
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return null
        val totalLength = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
        if (totalLength < ihl + 8 || totalLength > length) return null
        val udpStart = ihl
        val srcPort = u16(packet, udpStart)
        val dstPort = u16(packet, udpStart + 2)
        val udpLength = u16(packet, udpStart + 4)
        if (udpLength < 8 || udpStart + udpLength > totalLength) return null
        val payload = packet.copyOfRange(udpStart + 8, udpStart + udpLength)
        return Udp4(
            srcIp = packet.copyOfRange(12, 16),
            dstIp = packet.copyOfRange(16, 20),
            srcPort = srcPort,
            dstPort = dstPort,
            payload = payload,
        )
    }

    fun buildUdp4(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray,
    ): ByteArray {
        val udpLength = 8 + payload.size
        val total = 20 + udpLength
        val packet = ByteArray(total)
        packet[0] = 0x45
        packet[2] = (total ushr 8).toByte()
        packet[3] = (total and 0xFF).toByte()
        packet[6] = 0x40
        packet[8] = 64
        packet[9] = 17
        srcIp.copyInto(packet, 12, endIndex = 4)
        dstIp.copyInto(packet, 16, endIndex = 4)
        val checksum = ipv4Checksum(packet, 20)
        packet[10] = (checksum ushr 8).toByte()
        packet[11] = (checksum and 0xFF).toByte()
        put16(packet, 20, srcPort)
        put16(packet, 22, dstPort)
        put16(packet, 24, udpLength)
        payload.copyInto(packet, 28)
        return packet
    }

    fun questionName(dns: ByteArray): String? {
        if (dns.size < 12) return null
        val questions = u16(dns, 4)
        if (questions < 1) return null
        return readName(dns, 12)?.first
    }

    /** Copy of the query with QR, RA, and NXDOMAIN set, and no answers. */
    fun nxdomain(query: ByteArray): ByteArray {
        val response = query.copyOf()
        if (response.size < 12) return response
        val rd = response[2].toInt() and 0x01
        response[2] = (0x80 or rd).toByte()
        response[3] = 0x83.toByte()
        response[6] = 0
        response[7] = 0
        response[8] = 0
        response[9] = 0
        response[10] = 0
        response[11] = 0
        return response
    }

    fun ipv4Checksum(header: ByteArray, length: Int = 20): Int {
        var sum = 0
        var index = 0
        while (index < length) {
            if (index == 10) {
                index += 2
                continue
            }
            val hi = header[index].toInt() and 0xFF
            val lo = if (index + 1 < length) header[index + 1].toInt() and 0xFF else 0
            sum += (hi shl 8) or lo
            index += 2
        }
        while (sum ushr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv() and 0xFFFF
    }

    private fun readName(packet: ByteArray, start: Int): Pair<String, Int>? {
        val labels = ArrayList<String>(6)
        var position = start
        var end = start
        var jumped = false
        var hops = 0
        while (position < packet.size && hops++ < 16) {
            val length = packet[position].toInt() and 0xFF
            if (length == 0) {
                if (!jumped) end = position + 1
                return labels.joinToString(".") to end
            }
            if (length and 0xC0 == 0xC0) {
                if (position + 1 >= packet.size) return null
                if (!jumped) end = position + 2
                jumped = true
                position = ((length and 0x3F) shl 8) or (packet[position + 1].toInt() and 0xFF)
                continue
            }
            if (length and 0xC0 != 0) return null
            position += 1
            if (position + length > packet.size) return null
            labels += packet.copyOfRange(position, position + length).toString(Charsets.UTF_8)
            position += length
            if (!jumped) end = position
        }
        return null
    }

    private fun u16(packet: ByteArray, offset: Int): Int {
        return ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)
    }

    private fun put16(packet: ByteArray, offset: Int, value: Int) {
        packet[offset] = (value ushr 8).toByte()
        packet[offset + 1] = (value and 0xFF).toByte()
    }
}
