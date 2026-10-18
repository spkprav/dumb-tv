package org.dumbtv.launcher.input

/** Header positions 0–1 are pages; 2 is wallpaper; 3 is Settings. -1 means page content. */
data class HomeNavigation(val tab: Int = 0, val target: Int = -1, val request: Int = 0) {
    fun headerFocused(index: Int): HomeNavigation {
        require(index in 0..3)
        return copy(tab = if (index < 2) index else tab, target = index)
    }
    fun contentFocused() = copy(target = -1)
    fun enterContent() = copy(target = -1, request = request + 1)
    fun header(index: Int = tab): HomeNavigation {
        require(index in 0..3)
        return copy(target = index, request = request + 1)
    }
    fun movies() = copy(tab = 1, target = -1, request = request + 1)
    fun home() = copy(tab = 0, target = -1, request = request + 1)
    fun restore(savedTarget: Int) = copy(target = savedTarget, request = request + 1)
}
