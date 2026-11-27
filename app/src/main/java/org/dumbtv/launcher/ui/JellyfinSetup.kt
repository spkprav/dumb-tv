@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package org.dumbtv.launcher.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import org.dumbtv.launcher.jellyfin.JellyfinLibrary
import org.dumbtv.launcher.jellyfin.JellyfinServer

private val Ink = Color(0xFF172126)
private val Muted = Color(0xFF9CAABD)
private val Panel = Color(0xFF141C28)
private val RowColor = Color(0xFF1E2939)
private val Active = Color(0xFF2D4058)
private val Focused = Color(0xFFD2E3ED)
private val ErrorColor = Color(0xFFFFB4A2)
private val Shape = RoundedCornerShape(12.dp)

@Composable
internal fun JellyfinSetupDialog(library: JellyfinLibrary, onClose: () -> Unit) {
    val libraryState by library.state.collectAsState()
    val server by library.server.collectAsState()
    var editing by remember { mutableStateOf(false) }
    var secure by remember { mutableStateOf(server?.secure == true) }
    var active by remember { mutableStateOf(Field.Address) }
    var shifted by remember { mutableStateOf(false) }
    var symbols by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(false) }
    val address = remember { Draft(server?.host.orEmpty(), 300) { it.code in 33..126 } }
    val port = remember { Draft(server?.port?.toString() ?: "8096", 5, Char::isDigit) }
    val username = remember { Draft("", 128) { it.code in 32..126 } }
    val password = remember { Draft("", 256) { it.code in 32..126 } }
    val addressFocus = remember { FocusRequester() }
    val refreshFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    val mode = when {
        libraryState.code != null -> "code"
        libraryState.connected && !editing -> "status"
        else -> "form"
    }
    val draft = when (active) {
        Field.Address -> address
        Field.Port -> port
        Field.Username -> username
        Field.Password -> password
    }
    LaunchedEffect(server?.origin) {
        val current = server ?: return@LaunchedEffect
        if (address.text.isEmpty()) {
            address.replace(current.host)
            port.replace(current.port.toString())
            secure = current.secure
        }
    }
    LaunchedEffect(libraryState.loading, libraryState.code, libraryState.error, libraryState.connected) {
        if (!attempt || libraryState.loading || libraryState.code != null) return@LaunchedEffect
        attempt = false
        busy = null
        if (libraryState.connected) {
            password.clear()
            editing = false
        }
    }
    LaunchedEffect(mode) {
        val target = when (mode) {
            "status" -> refreshFocus
            "code" -> closeFocus
            else -> addressFocus
        }
        repeat(6) {
            withFrameNanos { }
            if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    BackHandler(enabled = libraryState.code != null || libraryState.loading || (editing && libraryState.connected)) {
        if (libraryState.loading || libraryState.code != null) library.cancelConnect() else editing = false
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val width = if (maxWidth != Dp.Infinity) maxWidth - 48.dp else 900.dp
            val height = if (maxHeight != Dp.Infinity) maxHeight - 36.dp else 520.dp
            Column(Modifier.size(width, height).clip(RoundedCornerShape(22.dp)).background(Panel)
                .border(1.dp, Color(0xFF354153), RoundedCornerShape(22.dp)).padding(24.dp)
                .onPreviewKeyEvent { event ->
                    if (mode != "form") return@onPreviewKeyEvent false
                    val key = event.nativeKeyEvent
                    when (key.keyCode) {
                        KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> {
                            if (key.action == KeyEvent.ACTION_DOWN) draft.backspace()
                            true
                        }
                        else -> {
                            if (key.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                            val ch = key.unicodeChar.toChar()
                            if (ch.code in 32..126) {
                                draft.insert(ch.toString())
                                true
                            } else false
                        }
                    }
                }) {
                Header(onClose = onClose, closeFocus = closeFocus)
                Spacer(Modifier.height(12.dp))
                when (mode) {
                    "code" -> CodeBody(libraryState.code.orEmpty(), libraryState.error, library)
                    "status" -> StatusBody(library, libraryState, server?.origin, refreshFocus, onEdit = { editing = true })
                    else -> FormBody(
                        address, port, username, password, secure, active, shifted, symbols, busy, libraryState.error,
                        addressFocus,
                        onActive = { active = it },
                        onSecure = { next ->
                            if (next == secure) return@FormBody
                            if (next && port.text == "8096") port.replace("8920")
                            if (!next && port.text == "8920") port.replace("8096")
                            secure = next
                        },
                        onShift = { shifted = !shifted },
                        onSymbols = { symbols = !symbols },
                        onSignIn = {
                            if (libraryState.loading || busy != null) return@FormBody
                            attempt = true
                            busy = "Signing in…"
                            library.signIn(address.text, port.text, secure, username.text, password.text)
                        },
                        onQuick = {
                            if (libraryState.loading || busy != null) return@FormBody
                            attempt = true
                            busy = "Contacting server…"
                            library.connect(address.text, port.text, secure)
                        },
                        onInsert = draft::insert,
                        onBackspace = draft::backspace,
                        onClear = draft::clear,
                        onMove = draft::move,
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(onClose: () -> Unit, closeFocus: FocusRequester) {
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Movie library", color = Glass.Paper, fontSize = 28.sp)
        Spacer(Modifier.weight(1f))
        TvKey("Done", Modifier.width(96.dp).height(40.dp).focusRequester(closeFocus)
            .focusProperties { up = FocusRequester.Cancel; right = FocusRequester.Cancel }) { onClose() }
    }
}

@Composable
private fun ColumnScope.FormBody(
    address: Draft, port: Draft, username: Draft, password: Draft,
    secure: Boolean, active: Field, shifted: Boolean, symbols: Boolean, busy: String?, error: String?,
    addressFocus: FocusRequester,
    onActive: (Field) -> Unit, onSecure: (Boolean) -> Unit, onShift: () -> Unit, onSymbols: () -> Unit,
    onSignIn: () -> Unit, onQuick: () -> Unit,
    onInsert: (String) -> Unit, onBackspace: () -> Unit, onClear: () -> Unit, onMove: (Int) -> Unit,
) {
    val attempt = JellyfinServer.parse(address.text, port.text, secure)
    val preview = when {
        address.text.isBlank() -> "IP address or hostname. A server on your network is usually HTTP, port 8096."
        attempt is JellyfinServer.Attempt.Valid -> attempt.server.origin
        attempt is JellyfinServer.Attempt.Rejected -> attempt.message
        else -> ""
    }
    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(0.44f).fillMaxHeight().verticalScroll(rememberScrollState()).focusGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Server", color = Muted, fontSize = 13.sp)
            EntryField("Address", address.shown(mask = false, active = active == Field.Address, hint = "192.168.1.20"),
                active == Field.Address, Modifier.focusRequester(addressFocus)) { onActive(Field.Address) }
            EntryField("Port", port.shown(mask = false, active = active == Field.Port, hint = "8096"),
                active == Field.Port) { onActive(Field.Port) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TvKey("HTTP", Modifier.weight(1f).height(48.dp), selected = !secure) { onSecure(false) }
                TvKey("HTTPS", Modifier.weight(1f).height(48.dp), selected = secure) { onSecure(true) }
            }
            Text(preview, color = if (attempt is JellyfinServer.Attempt.Valid) Glass.Paper else Muted, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text("Account", color = Muted, fontSize = 13.sp)
            EntryField("Username", username.shown(mask = false, active = active == Field.Username, hint = "username"),
                active == Field.Username) { onActive(Field.Username) }
            EntryField("Password", password.shown(mask = true, active = active == Field.Password, hint = "password"),
                active == Field.Password) { onActive(Field.Password) }
            Text("The password is sent to your server and is not saved on the TV.", color = Muted, fontSize = 12.sp)
            TvKey(busy ?: "Sign in", Modifier.fillMaxWidth().height(52.dp), primary = true, onClick = onSignIn)
            TvKey(if (busy == "Contacting server…") "Contacting server…" else "Quick Connect", Modifier.fillMaxWidth().height(48.dp), onClick = onQuick)
            error?.let { Text(it, color = ErrorColor, fontSize = 14.sp) }
        }
        Column(Modifier.weight(0.56f).fillMaxHeight().focusGroup()) {
            Text(if (active == Field.Port) "Port" else "Keyboard · ${active.label}", color = Glass.Paper, fontSize = 18.sp)
            Text("Remote or USB keyboard", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp, top = 2.dp))
            if (active == Field.Port) {
                Numpad(onInsert, onBackspace, onClear, onMove)
            } else {
                Letters(symbols, shifted, onInsert, onBackspace, onClear, onMove, onShift, onSymbols)
            }
        }
    }
}

@Composable
private fun ColumnScope.StatusBody(library: JellyfinLibrary, state: JellyfinLibrary.State, origin: String?, refreshFocus: FocusRequester, onEdit: () -> Unit) {
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Connected", color = Glass.Paper, fontSize = 22.sp)
        Text(origin ?: "Server saved", color = Muted, fontSize = 16.sp)
        Text(when {
            state.loading && state.movies.isEmpty() -> "Loading movies…"
            else -> "${state.movies.size} recent movies on Home"
        }, color = Glass.Paper, fontSize = 16.sp)
        state.error?.let { Text(it, color = ErrorColor, fontSize = 14.sp) }
        TvKey(if (state.loading) "Refreshing…" else "Refresh movies", Modifier.fillMaxWidth().height(56.dp).focusRequester(refreshFocus), primary = true) {
            library.refresh(force = true)
        }
        TvKey("Change server", Modifier.fillMaxWidth().height(56.dp), onClick = onEdit)
        TvKey("Disconnect", Modifier.fillMaxWidth().height(56.dp)) { library.disconnect() }
    }
}

@Composable
private fun ColumnScope.CodeBody(code: String, error: String?, library: JellyfinLibrary) {
    Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Quick Connect", color = Glass.Paper, fontSize = 22.sp)
        Text(code, color = Glass.Paper, fontSize = 56.sp, letterSpacing = 8.sp)
        Text("On your phone or computer, open Jellyfin, then your profile, then Quick Connect, and approve this code.",
            color = Muted, fontSize = 16.sp)
        error?.let { Text(it, color = ErrorColor, fontSize = 14.sp) }
        TvKey("Cancel", Modifier.width(220.dp).height(52.dp)) { library.cancelConnect() }
    }
}

@Composable
private fun EntryField(label: String, value: AnnotatedString, active: Boolean, modifier: Modifier = Modifier, onFocused: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Card(onClick = onFocused, modifier = modifier.fillMaxWidth().height(64.dp).onFocusChanged {
        focused = it.isFocused
        if (it.isFocused) onFocused()
    }.remoteOk(),
        shape = CardDefaults.shape(Shape),
        scale = CardDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 0.98f),
        colors = CardDefaults.colors(containerColor = if (active) Active else RowColor, focusedContainerColor = Focused),
        border = CardDefaults.border(
            border = if (active) Border(BorderStroke(2.dp, Glass.Ice), shape = Shape) else Border.None,
            focusedBorder = Border(BorderStroke(2.dp, Glass.Paper), shape = Shape),
        )) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center) {
            Text(label, color = if (focused) Ink.copy(alpha = 0.65f) else Muted, fontSize = 12.sp)
            Text(value, color = if (focused) Ink else Glass.Paper, fontSize = 18.sp, maxLines = 1, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun Letters(symbols: Boolean, shifted: Boolean, onInsert: (String) -> Unit, onBackspace: () -> Unit,
    onClear: () -> Unit, onMove: (Int) -> Unit, onShift: () -> Unit, onSymbols: () -> Unit) {
    val rows = if (symbols) symbolRows else letterRows
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { ch ->
                    val shown = if (!symbols && shifted && ch.isLetter()) ch.uppercaseChar() else ch
                    TvKey(shown.toString(), Modifier.weight(1f).height(46.dp)) { onInsert(shown.toString()) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TvKey("Aa", Modifier.weight(1.2f).height(46.dp), selected = shifted, onClick = onShift)
            TvKey(if (symbols) "abc" else "123", Modifier.weight(1.2f).height(46.dp), selected = symbols, onClick = onSymbols)
            TvKey("←", Modifier.weight(0.8f).height(46.dp)) { onMove(-1) }
            TvKey("space", Modifier.weight(2.2f).height(46.dp)) { onInsert(" ") }
            TvKey("→", Modifier.weight(0.8f).height(46.dp)) { onMove(1) }
            TvKey("⌫", Modifier.weight(1f).height(46.dp), onClick = onBackspace)
            TvKey("clear", Modifier.weight(1.2f).height(46.dp), onClick = onClear)
        }
    }
}

@Composable
private fun Numpad(onInsert: (String) -> Unit, onBackspace: () -> Unit, onClear: () -> Unit, onMove: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("123", "456", "789").forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { ch -> TvKey(ch.toString(), Modifier.weight(1f).height(58.dp)) { onInsert(ch.toString()) } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvKey("←", Modifier.weight(1f).height(58.dp)) { onMove(-1) }
            TvKey("0", Modifier.weight(1f).height(58.dp)) { onInsert("0") }
            TvKey("→", Modifier.weight(1f).height(58.dp)) { onMove(1) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvKey("Delete", Modifier.weight(1f).height(58.dp), onClick = onBackspace)
            TvKey("Clear", Modifier.weight(1f).height(58.dp), onClick = onClear)
        }
    }
}

@Composable
private fun TvKey(label: String, modifier: Modifier, selected: Boolean = false, primary: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Card(onClick = onClick, modifier = modifier.onFocusChanged { focused = it.isFocused }.remoteOk(),
        shape = CardDefaults.shape(Shape),
        scale = CardDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 0.98f),
        colors = CardDefaults.colors(
            containerColor = when {
                primary -> Focused
                selected -> Active
                else -> RowColor
            },
            focusedContainerColor = if (primary) Color.White else Focused,
        ),
        border = CardDefaults.border(border = Border.None, focusedBorder = Border(BorderStroke(2.dp, Glass.Paper), shape = Shape))) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, color = if (focused || primary) Ink else Glass.Paper, fontSize = if (label.length > 2) 14.sp else 18.sp, maxLines = 1)
        }
    }
}

