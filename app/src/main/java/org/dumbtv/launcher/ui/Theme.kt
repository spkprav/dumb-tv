package org.dumbtv.launcher.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import org.dumbtv.launcher.input.RemoteOkInput
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object Glass {
    val Ink = Color(0xFF070B14)
    val Paper = Color(0xFFF7F8FB)
    val Mist = Color(0xFFADBACE)
    val Ice = Color(0xFFF3F7FF)
    val GlassFill = Color(0xFFB8CEE9).copy(alpha = 0.08f)
    val GlassFocused = Color(0xFFB8CEE9).copy(alpha = 0.20f)
    val Line = Color.White.copy(alpha = 0.10f)
    val Sheen = Color.White.copy(alpha = 0.32f)
}

val ShelfPadding = 32.dp

private val Colors = darkColorScheme(
    background = Glass.Ink,
    onBackground = Glass.Paper,
    surface = Glass.GlassFill,
    onSurface = Glass.Paper,
    surfaceVariant = Glass.GlassFocused,
    onSurfaceVariant = Glass.Mist,
    primary = Glass.Ice,
    onPrimary = Glass.Ink,
    secondary = Glass.Ice,
    onSecondary = Glass.Ink,
    border = Glass.Ice,
)

@Composable
fun DumbTvTheme(content: @Composable () -> Unit) {
    val input = remember { RemoteOkInput() }
    CompositionLocalProvider(LocalRemoteOk provides input) {
        MaterialTheme(colorScheme = Colors, content = content)
    }
}
