package org.dumbtv.launcher.input

import org.junit.Assert.*
import org.junit.Test

class HomeNavigationTest {
    @Test fun settingsPreservesTheSelectedPageAndRestoresToItsOwnControl() {
        val movies = HomeNavigation().headerFocused(1)
        val settings = movies.headerFocused(3)
        assertEquals(1, settings.tab)
        assertEquals(3, settings.target)
        val returned = settings.restore(settings.target)
        assertEquals(3, returned.target)
        assertEquals(1, returned.tab)
        assertTrue(returned.request > settings.request)
    }
    @Test fun wallpaperShortcutDoesNotChangeTheSelectedPage() {
        val state = HomeNavigation().headerFocused(1).headerFocused(2)
        assertEquals(1, state.tab)
        assertEquals(2, state.target)
        assertEquals(2, state.restore(2).target)
    }
    @Test fun focusingTabsSelectsPagesWithoutRequestingFocusAgain() {
        var state = HomeNavigation()
        for (tab in listOf(0, 1, 0, 1, 0, 1)) {
            val previousRequest = state.request
            state = state.headerFocused(tab)
            assertEquals(tab, state.tab)
            assertEquals(tab, state.target)
            assertEquals(previousRequest, state.request)
        }
    }
    @Test fun downEntersTheSelectedPageAndUpReturnsToItsTab() {
        val movies = HomeNavigation().headerFocused(1).enterContent()
        assertEquals(1, movies.tab)
        assertEquals(-1, movies.target)
        val header = movies.header()
        assertEquals(1, header.target)
        assertEquals(1, header.tab)
        assertTrue(header.request > movies.request)
    }
    @Test fun passiveContentFocusDoesNotStartAnAutomaticFocusLoop() {
        val content = HomeNavigation().enterContent()
        val observed = content.contentFocused()
        assertEquals(content.request, observed.request)
        assertEquals(-1, observed.target)
    }
    @Test fun dockDownOpensMoviesAndHomeReturnsToAppsContent() {
        val movies = HomeNavigation().movies()
        assertEquals(1, movies.tab)
        assertEquals(-1, movies.target)
        val home = movies.headerFocused(3).home()
        assertEquals(0, home.tab)
        assertEquals(-1, home.target)
        assertTrue(home.request > movies.request)
    }
}
