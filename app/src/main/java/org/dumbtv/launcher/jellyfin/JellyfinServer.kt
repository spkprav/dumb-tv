package org.dumbtv.launcher.jellyfin

/** A Jellyfin origin entered on the TV: IP or hostname, port, and http/https. */
data class JellyfinServer(val secure: Boolean, val host: String, val port: Int) {
    val origin: String
        get() = buildString {
            append(if (secure) "https" else "http")
            append("://")
            if (':' in host) append('[').append(host).append(']') else append(host)
            append(':').append(port)
        }

    sealed interface Attempt {
        data class Valid(val server: JellyfinServer) : Attempt
        data class Rejected(val message: String) : Attempt
    }

    companion object {
        fun parse(address: String, portText: String, secure: Boolean): Attempt {
            val raw = address.trim()
            if (raw.isEmpty()) return rejected("Enter the server IP address or hostname.")
            if (raw.any { it.isWhitespace() || it < ' ' }) return rejected("The address cannot contain spaces.")
            if ("://" in raw) return parseUrl(raw, portText)
            if ('/' in raw || '?' in raw || '#' in raw || '@' in raw) {
                return rejected("Use an IP address or hostname, not a path or login.")
            }

            val hostPart: String
            val embeddedPort: String?
            if (raw.startsWith("[")) {
                val end = raw.indexOf(']')
                if (end <= 1) return rejected("That IPv6 address is invalid.")
                hostPart = raw.substring(1, end)
                val rest = raw.substring(end + 1)
                embeddedPort = when {
                    rest.isEmpty() -> null
                    rest.length > 1 && rest[0] == ':' && rest.drop(1).all(Char::isDigit) -> rest.drop(1)
                    else -> return rejected("That address is invalid.")
                }
            } else if (raw.count { it == ':' } == 1) {
                val split = raw.split(':', limit = 2)
                hostPart = split[0]
                embeddedPort = split[1].takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
                if (embeddedPort == null && split[1].isNotEmpty()) return rejected("That port is invalid.")
            } else {
                hostPart = raw
                embeddedPort = null
            }

            val host = when (val checked = checkHost(hostPart)) {
                is Attempt.Rejected -> return checked
                is Attempt.Valid -> checked.server.host
            }
            val port = resolvePort(embeddedPort ?: portText, secure) ?: return rejected("Enter a port from 1 to 65535.")
            return Attempt.Valid(JellyfinServer(secure, host, port))
        }

        private fun parseUrl(raw: String, portText: String): Attempt {
            val uri = try {
                java.net.URI(raw)
            } catch (_: Exception) {
                return rejected("That address is invalid.")
            }
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return rejected("Use an http or https address.")
            if (!uri.userInfo.isNullOrEmpty()) return rejected("Remove the username from the address. It has its own field.")
            if (!uri.rawQuery.isNullOrEmpty() || uri.rawFragment != null) {
                return rejected("Use only the server address, without a query.")
            }
            val path = uri.path.orEmpty()
            if (path.isNotEmpty() && path != "/") return rejected("Use only the server address, without a path.")
            val hostText = uri.host?.removePrefix("[")?.removeSuffix("]")
            if (hostText.isNullOrBlank()) return rejected("That address is missing a host.")
            val secure = scheme == "https"
            val host = when (val checked = checkHost(hostText)) {
                is Attempt.Rejected -> return checked
                is Attempt.Valid -> checked.server.host
            }
            val port = if (uri.port != -1) {
                uri.port.takeIf { it in 1..65535 } ?: return rejected("Enter a port from 1 to 65535.")
            } else {
                resolvePort(portText, secure) ?: return rejected("Enter a port from 1 to 65535.")
            }
            return Attempt.Valid(JellyfinServer(secure, host, port))
        }

        /** Valid returns a server whose host is normalized. Secure and port are placeholders. */
        private fun checkHost(input: String): Attempt {
            val host = input.trim().trimEnd('.')
            if (host.isEmpty()) return rejected("Enter the server IP address or hostname.")
            if (host.length > 253) return rejected("That address is too long.")
            if (host.all { it.isDigit() || it == '.' }) {
                val ipv4 = normalizeIpv4(host) ?: return rejected("That IP address is invalid.")
                return Attempt.Valid(JellyfinServer(false, ipv4, 1))
            }
            if (':' in host) {
                if (!validIpv6(host)) return rejected("That IPv6 address is invalid.")
                return Attempt.Valid(JellyfinServer(false, host.lowercase(), 1))
            }
            if (!validHostname(host)) return rejected("That hostname is invalid.")
            return Attempt.Valid(JellyfinServer(false, host.lowercase(), 1))
        }

        private fun resolvePort(text: String, secure: Boolean): Int? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return if (secure) 8920 else 8096
            if (trimmed.length > 5 || trimmed.any { !it.isDigit() }) return null
            return trimmed.toIntOrNull()?.takeIf { it in 1..65535 }
        }

        private fun normalizeIpv4(host: String): String? {
            val parts = host.split('.')
            if (parts.size != 4) return null
            val numbers = parts.map { part ->
                if (part.isEmpty() || part.length > 3 || part.any { !it.isDigit() }) return null
                part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            }
            return numbers.joinToString(".")
        }

        private fun validHostname(host: String): Boolean {
            if (host.length > 253) return false
            return host.split('.').all { it.matches(label) }
        }

        private fun validIpv6(host: String): Boolean {
            if (host.isEmpty() || '%' in host) return false
            val halves = host.lowercase().split("::")
            if (halves.size > 2) return false
            fun groups(section: String): List<String>? {
                if (section.isEmpty()) return emptyList()
                val pieces = section.split(':')
                if (pieces.any { it.isEmpty() }) return null
                return pieces
            }
            val left = groups(halves[0]) ?: return false
            val right = if (halves.size == 2) groups(halves[1]) ?: return false else emptyList()
            val ipv4Tail = right.lastOrNull()?.contains('.') == true
            if (ipv4Tail && normalizeIpv4(right.last()) == null) return false
            val head = if (ipv4Tail) right.dropLast(1) else right
            if (left.any { !validHex(it) } || head.any { !validHex(it) }) return false
            val explicit = left.size + head.size + if (ipv4Tail) 2 else 0
            return if (halves.size == 1) explicit == 8 else explicit <= 7
        }

        private fun validHex(group: String) =
            group.length in 1..4 && group.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

        private fun rejected(message: String): Attempt = Attempt.Rejected(message)

        private val label = Regex("[A-Za-z0-9_](?:[A-Za-z0-9_-]{0,61}[A-Za-z0-9_])?")
    }
}
