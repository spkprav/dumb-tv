@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package org.dumbtv.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import kotlinx.coroutines.launch
import org.dumbtv.launcher.HomeViewModel
import org.dumbtv.launcher.apps.HomeApps
import org.dumbtv.launcher.dns.PrivateDns
import org.dumbtv.launcher.privacy.TrackerFirewallService
import org.dumbtv.launcher.settings.SettingsDestinations

private data class SettingsEntry(val title: String, val detail: String, val value: String = "›", val action: () -> Unit)
private val SettingsInk = Color(0xFF172126)
private val SettingsMuted = Color(0xFF9CAABD)

@Composable
internal fun SettingsDialog(viewModel: HomeViewModel, initialApps: Boolean,
    defaultHome: Boolean, homeOverride: Boolean, dns: PrivateDns.Snapshot, dnsBusy: Boolean,
    onChooseHome: () -> Unit, onHomeOverride: () -> Unit, onToggleDns: () -> Unit,
    onPrivacy: () -> Unit, onCaptureTest: () -> Unit, onJellyfin: () -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val pages = listOf("General", "Apps", "Appearance", "Connections", "System")
    var page by remember { mutableIntStateOf(if (initialApps) 1 else 0) }
    val sidebar = remember { List(pages.size) { FocusRequester() } }
    val content = remember { FocusRequester() }
    val close = remember { FocusRequester() }
    val apps by viewModel.apps.collectAsState()
    val visibility by viewModel.appVisibility.collectAsState()
    val minutes by viewModel.wallpapers.minutes.collectAsState()
    val wallpaper by viewModel.wallpapers.title.collectAsState()
    val firewall by TrackerFirewallService.status.collectAsState()
    val selectedCount = apps.count { visibility[it.packageName] ?: HomeApps.isDefault(it.packageName, it.label) }
    LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos { }; sidebar[page].requestFocus() }
    val entries = when (page) {
        0 -> listOf(
            SettingsEntry("Default launcher", "Choose what opens when you press Home", if (defaultHome) "Selected" else "Set up", onChooseHome),
            SettingsEntry("Home button override", "For TVs that keep returning to Google TV", if (homeOverride) "On" else "Set up", onHomeOverride),
            SettingsEntry("Privacy & tracking", "Tracker firewall and TV privacy controls", if (firewall.active) "On" else "Off", onPrivacy),
            SettingsEntry("Ad protection", "Continuous 3-second checks · mute and inset black cover", "Open", onCaptureTest))
        2 -> listOf(
            SettingsEntry("Change wallpaper", wallpaper, "Next", { viewModel.wallpapers.next() }),
            SettingsEntry("Wallpaper rotation", "Select to change the interval", if (minutes == 0) "Fixed" else "$minutes min", { viewModel.wallpapers.cycleInterval() }))
        3 -> listOf(
            SettingsEntry("Movie library", "Manage your Jellyfin connection", "›", onJellyfin),
            SettingsEntry("DNS blocking", if (dns.enabled && !dns.verified) "Configured · waiting for Android to confirm connectivity" else "Filter known advertising and tracking domains",
                if (dnsBusy) "Checking…" else if (dns.verified) "On" else if (dns.enabled) "Pending" else "Off", onToggleDns))
        4 -> SettingsDestinations.all.map { destination -> SettingsEntry(destination.title, destination.detail, "›", { viewModel.openSettings(destination) }) }
        else -> emptyList()
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(0.86f).fillMaxHeight(0.84f).widthIn(max = 860.dp)
            .clip(RoundedCornerShape(22.dp)).background(Color(0xFF141C28))
            .border(1.dp, Color(0xFF354153), RoundedCornerShape(22.dp)).padding(22.dp)) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", color = Glass.Paper, fontSize = 25.sp)
                Spacer(Modifier.weight(1f))
                SettingsControl(onClick = onClose, modifier = Modifier.width(76.dp).height(36.dp)
                    .focusRequester(close).focusProperties {
                        left = sidebar[page]; down = content; up = FocusRequester.Cancel; right = FocusRequester.Cancel
                    }) { ink -> Text("Done", color = ink, fontSize = 14.sp) }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(146.dp).fillMaxHeight().focusGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    pages.forEachIndexed { index, title ->
                        SettingsControl(onClick = {
                            page = index
                            scope.launch { androidx.compose.runtime.withFrameNanos { }; runCatching { content.requestFocus() } }
                        }, selected = page == index,
                            modifier = Modifier.fillMaxWidth().height(43.dp).focusRequester(sidebar[index])
                                .onFocusChanged { if (it.isFocused) page = index }.focusProperties {
                                    left = FocusRequester.Cancel; right = content
                                    up = if (index == 0) close else sidebar[index - 1]
                                    down = if (index == pages.lastIndex) FocusRequester.Cancel else sidebar[index + 1]
                                }, centered = false) { ink -> Text(title, color = ink, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 14.dp)) }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Dumb TV", color = SettingsMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF2A3546)))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Text(if (page == 1) "Home apps" else pages[page], color = Glass.Paper, fontSize = 20.sp)
                    Text(when (page) {
                        0 -> "Launcher and privacy preferences"
                        1 -> "$selectedCount selected · Changes save automatically"
                        2 -> "Background and rotation"
                        3 -> "Movies and network filtering"
                        else -> "Open your TV’s system settings"
                    }, color = SettingsMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                    key(page) {
                        if (page == 1) LazyVerticalGrid(columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxWidth().weight(1f).focusRequester(content),
                            contentPadding = PaddingValues(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(apps.sortedBy { it.label.lowercase() }, key = { _, app -> app.packageName }) { index, app ->
                                val checked = visibility[app.packageName] ?: HomeApps.isDefault(app.packageName, app.label)
                                SettingsControl(onClick = { viewModel.setAppVisible(app, !checked) }, centered = false,
                                    modifier = Modifier.fillMaxWidth().height(58.dp).focusProperties {
                                        if (index % 2 == 0) left = sidebar[page]
                                        if (index < 2) up = close
                                    }.semantics { role = Role.Checkbox; toggleableState = if (checked) ToggleableState.On else ToggleableState.Off }) { ink ->
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Image(remember(app.icon) { DrawablePainter(app.icon) }, contentDescription = null,
                                            contentScale = ContentScale.Fit, modifier = Modifier.size(28.dp))
                                        Text(app.label, color = ink, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(horizontal = 9.dp))
                                        Box(Modifier.size(17.dp).border(1.dp, if (checked) ink else SettingsMuted, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                                            if (checked) Text("✓", color = ink, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        } else LazyColumn(Modifier.fillMaxWidth().weight(1f).focusRequester(content),
                            contentPadding = PaddingValues(3.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(entries, key = { _, entry -> entry.title }) { index, entry ->
                                SettingsControl(onClick = entry.action, centered = false,
                                    modifier = Modifier.fillMaxWidth().height(66.dp).focusProperties {
                                        left = sidebar[page]
                                        if (index == 0) up = close
                                    }) { ink ->
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                            Text(entry.title, color = ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(entry.detail, color = ink.copy(alpha = 0.65f), fontSize = 11.sp,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                                        }
                                        Text(entry.value, color = ink, fontSize = 12.sp)
                                    }
                                }
                            }
                            if (page == 3 && dns.supported && !dns.allowed) item {
                                Text("Enable DNS control once:\nadb shell pm grant org.dumbtv.launcher android.permission.WRITE_SECURE_SETTINGS",
                                    color = SettingsMuted, fontSize = 11.sp, modifier = Modifier.padding(12.dp))
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
