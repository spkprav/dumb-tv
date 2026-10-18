package org.dumbtv.launcher.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.dumbtv.launcher.ads.AdCaptureProbeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AdCaptureTestDialog(onStart: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val state by AdCaptureProbeService.status.collectAsState()
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(state.latestBadge) {
        preview = withContext(Dispatchers.IO) { state.latestBadge?.let { BitmapFactory.decodeFile(it) } }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(640.dp).heightIn(max = 470.dp).background(Color(0xFF141C28))
            .verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Ad protection", color = Glass.Paper, fontSize = 24.sp)
            Text("Approve Android screen sharing, then open any app. Monitoring follows the screen across app switches every 3 seconds until you press Stop or Android ends sharing. Ad detection currently recognizes readable Ad badges in the top-right corner. Only the latest 30 screenshots are retained.", color = Glass.Mist, fontSize = 14.sp)
            Text(state.message, color = Glass.Paper, fontSize = 16.sp)
            Text(state.controlMessage, color = Glass.Mist, fontSize = 13.sp)
            if (state.frames > 0) Text("${state.frames} retained screenshots · ${state.tagged} tagged", color = Glass.Mist, fontSize = 13.sp)
            preview?.let { bitmap ->
                Image(bitmap.asImageBitmap(), contentDescription = "Latest top-right capture from MediaProjection",
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(100.dp).background(Color.Black))
                Text("Latest captured badge region", color = Glass.Mist, fontSize = 12.sp)
            }
            Text("Ad detected: mute media and cover the center with black, leaving 10% margins. Two consecutive checks without the badge restore audio. Press Back or OK while covered to stop immediately. A 12-second OCR timeout also removes the cover and restores audio. An existing mute stays muted.", color = Glass.Mist, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassCard(onClick = onStart, onLongClick = null, modifier = Modifier.width(190.dp).height(44.dp).focusRequester(first)) {
                    Text(if (state.active) "Monitoring…" else "Start protection", color = Glass.Paper, fontSize = 14.sp, modifier = Modifier.padding(12.dp))
                }
                if (state.active) GlassCard(onClick = {
                    context.startService(Intent(context, AdCaptureProbeService::class.java).setAction(AdCaptureProbeService.STOP))
                }, onLongClick = null, modifier = Modifier.width(90.dp).height(44.dp)) {
                    Text("Stop", color = Glass.Paper, modifier = Modifier.padding(12.dp))
                }
                GlassCard(onClick = onClose, onLongClick = null, modifier = Modifier.width(80.dp).height(44.dp)) {
                    Text("Done", color = Glass.Paper, modifier = Modifier.padding(12.dp))
                }
            }
        }
        LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos { }; first.requestFocus() }
    }
}
