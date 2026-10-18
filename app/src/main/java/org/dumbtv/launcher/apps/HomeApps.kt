package org.dumbtv.launcher.apps

import java.util.Locale

/** Stable default order, independent of launch counts and previous pins. */
object HomeApps {
    private val packages = listOf(
        setOf("in.startv.hotstar", "in.startv.hotstar.dplus.tv"),
        setOf("com.graymatrix.did", "com.zee5.tv"),
        setOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"),
        setOf("com.apple.atve.androidtv.appletv"),
        setOf("com.google.android.youtube.tv", "com.google.android.youtube"),
        setOf("com.suntv.sunnxt"),
        setOf("tv.accedo.airtel.wynk", "com.airtel.xstream"),
        setOf("com.netflix.ninja", "com.netflix.mediaclient"),
    )
    private val labels = listOf(
        setOf("jiohotstar", "hotstar", "disneyhotstar", "disneyplushotstar"),
        setOf("zee5"),
        setOf("primevideo", "amazonprimevideo"),
        setOf("appletv"),
        setOf("youtube"),
        setOf("sunnxt"),
        setOf("xtremeplay", "xstreamplay", "airtelxstream", "airtelxstreamplay"),
        setOf("netflix"),
    )

    fun order(packageName: String, label: String): Int {
        val normalized = label.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
        return packages.indices.firstOrNull { packageName in packages[it] || normalized in labels[it] }
            ?: Int.MAX_VALUE
    }

    fun isDefault(packageName: String, label: String): Boolean = order(packageName, label) != Int.MAX_VALUE
}
