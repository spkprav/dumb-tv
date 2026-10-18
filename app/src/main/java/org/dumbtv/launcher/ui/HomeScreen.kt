@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package org.dumbtv.launcher.ui

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import org.dumbtv.launcher.input.HomeNavigation
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import org.dumbtv.launcher.HomeViewModel
import org.dumbtv.launcher.apps.LaunchableApp
import org.dumbtv.launcher.dns.PrivateDns
import org.dumbtv.launcher.settings.SettingsDestination
import org.dumbtv.launcher.settings.SettingsDestinations
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Panel = RoundedCornerShape(16.dp)
private val QuietBorder = BorderStroke(
    width = 1.dp,
    brush = Brush.verticalGradient(listOf(Glass.Sheen, Glass.Line)),
)
private val FocusBorder = BorderStroke(2.dp, Glass.Ice)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    dns: PrivateDns.Snapshot,
    dnsBusy: Boolean,
    onToggleDns: () -> Unit,
    onToggleFirewall: () -> Unit,
    onAdCaptureTest: () -> Unit,
    defaultHome: Boolean,
    homeOverride: Boolean,
    homeRequest: Int,
    onHomeOverride: () -> Unit,
    onChooseHome: () -> Unit,
) {
    val firewall by org.dumbtv.launcher.privacy.TrackerFirewallService.status.collectAsState()
    val jellyfinState by viewModel.jellyfin.state.collectAsState()
    var jellyfinOpen by remember { mutableStateOf(false) }
    var captureTestOpen by remember { mutableStateOf(false) }
    var privacyOpen by remember { mutableStateOf(false) }
    val apps by viewModel.apps.collectAsState()
    val visibility by viewModel.appVisibility.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val displayedApps = remember(apps, visibility) {
        apps.filter { visibility[it.packageName] ?: org.dumbtv.launcher.apps.HomeApps.isDefault(it.packageName, it.label) }
            .sortedWith(compareBy<LaunchableApp> { org.dumbtv.launcher.apps.HomeApps.order(it.packageName, it.label) }
                .thenBy { it.label.lowercase() })
    }
    var appsTab by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<LaunchableApp?>(null) }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val dockFocus = remember { FocusRequester() }
    val moviesFocus = remember { FocusRequester() }
    var navigation by remember { mutableStateOf(HomeNavigation()) }
    val activeTab = navigation.tab
    val moviesOpen = activeTab == 1
    val navFocus = remember { List(4) { FocusRequester() } }
    var contentHasFocus by remember { mutableStateOf(false) }
    var transitionKey by remember { mutableStateOf<Int?>(null) }
    val modalOpen = settingsOpen || jellyfinOpen || privacyOpen || captureTestOpen || selected != null

    // Only explicit navigation actions request focus. Loading data never steals it.
    LaunchedEffect(navigation.request) {
        if (modalOpen) return@LaunchedEffect
        val instruction = navigation
        androidx.compose.runtime.withFrameNanos { }
        if (instruction.target >= 0) runCatching { navFocus[instruction.target].requestFocus() }
        else {
            if (moviesOpen) grid.scrollToItem(0)
            androidx.compose.runtime.withFrameNanos { }
            runCatching { when (activeTab) {
                1 -> if (jellyfinState.movies.isNotEmpty() || !jellyfinState.connected) moviesFocus.requestFocus()
                    else navFocus[1].requestFocus()
                else -> dockFocus.requestFocus()
            } }
        }
    }
    var previousModal by remember { mutableStateOf(false) }
    var modalReturnTarget by remember { mutableStateOf(-1) }
    LaunchedEffect(modalOpen) {
        if (modalOpen && !previousModal) modalReturnTarget = navigation.target
        if (!modalOpen && previousModal) navigation = navigation.restore(modalReturnTarget)
        previousModal = modalOpen
    }
    // Replace the temporary loading tile once, without affecting header/modal focus.
    LaunchedEffect(displayedApps.isNotEmpty()) {
        if (displayedApps.isNotEmpty() && activeTab == 0 && navigation.target == -1 && !modalOpen)
            navigation = navigation.enterContent()
    }
    LaunchedEffect(homeRequest) {
        if (homeRequest > 0) {
            previousModal = false
            modalReturnTarget = -1
            selected = null
            settingsOpen = false
            jellyfinOpen = false
            privacyOpen = false
            captureTestOpen = false
            navigation = navigation.home()
            transitionKey = null
            viewModel.jellyfin.cancelConnect()
        }
    }
    BackHandler(enabled = !modalOpen) {
        navigation = if (activeTab != 0) navigation.home() else navigation.header()
    }
    Box(Modifier.fillMaxSize().background(Glass.Ink).onPreviewKeyEvent { event ->
        val key = event.nativeKeyEvent
        if (modalOpen) false
        else if (key.keyCode == transitionKey) {
            if (key.action == android.view.KeyEvent.ACTION_UP) transitionKey = null
            true
        } else if (activeTab == 0 && contentHasFocus && key.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN &&
            key.action == android.view.KeyEvent.ACTION_DOWN && key.repeatCount == 0) {
            transitionKey = key.keyCode
            navigation = navigation.movies()
            true
        } else if (activeTab == 0 && contentHasFocus && key.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP &&
            key.action == android.view.KeyEvent.ACTION_DOWN) {
            transitionKey = key.keyCode
            navigation = navigation.header(0)
            true
        } else false
    }) {
        WallpaperBackground(viewModel.wallpapers)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color(0x18080F1D), Color(0x15080F1D), Color(0xA6080F1D)),
        )))
        Column(Modifier.fillMaxSize().padding(horizontal = ShelfPadding).padding(top = 26.dp)) {
            LauncherHeader(onSettings = { settingsOpen = true }, onWallpaper = { viewModel.wallpapers.next() }, activeTab = activeTab,
                onHeaderFocused = { navigation = navigation.headerFocused(it) }, navFocus = navFocus,
                onEnterContent = { navigation = navigation.enterContent() })
            Column(Modifier.fillMaxWidth().weight(1f).onFocusChanged {
                contentHasFocus = it.hasFocus
                if (it.hasFocus && !modalOpen) navigation = navigation.contentFocused()
            }.focusGroup()) {
            if (moviesOpen) {
                Text("Movies Available", color = Glass.Paper, fontSize = 24.sp, fontWeight = FontWeight.Light,
                    modifier = Modifier.padding(bottom = 12.dp))
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val columns = ((maxWidth.value + 18f) / 128f).toInt().coerceAtLeast(1)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns), state = grid,
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize(),
                    ) {
                        items(jellyfinState.movies.size, key = { "movie:${jellyfinState.movies[it].id}" }, contentType = { "movie" }) { index ->
                            val movie = jellyfinState.movies[index]
                            MovieTile(movie, viewModel.jellyfin, modifier = (if (index == 0) Modifier.focusRequester(moviesFocus) else Modifier).onPreviewKeyEvent { event ->
                                val key = event.nativeKeyEvent
                                if (index < columns && key.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP &&
                                    key.action == android.view.KeyEvent.ACTION_DOWN) {
                                    transitionKey = key.keyCode
                                    navigation = navigation.header(1)
                                    true
                                } else false
                            }) { viewModel.openMovie(movie) }
                        }
                        if (!jellyfinState.connected) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                GlassCard(onClick = { jellyfinOpen = true }, onLongClick = null,
                                    modifier = Modifier.fillMaxWidth().focusRequester(moviesFocus)) {
                                    Text("Connect your movie library →", color = Glass.Paper, modifier = Modifier.padding(18.dp))
                                }
                            }
                        } else if (jellyfinState.loading && jellyfinState.movies.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) { QuietLine("Loading movies…") }
                        }
                        jellyfinState.error?.let { error ->
                            item(span = { GridItemSpan(maxLineSpan) }) { QuietLine(error) }
                        }
                        if (jellyfinState.connected && !jellyfinState.loading && jellyfinState.movies.isEmpty() && jellyfinState.error == null) {
                            item(span = { GridItemSpan(maxLineSpan) }) { QuietLine("No movies are available for this profile.") }
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
                AppDock(displayedApps, focus = dockFocus, loading = loading,
                    onChooseApps = { appsTab = true; settingsOpen = true },
                    onOpen = { viewModel.launch(it) }, onInfo = { selected = it })
                Spacer(Modifier.height(18.dp))
            }
            }
        }
    }
    if (settingsOpen) SettingsDialog(viewModel, initialApps = appsTab,
        defaultHome = defaultHome, homeOverride = homeOverride, dns = dns, dnsBusy = dnsBusy,
        onChooseHome = onChooseHome, onHomeOverride = onHomeOverride, onToggleDns = onToggleDns,
        onPrivacy = { settingsOpen = false; privacyOpen = true },
        onCaptureTest = { settingsOpen = false; captureTestOpen = true },
        onJellyfin = { settingsOpen = false; jellyfinOpen = true },
        onClose = { settingsOpen = false; appsTab = false })
    if (captureTestOpen) AdCaptureTestDialog(onStart = onAdCaptureTest, onClose = { captureTestOpen = false })
    if (jellyfinOpen) {
        JellyfinDialog(viewModel.jellyfin, jellyfinState,
            onClose = { jellyfinOpen = false; viewModel.jellyfin.cancelConnect() })
    }
    if (privacyOpen) {
        Dialog(onDismissRequest = { privacyOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.width(660.dp).heightIn(max = 480.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFA1C2435))
                .verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Privacy & tracking", color = Glass.Paper, fontSize = 24.sp)
                Text("Tracker blocking starts automatically after Android VPN approval. Turning it off is remembered.", color = Glass.Paper, fontSize = 16.sp)
                Text("Stop viewing recognition: TV Settings → System / System Preferences → Samba Interactive TV → Privacy → Disagree and Disable (menus vary by model). This app cannot change Sony’s consent settings for you.", color = Glass.Mist, fontSize = 14.sp)
                Text("Also turn off Usage & diagnostics, review Ads / advertising ID, and disable unneeded location permissions in TV settings.", color = Glass.Mist, fontSize = 14.sp)
                GlassCard(onClick = onToggleFirewall, onLongClick = null, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(if (firewall.starting) "Starting firewall…" else if (firewall.active) "Tracker firewall · On · Select to turn off" else "Enable tracker firewall", color = Glass.Paper, fontSize = 16.sp)
                        Text("Blocks network access for installed Samba TV, Sony Select, and Google/Android TV Home and recommendation apps.", color = Glass.Mist, fontSize = 13.sp)
                    }
                }
                if (firewall.blocked.isNotEmpty()) Text("Blocked: " + firewall.blocked.joinToString(), color = Glass.Mist, fontSize = 12.sp)
                firewall.error?.let { Text(it, color = Glass.Mist, fontSize = 13.sp) }
                Text("This local firewall uses Android’s single VPN slot. Keep ‘Block connections without VPN’ OFF so other apps can connect. It does not block core Google Play services or telemetry inside Prime/Hotstar. It cannot guarantee zero tracking.", color = Glass.Mist, fontSize = 13.sp)
                GlassCard(onClick = { viewModel.openSettings(SettingsDestination("Privacy", "", android.provider.Settings.ACTION_PRIVACY_SETTINGS)) },
                    onLongClick = null, modifier = Modifier.fillMaxWidth()) { Text("Open TV privacy settings", color = Glass.Paper, modifier = Modifier.padding(14.dp)) }
                GlassCard(onClick = { viewModel.openSettings(SettingsDestination("VPN", "", android.provider.Settings.ACTION_VPN_SETTINGS)) },
                    onLongClick = null, modifier = Modifier.fillMaxWidth()) { Text("Open VPN settings · Always-on", color = Glass.Paper, modifier = Modifier.padding(14.dp)) }
                Text("After approval, restart is attempted at boot and when Home opens. For Android-managed persistence, select this app as Always-on VPN if your TV offers it; leave Block connections without VPN off. Internet apps can still collect their own data. To prevent the TV sending any internet data, block its internet access at the router and use an external streaming device.", color = Glass.Mist, fontSize = 13.sp)
                TextButton(onClick = { privacyOpen = false }, modifier = Modifier.remoteOk()) { Text("Done", color = Glass.Paper) }
            }
        }
    }
    selected?.let { app ->
        Dialog(onDismissRequest = { selected = null }) {
            Column(Modifier.width(360.dp).clip(RoundedCornerShape(24.dp))
                .background(Color(0xF21C2435)).border(QuietBorder, RoundedCornerShape(24.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(app.label, color = Glass.Paper, fontSize = 22.sp, modifier = Modifier.padding(bottom = 8.dp))
                GlassCard(onClick = { viewModel.setAppVisible(app, false); selected = null },
                    onLongClick = null, modifier = Modifier.fillMaxWidth()) {
                    Text("Hide from Home", color = Glass.Paper, modifier = Modifier.padding(14.dp))
                }
                GlassCard(onClick = { selected = null; viewModel.openAppInfo(app) }, onLongClick = null,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("App info / uninstall", color = Glass.Paper, modifier = Modifier.padding(14.dp))
                }
                TextButton(onClick = { selected = null }, modifier = Modifier.remoteOk()) { Text("Cancel", color = Glass.Mist) }
            }
        }
    }
}

@Composable
private fun MovieTile(movie: org.dumbtv.launcher.jellyfin.JellyfinLibrary.Movie,
    library: org.dumbtv.launcher.jellyfin.JellyfinLibrary, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    var poster by remember(movie.id, movie.imageTag) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(movie.id, movie.imageTag) { poster = library.poster(movie) }
    Column(modifier) {
        GlassCard(onClick = onOpen, onLongClick = null, modifier = Modifier.fillMaxWidth().aspectRatio(0.67f)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (poster != null) Image(poster!!.asImageBitmap(), contentDescription = movie.title,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text(movie.title, color = Glass.Mist, fontSize = 12.sp, maxLines = 3,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
            }
        }
        Text(movie.title, color = Glass.Paper, fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        if (movie.year.isNotEmpty()) Text(movie.year, color = Glass.Mist, fontSize = 10.sp)
    }
}

@Composable
private fun JellyfinDialog(library: org.dumbtv.launcher.jellyfin.JellyfinLibrary,
    state: org.dumbtv.launcher.jellyfin.JellyfinLibrary.State, onClose: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val fieldColors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        focusedTextColor = Glass.Paper, unfocusedTextColor = Glass.Paper,
        focusedLabelColor = Glass.Mist, unfocusedLabelColor = Glass.Mist,
        cursorColor = Glass.Paper, focusedBorderColor = Glass.Ice, unfocusedBorderColor = Glass.Line,
    )
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(600.dp).heightIn(max = 500.dp).clip(RoundedCornerShape(24.dp))
            .background(Color(0xFA1C2435)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your Jellyfin library", color = Glass.Paper, fontSize = 24.sp)
            Text(org.dumbtv.launcher.jellyfin.JellyfinLibrary.SERVER, color = Glass.Mist, fontSize = 13.sp)
            when {
                state.connected -> {
                    Text("Connected · ${state.movies.size} recent movies", color = Glass.Paper)
                    GlassCard(onClick = { library.refresh(force = true) }, onLongClick = null, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.loading) "Refreshing…" else "Refresh movies", color = Glass.Paper, modifier = Modifier.padding(14.dp))
                    }
                    GlassCard(onClick = { library.disconnect() }, onLongClick = null, modifier = Modifier.fillMaxWidth()) {
                        Text("Disconnect this launcher", color = Glass.Paper, modifier = Modifier.padding(14.dp))
                    }
                }
                state.code != null -> {
                    Text(state.code, color = Glass.Paper, fontSize = 44.sp, letterSpacing = 8.sp)
                    Text("On your phone/computer, open Jellyfin → user menu → Quick Connect and approve this code using the profile you want on Home.", color = Glass.Mist, fontSize = 15.sp)
                }
                state.loading -> Text("Connecting…", color = Glass.Paper)
                else -> {
                    OutlinedTextField(username, { username = it }, label = { Text("Username") }, colors = fieldColors, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(password, { password = it }, label = { Text("Password") }, colors = fieldColors, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassCard(onClick = { val entered = password; password = ""; library.signIn(username, entered) }, onLongClick = null, modifier = Modifier.weight(1f)) {
                            Text("Sign in", color = Glass.Paper, modifier = Modifier.padding(14.dp))
                        }
                        GlassCard(onClick = { password = ""; library.connect() }, onLongClick = null, modifier = Modifier.weight(1f)) {
                            Text("Use Quick Connect", color = Glass.Paper, modifier = Modifier.padding(14.dp))
                        }
                    }
                }
            }
            state.error?.let { Text(it, color = Glass.Mist, fontSize = 13.sp) }
            TextButton(onClick = onClose, modifier = Modifier.remoteOk()) { Text("Done", color = Glass.Paper) }
        }
    }
}

