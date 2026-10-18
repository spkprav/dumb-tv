package org.dumbtv.launcher.jellyfin

object JellyfinIds {
    private val valid = Regex("(?:[0-9a-fA-F]{32}|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})")
    fun uuid(value: String): String? {
        if (!valid.matches(value)) return null
        val hex = value.replace("-", "")
        return "${hex.take(8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}".lowercase()
    }
}
