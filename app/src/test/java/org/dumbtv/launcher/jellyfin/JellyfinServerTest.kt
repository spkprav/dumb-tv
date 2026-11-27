package org.dumbtv.launcher.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinServerTest {
    @Test fun acceptsLanIpAndDefaultPort() {
        val server = ok("192.168.1.20", "8096", false)
        assertEquals("http://192.168.1.20:8096", server.origin)
    }

    @Test fun normalizesLeadingZerosInIpv4() {
        assertEquals("http://192.168.50.249:8096", ok("192.168.050.249", "", false).origin)
    }

    @Test fun embeddedPortOverridesThePortField() {
        assertEquals("http://192.168.1.20:8096", ok("192.168.1.20:8096", "1111", false).origin)
    }

    @Test fun httpsToggleUsesThePortField() {
        assertEquals("https://jellyfin.local:8920", ok("jellyfin.local", "", true).origin)
        assertEquals("https://jellyfin.local:9000", ok("Jellyfin.Local", "9000", true).origin)
    }

    @Test fun fullUrlSuppliesSchemeAndExplicitPort() {
        val server = ok("https://jellyfin.home:443", "8096", false)
        assertEquals("https://jellyfin.home:443", server.origin)
        assertTrue(server.secure)
    }

    @Test fun urlWithoutPortUsesThePortField() {
        assertEquals("http://192.168.1.20:8096", ok("http://192.168.1.20", "8096", true).origin)
        assertEquals("http://192.168.1.20:8096", ok("http://192.168.1.20/", "", false).origin)
    }

    @Test fun acceptsBracketedIpv6() {
        assertEquals("http://[fe80::1]:8096", ok("[fe80::1]", "8096", false).origin)
        assertEquals("http://[::1]:8096", ok("http://[::1]:8096", "", false).origin)
    }

    @Test fun acceptsSingleLabelAndUnderscoreHostnames() {
        assertEquals("http://jellyfin:8096", ok("jellyfin", "8096", false).origin)
        assertEquals("http://media_server.local:8096", ok("media_server.local", "8096", false).origin)
    }

    @Test fun rejectsBlankPathUserinfoAndBadPorts() {
        assertNull(attempt("   ", "8096", false).serverOrNull())
        assertEquals("Enter the server IP address or hostname.", message("   ", "8096", false))
        assertNull(attempt("http://user:secret@192.168.1.20:8096", "", false).serverOrNull())
        assertNull(attempt("http://192.168.1.20:8096/web", "", false).serverOrNull())
        assertNull(attempt("http://192.168.1.20:8096?token=1", "", false).serverOrNull())
        assertNull(attempt("ftp://192.168.1.20", "", false).serverOrNull())
        assertNull(attempt("192.168.1", "8096", false).serverOrNull())
        assertNull(attempt("192.168.1.20", "0", false).serverOrNull())
        assertNull(attempt("192.168.1.20", "65536", false).serverOrNull())
        assertNull(attempt("192.168.1.20", "abc", false).serverOrNull())
        assertNull(attempt("-bad.local", "8096", false).serverOrNull())
        assertNull(attempt("fe80::1::2", "8096", false).serverOrNull())
    }

    private fun ok(address: String, port: String, secure: Boolean) =
        (JellyfinServer.parse(address, port, secure) as JellyfinServer.Attempt.Valid).server

    private fun attempt(address: String, port: String, secure: Boolean) = JellyfinServer.parse(address, port, secure)

    private fun message(address: String, port: String, secure: Boolean) =
        (attempt(address, port, secure) as JellyfinServer.Attempt.Rejected).message

    private fun JellyfinServer.Attempt.serverOrNull() = (this as? JellyfinServer.Attempt.Valid)?.server
}