@Composable
private fun WallpaperBackground(wallpapers: org.dumbtv.launcher.wallpaper.Wallpapers) {
    val wallpaper by wallpapers.bitmap.collectAsState()
    Crossfade(targetState = wallpaper, animationSpec = tween(400), label = "Wallpaper",
        modifier = Modifier.fillMaxSize()) { image ->
        if (image != null) Image(image.asImageBitmap(), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun ShelfTitle(title: String, note: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, end = 2.dp, top = 12.dp, bottom = 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(text = title, color = Glass.Paper, fontSize = 14.sp, fontWeight = FontWeight.Normal)
        if (note.isNotEmpty()) {
            Text(text = note, color = Glass.Mist, fontSize = 10.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun QuietLine(text: String) {
    Text(
        text = text,
        color = Glass.Mist,
        fontSize = 18.sp,
        modifier = Modifier.padding(horizontal = 2.dp),
    )
}

@Composable
private fun SettingsTile(destination: SettingsDestination, onOpen: () -> Unit) {
    GlassCard(onClick = onOpen, onLongClick = null, modifier = Modifier.fillMaxWidth().height(112.dp)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
            Text(
                text = destination.title,
                color = Glass.Paper,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = destination.detail,
                color = Glass.Mist,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DnsTile(dns: PrivateDns.Snapshot, busy: Boolean, onToggle: () -> Unit) {
    val title = when {
        busy -> "Checking secure DNS…"
        !dns.supported -> "DNS blocking unavailable"
        dns.verified -> "DNS blocking · Connected"
        dns.enabled -> "DNS blocking · Configured, connection unconfirmed"
        else -> "DNS blocking · Off"
    }
    GlassCard(onClick = { if (dns.supported && !busy) onToggle() }, onLongClick = null,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = Glass.Paper, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(if (dns.enabled) "Select to turn off and restore your previous DNS settings."
                else "Select to enable AdGuard DNS for ads and trackers on blocked domains.",
                color = Glass.Mist, fontSize = 13.sp)
            Text("Filters known ad domains. Prime Video ads and Hotstar banners may remain; ads sharing content domains cannot be filtered separately.",
                color = Glass.Mist, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            if (dns.supported && !dns.allowed) {
                Text("Allow DNS control once from your computer:\nadb shell pm grant org.dumbtv.launcher android.permission.WRITE_SECURE_SETTINGS",
                    color = Glass.Mist, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
internal fun GlassCard(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val menu = remember { RemoteMenuAction() }
    val currentLongClick = androidx.compose.runtime.rememberUpdatedState(onLongClick)
    Card(
        onClick = onClick,
        onLongClick = if (onLongClick == null) null else { {
            if (menu.keyHeld) menu.pending = currentLongClick.value else currentLongClick.value?.invoke()
        } },
        modifier = modifier.remoteOk(menu),
        shape = CardDefaults.shape(shape = Panel, focusedShape = Panel, pressedShape = Panel),
        colors = CardDefaults.colors(
            containerColor = Glass.GlassFill,
            contentColor = Glass.Paper,
            focusedContainerColor = Glass.GlassFocused,
            focusedContentColor = Glass.Paper,
            pressedContainerColor = Glass.GlassFocused,
            pressedContentColor = Glass.Paper,
        ),
        scale = CardDefaults.scale(scale = 1f, focusedScale = 1.04f, pressedScale = 1.02f),
        border = CardDefaults.border(
            border = Border(border = QuietBorder, shape = Panel),
            focusedBorder = Border(border = FocusBorder, shape = Panel),
            pressedBorder = Border(border = FocusBorder, shape = Panel),
        ),
        glow = CardDefaults.glow(glow = Glow.None, focusedGlow = Glow.None, pressedGlow = Glow.None),
        content = {
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.015f)),
            )), content = content)
        },
    )
}
