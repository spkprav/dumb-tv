package org.dumbtv.launcher.ads

/** These are OCR-based observations, not claims about the user's button presses. */
object FrameTags {
    data class Result(val screen: String, val adDetected: Boolean, val remainingSeconds: Int?, val reason: String,
        val countdownRaw: String? = null, val countdownNormalized: String? = null)
    private val badge = Regex("(?i)(?<![a-z])ad(?:vertisement)?(?![a-z]|\\s*(?:free|choices)\\b)")
    private val countdown = Regex("(?<![0-9])([0-9]{1,2}):([0-5][0-9])(?![0-9])")
    // Only badge countdown tokens accept OCR O/o as zero; never rewrite full screen text.
    private val badgeCountdown = Regex("(?<![a-zA-Z0-9:])([0-9Oo]{1,4})\\s*:\\s*([0-9Oo]{2})(?![a-zA-Z0-9:])")
    fun classify(fullText: String, badgeText: String, darkRatio: Double): Result {
        val ad = badge.find(badgeText)
        if (ad != null) {
            val timer = badgeCountdown.find(badgeText, ad.range.last + 1)
            val normalized = timer?.value?.replace('O', '0')?.replace('o', '0')?.replace(Regex("\\s+"), "")
            val parts = normalized?.split(':')
            val seconds = parts?.takeIf { it[1].toInt() in 0..59 }?.let { it[0].toInt() * 60 + it[1].toInt() }
            return Result("ad", true, seconds, if (seconds == null) "ad_label_in_badge_region" else "ad_label_and_countdown_in_badge_region",
                timer?.value, normalized.takeIf { seconds != null })
        }
        val text = fullText.lowercase()
        val (screen, reason) = when {
            text.contains("who’s watching") || text.contains("who's watching") || text.contains("select a profile") || text.contains("choose a profile") -> "profile_selection" to "profile_prompt_text"
            text.contains("sign in") || text.contains("register your device") -> "sign_in" to "sign_in_text"
            text.contains("something went wrong") || text.contains("unable to play") || text.contains("playback error") -> "error_screen" to "error_prompt_text"
            text.contains("loading") || text.contains("buffering") -> "loading" to "loading_text"
            text.contains("go ad free") || text.contains("subtitles") || text.contains("audio languages") || countdown.containsMatchIn(fullText) -> "playback_controls" to "playback_control_text"
            text.contains("continue watching") || text.contains("watch again") || text.contains("recently added") -> "browse" to "browse_shelf_text"
            fullText.isBlank() && darkRatio >= 0.98 -> "blank_or_protected" to "nearly_black_capture_without_recognized_text"
            fullText.isBlank() -> "no_text" to "no_recognized_text"
            else -> "unclassified" to "text_saved_for_review"
        }
        return Result(screen, false, null, reason)
    }
}

/** Continuous three-second samples; only the latest COUNT images are retained. */
object CaptureSequence {
    const val COUNT = 30
    const val INTERVAL_MS = 3000L
    fun scheduledMs(slot: Int): Long {
        require(slot >= 0)
        return slot * INTERVAL_MS
    }
}
