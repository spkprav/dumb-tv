@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package org.dumbtv.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val HeaderInk = Color(0xFF172126)
private val HeaderSelection = Color(0xFFD2E3ED)

@Composable
internal fun LauncherHeader(activeTab: Int, navFocus: List<FocusRequester>,
    onHeaderFocused: (Int) -> Unit, onEnterContent: () -> Unit,
    onWallpaper: () -> Unit, onSettings: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var focused by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(60000 - now % 60000) }
    }
    val time = remember(now) { SimpleDateFormat("H:mm", Locale.getDefault()).format(Date(now)) }
    val order = listOf(0, 1, 2, 3) // Pages, wallpaper, Settings.
    fun controlModifier(index: Int): Modifier {
        val position = order.indexOf(index)
        return Modifier.focusRequester(navFocus[index]).onFocusChanged {
            if (it.isFocused) { focused = index; onHeaderFocused(index) }
            else if (focused == index) focused = null
        }.focusProperties {
            left = if (position > 0) navFocus[order[position - 1]] else FocusRequester.Cancel
            right = if (position < order.lastIndex) navFocus[order[position + 1]] else FocusRequester.Cancel
            up = FocusRequester.Cancel
        }.onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            if (key.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                if (key.action == android.view.KeyEvent.ACTION_DOWN && key.repeatCount == 0) onEnterContent()
                true
            } else false
        }
    }
    Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically) {
        // Decorative avatar, with no unimplemented profile action.
        HeaderAvatar()
        Spacer(Modifier.width(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Apps", "Movies").forEachIndexed { index, label ->
                HeaderControl(onClick = { onHeaderFocused(index); onEnterContent() },
                    modifier = controlModifier(index).height(40.dp).width(if (index == 0) 78.dp else 90.dp),
                    selected = index == activeTab) {
                    Text(label, color = if (index == activeTab || focused == index) HeaderInk else Glass.Paper,
                        fontSize = 16.sp)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Row(Modifier.background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            HeaderControl(onClick = { onHeaderFocused(2); onWallpaper() }, modifier = controlModifier(2).size(36.dp)
                .semantics { contentDescription = "Next wallpaper" }, selected = false) {
                HeaderSymbol(wallpaper = true, tint = if (focused == 2) HeaderInk else Glass.Paper)
            }
            HeaderControl(onClick = { onHeaderFocused(3); onSettings() }, modifier = controlModifier(3).size(36.dp)
                .semantics { contentDescription = "Settings" }, selected = false) {
                HeaderSymbol(wallpaper = false, tint = if (focused == 3) HeaderInk else Glass.Paper)
            }
        }
        Spacer(Modifier.weight(1f))
        Text("$time  |  Dumb TV", color = Glass.Paper.copy(alpha = 0.8f), fontSize = 17.sp,
            modifier = Modifier.padding(start = 18.dp))
    }
}

@Composable
private fun HeaderControl(onClick: () -> Unit, modifier: Modifier, selected: Boolean,
    content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(50)
    Card(onClick = onClick, modifier = modifier.remoteOk(), shape = CardDefaults.shape(shape),
        colors = CardDefaults.colors(containerColor = if (selected) HeaderSelection else Color.Transparent,
            focusedContainerColor = HeaderSelection, pressedContainerColor = HeaderSelection),
        scale = CardDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 0.97f),
        border = CardDefaults.border(border = Border.None,
            focusedBorder = Border(BorderStroke(4.dp, Color.White.copy(alpha = 0.28f)), inset = (-4).dp, shape = shape))) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun HeaderAvatar() {
    Canvas(Modifier.size(40.dp).semantics { contentDescription = "TV avatar" }) {
        val w = size.width
        drawCircle(Color.White.copy(alpha = 0.35f))
        drawCircle(Color(0xFFD7E1DF), radius = w * 0.44f)
        val shoulders = Path().apply {
            moveTo(w * 0.19f, w * 0.83f)
            cubicTo(w * 0.22f, w * 0.58f, w * 0.78f, w * 0.58f, w * 0.81f, w * 0.83f)
            quadraticBezierTo(w * 0.5f, w * 1.02f, w * 0.19f, w * 0.83f)
        }
        drawPath(shoulders, Color(0xFF456F83))
        drawOval(Color(0xFF303239), topLeft = androidx.compose.ui.geometry.Offset(w * 0.3f, w * 0.16f),
            size = androidx.compose.ui.geometry.Size(w * 0.4f, w * 0.48f))
        drawOval(Color(0xFFF0C6A9), topLeft = androidx.compose.ui.geometry.Offset(w * 0.35f, w * 0.27f),
            size = androidx.compose.ui.geometry.Size(w * 0.3f, w * 0.34f))
        drawArc(Color(0xFF303239), 185f, 170f, false, androidx.compose.ui.geometry.Offset(w * 0.31f, w * 0.17f),
            androidx.compose.ui.geometry.Size(w * 0.38f, w * 0.28f), style = Stroke(w * 0.08f))
    }
}

@Composable
private fun HeaderSymbol(wallpaper: Boolean, tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val stroke = Stroke(w * 0.085f)
        if (wallpaper) {
            drawRoundRect(tint, androidx.compose.ui.geometry.Offset(w * 0.09f, w * 0.19f),
                androidx.compose.ui.geometry.Size(w * 0.82f, w * 0.65f),
                androidx.compose.ui.geometry.CornerRadius(w * 0.04f), style = stroke)
            drawCircle(tint, radius = w * 0.065f, center = androidx.compose.ui.geometry.Offset(w * 0.66f, w * 0.37f))
            drawPath(Path().apply {
                moveTo(w * 0.19f, w * 0.72f); lineTo(w * 0.4f, w * 0.43f)
                lineTo(w * 0.57f, w * 0.64f); lineTo(w * 0.69f, w * 0.52f); lineTo(w * 0.84f, w * 0.72f)
            }, tint, style = stroke)
        } else {
            val gear = Path()
            repeat(32) { step ->
                val angle = step * Math.PI * 2 / 32 - Math.PI / 2
                val radius = w * if (step % 4 == 1 || step % 4 == 2) 0.44f else 0.34f
                val x = w / 2 + cos(angle).toFloat() * radius
                val y = w / 2 + sin(angle).toFloat() * radius
                if (step == 0) gear.moveTo(x, y) else gear.lineTo(x, y)
            }
            gear.close()
            drawPath(gear, tint, style = stroke)
            drawCircle(tint, radius = w * 0.13f, style = stroke)
        }
    }
}
