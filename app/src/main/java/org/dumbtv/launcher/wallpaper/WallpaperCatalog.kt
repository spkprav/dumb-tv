package org.dumbtv.launcher.wallpaper

import java.net.URI

/** Fixed photo subjects; no search or random-image endpoints at runtime. */
object WallpaperCatalog {
    data class Entry(val id: String, val title: String) {
        val url: String get() = "https://images.pexels.com/photos/$id/pexels-photo-$id.jpeg?auto=compress&cs=tinysrgb&fit=crop&w=1920&h=1080"
    }

    val entries = listOf(
        Entry("19124585", "Kitten Standing next to a Tree"),
        Entry("15570637", "Portrait of Cat on Meadow"),
        Entry("35172955", "Adorable Kitten Resting in Sunlit Garden Path"),
        Entry("11355105", "Close-up Photo of Kitten on Green Grass"),
        Entry("15665454", "Two Cats in a Park"),
        Entry("24503806", "Two Cute Kittens Lying Together in a Garden"),
        Entry("17012829", "Cute Kittens in Yard"),
        Entry("30091914", "Adorable kittens cuddling outdoors in countryside"),
        Entry("7687627", "Close up of Cat and Kitten"),
        Entry("3895277", "Black And White Border Collie Puppy Lying On Green Grass"),
        Entry("35626991", "Brown Dog Lying Outdoors in Summer Landscape"),
        Entry("6342314", "Portrait of Cute Puppy Dogs"),
        Entry("37037474", "Playful Bernese Mountain Dog Puppy Outdoors"),
        Entry("14314951", "Close Up Shot of a Brown Dog"),
        Entry("35764077", "Adorable Border Collie Puppy in Outdoor Setting"),
        Entry("19249384", "A Puppy Lying on the Beach"),
        Entry("33138509", "Cute Puppy Resting on Earthy Terrain in Vietnam"),
        Entry("36726823", "Close-up of a Red Panda in Natural Habitat"),
        Entry("8018524", "Red Panda in Close Up Shot"),
        Entry("148359", "Red Panda"),
        Entry("7264991", "Close Up Shot of a Red Panda"),
        Entry("36274969", "Close-Up of a Red Panda in Natural Habitat"),
        Entry("31486428", "Serene Cherry Blossom Landscape by a Reflective Lake"),
        Entry("32164878", "Cherry Blossoms Beside Tranquil Lake in Seoul Park"),
        Entry("37026844", "Cherry Blossoms Overlooking Cityscape and Lake"),
        Entry("17984388", "Trees on the Shore Reflecting in the Water"),
        Entry("7385285", "A Scenic View of a Water Reservoir Surrounded by Cherry Blossoms"),
        Entry("20902994", "Blooming Cherry Blossom by the Lake"),
        Entry("31108433", "Serene Cherry Blossom by a Peaceful Lake"),
        Entry("37050902", "Serene Cherry Blossoms over a Tranquil Lake"),
        Entry("8170034", "Cherry Blossoms and a Lake in a Garden Park"),
        Entry("31108432", "Serene Cherry Blossom by Tranquil Lake"),
        Entry("17325013", "Lavender Flowers on a Meadow in a Mountain Valley"),
        Entry("13910878", "Lavender Field in Mountains Landscape"),
        Entry("4242729", "Landscape With Lavender Field, Forest and Mountain Range"),
        Entry("27636889", "Lavender field with mountains in the background"),
        Entry("4242690", "Lavender field near green hills in countryside"),
        Entry("17591210", "A Lavender Field and Mountains in the Background at Sunset"),
        Entry("36031237", "Vibrant Lavender Field with Mountain Backdrop"),
        Entry("36125041", "Lavender Field in Sunlit Mountain Landscape"),
        Entry("31314852", "Lavender Fields and Mountain Scenery in Thailand"),
        Entry("8545890", "Green Field With Trees and a Mountain View"),
        Entry("30420292", "Serene Sunset Over Tranquil Forest Lake"),
        Entry("35642548", "Tranquil Forest Sunset by the Lake"),
        Entry("28256439", "Calm Lake in Forest"),
        Entry("34805125", "Serene Forest Sunset by the Lake"),
        Entry("6389404", "View of Forest on Lake Shore at Sunset"),
        Entry("19205915", "Silhouette of a Forest by the Lake"),
        Entry("3359219", "Lake Near Forest"),
        Entry("15283372", "Photo of the Lake at Sunset"),
    )
    val intervals = listOf(5, 15, 1, 0)

    fun allowed(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "images.pexels.com" &&
            uri.port == -1 && uri.userInfo == null && uri.fragment == null &&
            entries.any { it.url == url }
    }.getOrDefault(false)
}

/** Each photo gets a turn before the collection reshuffles. */
class WallpaperRotation {
    private val remaining = java.util.ArrayDeque<WallpaperCatalog.Entry>()
    fun next(previousId: String?): WallpaperCatalog.Entry {
        if (remaining.isEmpty()) {
            val shuffled = WallpaperCatalog.entries.shuffled().toMutableList()
            if (shuffled.size > 1 && shuffled.first().id == previousId) {
                java.util.Collections.swap(shuffled, 0, shuffled.lastIndex)
            }
            remaining.addAll(shuffled)
        }
        return remaining.removeFirst()
    }
}
