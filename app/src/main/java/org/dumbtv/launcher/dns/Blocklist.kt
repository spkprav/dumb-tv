package org.dumbtv.launcher.dns

import java.io.BufferedReader

/**
 * Suffix blocklist. An entry for `doubleclick.net` matches that host and any
 * subdomain, and does not match a different site that merely contains the text.
 */
class Blocklist(domains: Collection<String>) {
    private val exact: Set<String> = domains.mapNotNull(::normalize).toHashSet()

    val size: Int get() = exact.size

    fun blocked(host: String): Boolean {
        var name = normalize(host) ?: return false
        while (name.isNotEmpty()) {
            if (name in exact) return true
            val dot = name.indexOf('.')
            if (dot < 0) return false
            name = name.substring(dot + 1)
        }
        return false
    }

    companion object {
        fun normalize(raw: String): String? {
            var line = raw.trim().lowercase()
            if (line.isEmpty() || line.startsWith("#")) return null
            val comment = line.indexOf('#')
            if (comment >= 0) line = line.substring(0, comment).trim()
            line = line.removePrefix("*.")
            if (line.endsWith(".")) line = line.dropLast(1)
            if (!line.contains('.') || line.any { it.isWhitespace() || it == '/' }) return null
            return line
        }

        fun load(reader: BufferedReader): Blocklist = Blocklist(reader.readLines())
    }
}
