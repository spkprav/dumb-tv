package org.dumbtv.launcher.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperCatalogTest {
    @Test fun containsDistinctPhotosWithApprovedLandscapeUrls() {
        assertTrue(WallpaperCatalog.entries.isNotEmpty())
        assertEquals(WallpaperCatalog.entries.size, WallpaperCatalog.entries.map { it.id }.toSet().size)
        WallpaperCatalog.entries.forEach { entry ->
            assertTrue(entry.title.isNotBlank())
            assertTrue(WallpaperCatalog.allowed(entry.url))
            assertTrue(entry.url.contains("w=1920&h=1080"))
        }
    }
    @Test fun rotationVisitsEveryPhotoBeforeRepeating() {
        val rotation = WallpaperRotation()
        val seen = mutableSetOf<String>()
        var previous: String? = null
        repeat(WallpaperCatalog.entries.size) {
            val next = rotation.next(previous)
            assertTrue(seen.add(next.id))
            previous = next.id
        }
        assertNotEquals(previous, rotation.next(previous).id)
    }
    @Test fun rejectsEndpointsOutsideTheFixedGallery() {
        val approved = WallpaperCatalog.entries.first().url
        assertTrue(WallpaperCatalog.allowed(approved))
        listOf(
            approved.replace("https://", "http://"),
            approved.replace("images.pexels.com", "images.pexels.com.evil.example"),
            approved.replace("images.pexels.com", "images.pexels.com@evil.example"),
            approved.replace("images.pexels.com", "images.pexels.com:8443"),
            approved + "&query=random",
            approved + "#fragment",
            WallpaperCatalog.Entry("19124585", "Kitten Standing next to a Tree").url,
            "https://images.pexels.com/photo-not-in-gallery",
            "https://source.unsplash.com/random/1920x1080/?nature",
        ).forEach { assertFalse(it, WallpaperCatalog.allowed(it)) }
    }
}