private enum class Field(val label: String) { Address("address"), Port("port"), Username("username"), Password("password") }

private class Draft(initial: String, private val limit: Int, private val accept: (Char) -> Boolean) {
    var text by mutableStateOf(initial)
    var cursor by mutableIntStateOf(initial.length)

    fun insert(value: String) {
        val clean = value.filter(accept)
        if (clean.isEmpty()) return
        val at = cursor.coerceIn(0, text.length)
        val next = (text.substring(0, at) + clean + text.substring(at)).take(limit)
        cursor = (at + clean.length).coerceAtMost(next.length)
        text = next
    }

    fun backspace() {
        val at = cursor.coerceIn(0, text.length)
        if (at == 0) return
        text = text.removeRange(at - 1, at)
        cursor = at - 1
    }

    fun move(delta: Int) { cursor = (cursor + delta).coerceIn(0, text.length) }

    fun clear() { text = ""; cursor = 0 }

    fun replace(value: String) {
        text = value.filter(accept).take(limit)
        cursor = text.length
    }

    fun shown(mask: Boolean, active: Boolean, hint: String): AnnotatedString {
        if (text.isEmpty()) {
            return buildAnnotatedString {
                if (active) append("|")
                else withStyle(SpanStyle(color = Color(0xFF7E8DA3))) { append(hint) }
            }
        }
        val raw = if (mask && !active) "•".repeat(text.length) else text
        val at = cursor.coerceIn(0, raw.length)
        val limitWindow = 28
        val start = if (raw.length <= limitWindow) 0 else (at - 20).coerceIn(0, (raw.length - limitWindow).coerceAtLeast(0))
        val end = (start + limitWindow).coerceAtMost(raw.length)
        val slice = raw.substring(start, end)
        val mark = (at - start).coerceIn(0, slice.length)
        return buildAnnotatedString {
            append(slice.substring(0, mark))
            if (active) append("|")
            append(slice.substring(mark))
        }
    }
}

private val letterRows = listOf(
    "1234567890".toList(),
    "qwertyuiop".toList(),
    "asdfghjkl.".toList(),
    "zxcvbnm-:/".toList(),
)

private val symbolRows = listOf(
    "1234567890".toList(),
    listOf('!', '@', '#', '$', '%', '^', '&', '*', '(', ')'),
    listOf('-', '_', '=', '+', '[', ']', '{', '}', '~', '`'),
    listOf('\'', '"', ';', ':', ',', '.', '?', '/', '\\', '|'),
)
