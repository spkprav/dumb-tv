package org.dumbtv.launcher.dns

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** Authenticated DNS-over-TLS exchange, rather than just checking whether TCP opens. */
object DnsTlsProbe {
    fun check(host: String) {
        Socket().use { tcp ->
            tcp.connect(InetSocketAddress(host, 853), 5_000)
            ((SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(tcp, host, 853, true) as SSLSocket).use { tls ->
                tls.soTimeout = 5_000
                tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                tls.startHandshake()
                val query = query()
                val output = DataOutputStream(tls.outputStream)
                output.writeShort(query.size)
                output.write(query)
                output.flush()
                val input = DataInputStream(tls.inputStream)
                val size = input.readUnsignedShort()
                require(size in 12..4096) { "Invalid DNS response length" }
                val response = ByteArray(size)
                input.readFully(response)
                require(validResponse(response)) { "DNS server did not return a successful lookup" }
            }
        }
    }

    internal fun query(): ByteArray = byteArrayOf(
        0x42, 0x56, 0x01, 0, 0, 1, 0, 0, 0, 0, 0, 0,
        7, 101, 120, 97, 109, 112, 108, 101, 3, 99, 111, 109, 0, 0, 1, 0, 1,
    )

    internal fun validResponse(bytes: ByteArray): Boolean = bytes.size >= 12 &&
        bytes[0] == 0x42.toByte() && bytes[1] == 0x56.toByte() &&
        bytes[2].toInt() and 0x80 != 0 && bytes[2].toInt() and 0x78 == 0 &&
        bytes[2].toInt() and 0x02 == 0 && bytes[3].toInt() and 0x0F == 0 &&
        (bytes[6].toInt() and 0xFF != 0 || bytes[7].toInt() and 0xFF != 0)
}
