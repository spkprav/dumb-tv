@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package org.dumbtv.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import org.dumbtv.launcher.HomeViewModel
import org.dumbtv.launcher.apps.HomeApps
import org.dumbtv.launcher.dns.PrivateDns
import org.dumbtv.launcher.privacy.TrackerFirewallService
import org.dumbtv.launcher.settings.SettingsDestination
import org.dumbtv.launcher.settings.SettingsDestinations

private data class SettingsEntry(val title: String, val detail: String, val value: String = "›", val action: () -> Unit)
private val SettingsInk = Color(0xFF172126)
private val SettingsMuted = Color(0xFF9CAABD)

@Composable
internal fun SettingsDialog(viewModel: HomeViewModel, initialApps: Boolean,
    defaultHome: Boolean, homeOverride: Boolean, dns: PrivateDns.Snapshot, dnsBusy: Boolean,
    onChooseHome: () -> Unit, onHomeOverride: () -> Unit, onToggleDns: () -> Unit,
    onPrivacy: () -> Unit, onCaptureTest: () -> Unit, onJellyfin: () -> Unit, onClose: () -> Unit) {
    val pages = listOf("General", "Apps", "Appearance", "Connections", "System")
    var page by remember { mutableIntStateOf(if (initialApps) 1 else 0) }
    val sidebar = remember { List(pages.size) { FocusRequester() } }
    val close = remember { FocusRequester() }
    val apps by viewModel.apps.collectAsState()
    val visibility by viewModel.appVisibility.collectAsState()
    val minutes by viewModel.wallpapers.minutes.collectAsState()
    val wallpaper by viewModel.wallpapers.title.collectAsState()
    val usage by viewModel.usage.collectAsState()
    val firewall by TrackerFirewallService.status.collectAsState()
    val jellyfin by viewModel.jellyfin.state.collectAsState()
    val jellyfinServer by viewModel.jellyfin.server.collectAsState()
    val selectedCount = apps.count { visibility[it.packageName] ?: HomeApps.isDefault(it.packageName, it.label) }
    val firstRow = remember { FocusRequester() }
    var moveToRows by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        repeat(5) {
            androidx.compose.runtime.withFrameNanos { }
            if (runCatching { sidebar[page].requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    LaunchedEffect(moveToRows, page) {
        if (!moveToRows) return@LaunchedEffect
        repeat(5) {
            androidx.compose.runtime.withFrameNanos { }
            if (runCatching { firstRow.requestFocus() }.isSuccess) {
                moveToRows = false
                return@LaunchedEffect
            }
        }
        moveToRows = false
    }
    val entries = when (page) {
        0 -> listOf(
            SettingsEntry("Default launcher", "Choose what opens when you press Home", if (defaultHome) "Selected" else "Set up", onChooseHome),
            SettingsEntry("Home button override", "For TVs that keep returning to Google TV", if (homeOverride) "On" else "Set up", onHomeOverride),
            SettingsEntry("Usage access", "Show app time and recent screen-on times on Home",
                if (usage?.hasAccess == true) "On" else "Set up",
                { viewModel.openSettings(SettingsDestination("Usage access", "", android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)) }),
            SettingsEntry("Privacy & tracking", "Tracker firewall and TV privacy controls", if (firewall.active) "On" else "Off", onPrivacy),
            SettingsEntry("Ad protection", "Continuous 3-second checks · mute and inset black cover", "Open", onCaptureTest))
        2 -> listOf(
            SettingsEntry("Change wallpaper", wallpaper, "Next", { viewModel.wallpapers.next() }),
            SettingsEntry("Wallpaper rotation", "Select to change the interval", if (minutes == 0) "Fixed" else "$minutes min", { viewModel.wallpapers.cycleInterval() }))
        3 -> listOf(
            SettingsEntry("Movie library", jellyfinServer?.origin ?: "Enter the server IP or hostname, port, and account",
                if (jellyfin.connected) "Connected" else "Set up", onJellyfin),
            SettingsEntry("DNS blocking", if (dns.enabled && !dns.verified) "Configured · waiting for Android to confirm connectivity" else "Filter known advertising and tracking domains",
                if (dnsBusy) "Checking…" else if (dns.verified) "On" else if (dns.enabled) "Pending" else "Off", onToggleDns))
        4 -> SettingsDestinations.all.map { destination -> SettingsEntry(destination.title, destination.detail, "›", { viewModel.openSettings(destination) }) }
        else -> emptyList()
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val panelWidth = if (maxWidth != Dp.Infinity) maxWidth - 48.dp else 900.dp
        val panelHeight = if (maxHeight != Dp.Infinity) maxHeight - 36.dp else 520.dp
        Column(Modifier.size(panelWidth, panelHeight)
            .clip(RoundedCornerShape(22.dp)).background(Color(0xFF141C28))
            .border(1.dp, Color(0xFF354153), RoundedCornerShape(22.dp)).padding(24.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", color = Glass.Paper, fontSize = 28.sp)
                Spacer(Modifier.weight(1f))
                SettingsControl(onClick = onClose, modifier = Modifier.width(96.dp).height(40.dp)
                    .focusRequester(close).focusProperties {
                        left = sidebar[page]; down = firstRow; up = FocusRequester.Cancel; right = FocusRequester.Cancel
                    }) { ink -> Text("Done", color = ink, fontSize = 16.sp) }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(188.dp).fillMaxHeight().focusGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pages.forEachIndexed { index, title ->
                        SettingsControl(onClick = {
                            page = index
                            moveToRows = true
                        }, selected = page == index,
                            modifier = Modifier.fillMaxWidth().height(48.dp).focusRequester(sidebar[index])
                                .onFocusChanged { if (it.isFocused) page = index }.focusProperties {
                                    left = FocusRequester.Cancel; right = firstRow
                                    up = if (index == 0) close else sidebar[index - 1]
                                    down = if (index == pages.lastIndex) FocusRequester.Cancel else sidebar[index + 1]
                                }, centered = false) { ink -> Text(title, color = ink, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 14.dp)) }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Dumb TV", color = SettingsMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF2A3546)))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Text(if (page == 1) "Home apps" else pages[page], color = Glass.Paper, fontSize = 22.sp)
                    Text(when (page) {
                        0 -> "Launcher and privacy preferences"
                        1 -> "$selectedCount selected · Changes save automatically"
                        2 -> "Background and rotation"
                        3 -> "Movies and network filtering"
                        else -> "Open your TV’s system settings"
                    }, color = SettingsMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
                    key(page) {
                        if (page == 1) {
                            if (apps.isEmpty()) {
                                Box(Modifier.fillMaxWidth().weight(1f).focusRequester(firstRow).focusable()) {
                                    Text("No apps found", color = SettingsMuted, fontSize = 16.sp)
                                }
                            } else LazyVerticalGrid(columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentPadding = PaddingValues(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                itemsIndexed(apps.sortedBy { it.label.lowercase() }, key = { _, app -> app.packageName }) { index, app ->
                                    val checked = visibility[app.packageName] ?: HomeApps.isDefault(app.packageName, app.label)
                                    SettingsControl(onClick = { viewModel.setAppVisible(app, !checked) }, centered = false,
                                        modifier = Modifier.fillMaxWidth().height(72.dp)
                                            .then(if (index == 0) Modifier.focusRequester(firstRow) else Modifier)
                                            .focusProperties {
                                                if (index % 2 == 0) left = sidebar[page]
                                                if (index < 2) up = close
                                            }.semantics { role = Role.Checkbox; toggleableState = if (checked) ToggleableState.On else ToggleableState.Off }) { ink ->
                                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Image(remember(app.icon) { DrawablePainter(app.icon) }, contentDescription = null,
                                                contentScale = ContentScale.Fit, modifier = Modifier.size(32.dp))
                                            Text(app.label, color = ink, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                                            Box(Modifier.size(20.dp).border(1.dp, if (checked) ink else SettingsMuted, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                                                if (checked) Text("✓", color = ink, fontSize = 14.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        } else Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(3.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            entries.forEachIndexed { index, entry ->
                                SettingsControl(onClick = entry.action, centered = false,
                                    modifier = Modifier.fillMaxWidth().height(76.dp)
                                        .then(if (index == 0) Modifier.focusRequester(firstRow) else Modifier)
                                        .focusProperties {
                                            left = sidebar[page]
                                            if (index == 0) up = close
                                        }) { ink ->
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                            Text(entry.title, color = ink, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(entry.detail, color = ink.copy(alpha = 0.72f), fontSize = 14.sp,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                                        }
                                        Text(entry.value, color = ink, fontSize = 16.sp)
                                    }
                                }
                            }
                            if (page == 3 && dns.supported && !dns.allowed) {
                                Text("Enable DNS control once:\nadb shell pm grant org.dumbtv.launcher android.permission.WRITE_SECURE_SETTINGS",
                                    color = SettingsMuted, fontSize = 14.sp, modifier = Modifier.padding(12.dp))
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun SettingsControl(onClick: () -> Unit, modifier: Modifier, selected: Boolean = false,
    centered: Boolean = true, content: @Composable (Color) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Card(onClick = onClick, modifier = modifier.onFocusChanged { focused = it.isFocused }.remoteOk(),
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 0.98f),
        colors = CardDefaults.colors(containerColor = if (selected) Color(0xFF2D4058) else Color(0xFF1E2939),
            focusedContainerColor = Color(0xFFD2E3ED)),
        border = CardDefaults.border(border = Border.None,
            focusedBorder = Border(BorderStroke(2.dp, Glass.Paper), shape = shape))) {
        Box(Modifier.fillMaxSize(), contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart) {
            content(if (focused) SettingsInk else Glass.Paper)
        }
    }
}
