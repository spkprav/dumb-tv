package org.dumbtv.launcher.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import org.dumbtv.launcher.apps.LaunchableApp
import kotlin.math.abs

/** Dock icons sit directly on the shelf; labels appear only over the focused icon. */
@Composable
internal fun AppDock(apps: List<LaunchableApp>, focus: FocusRequester, loading: Boolean, onChooseApps: () -> Unit,
    onOpen: (LaunchableApp) -> Unit, onInfo: (LaunchableApp) -> Unit) {
    var focusedPackage by remember { mutableStateOf<String?>(null) }
    var rememberedPackage by remember { mutableStateOf<String?>(null) }
    val focusedIndex = apps.indexOfFirst { it.packageName == focusedPackage }
    val rememberedIndex = apps.indexOfFirst { it.packageName == rememberedPackage }.coerceAtLeast(0)
    val scroll = rememberLazyListState()
    val shape = RoundedCornerShape(23.dp)
    BoxWithConstraints(Modifier.fillMaxWidth().height(178.dp), contentAlignment = Alignment.BottomCenter) {
        // The shelf hugs a short selection; larger selections scroll horizontally.
        val extra = if (focusedIndex < 0) 0 else 28 + (if (focusedIndex > 0) 10 else 0) + (if (focusedIndex < apps.lastIndex) 10 else 0)
        val width by animateDpAsState(minOf(maxWidth, (apps.size * 72 + 40 + extra).coerceAtLeast(180).dp),
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 450f), label = "Dock shelf width")
        Box(Modifier.width(width).height(88.dp).padding(bottom = 8.dp)
            .shadow(20.dp, shape, clip = false)
            .background(Brush.verticalGradient(listOf(Color(0x70DFE7ED), Color(0x5A94A2B2), Color(0x80778494))), shape)
            .border(BorderStroke(1.dp, Brush.verticalGradient(listOf(Color(0xB3FFFFFF), Color(0x35FFFFFF), Color(0x65FFFFFF)))), shape)) {
            Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 22.dp)
                .background(Color.White.copy(alpha = 0.35f)))
        }
        LazyRow(state = scroll, modifier = Modifier.width(width).height(174.dp).padding(bottom = 9.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            if (apps.isEmpty()) item {
                GlassCard(onClick = onChooseApps, onLongClick = null, modifier = Modifier.focusRequester(focus)) {
                    Text(if (loading) "Finding apps…" else "Choose apps +", color = Glass.Paper, modifier = Modifier.padding(16.dp))
                }
            }
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                val distance = if (focusedIndex < 0) Int.MAX_VALUE else abs(index - focusedIndex)
                val iconSize by animateDpAsState(when (distance) { 0 -> 88.dp; 1 -> 70.dp; else -> 60.dp },
                    spring(dampingRatio = 0.76f, stiffness = 420f), label = "Dock magnification")
                val cellWidth by animateDpAsState(when (distance) { 0 -> 100.dp; 1 -> 82.dp; else -> 72.dp },
                    spring(dampingRatio = 0.85f, stiffness = 450f), label = "Dock spacing")
                Box(Modifier.width(cellWidth).height(164.dp), contentAlignment = Alignment.BottomCenter) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DockIcon(app, Modifier.size(iconSize)
                            .then(if (index == rememberedIndex) Modifier.focusRequester(focus) else Modifier)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    focusedPackage = app.packageName
                                    rememberedPackage = app.packageName
                                } else if (focusedPackage == app.packageName) focusedPackage = null
                            }, { onOpen(app) }, { onInfo(app) })
                        // A restrained focus dot gives the remote an anchor without boxing the icon.
                        Box(Modifier.height(12.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (distance == 0) Box(Modifier.size(3.dp).background(Color(0xFF26313D), RoundedCornerShape(50)))
                        }
                    }
                    if (distance == 0) Column(
                        Modifier.align(Alignment.BottomCenter).offset(y = -(iconSize + 22.dp)).wrapContentWidth(unbounded = true),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(app.label, color = Color(0xFF20252B), fontSize = 13.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 180.dp).shadow(6.dp, RoundedCornerShape(7.dp))
                                .background(Color(0xF2F0F2F5), RoundedCornerShape(7.dp))
                                .padding(horizontal = 13.dp, vertical = 7.dp))
                        Canvas(Modifier.size(12.dp, 6.dp)) {
                            drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close() }, Color(0xF2F0F2F5))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockIcon(app: LaunchableApp, modifier: Modifier, onOpen: () -> Unit, onInfo: () -> Unit) {
    val menu = remember { RemoteMenuAction() }
    val currentInfo by rememberUpdatedState(onInfo)
    Card(onClick = onOpen, onLongClick = {
        if (menu.keyHeld) menu.pending = { currentInfo() } else currentInfo()
    }, modifier = modifier.remoteOk(menu),
        shape = CardDefaults.shape(RoundedCornerShape(14.dp)),
        colors = CardDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent),
        scale = CardDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 0.94f),
        border = CardDefaults.border(border = Border.None, focusedBorder = Border.None, pressedBorder = Border.None)) {
        Image(painter = remember(app.icon) { DrawablePainter(app.icon) }, contentDescription = app.label,
            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(2.dp))
    }
}
