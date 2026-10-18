package org.dumbtv.launcher.ui

import android.util.Log
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import org.dumbtv.launcher.input.RemoteOkInput
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

internal val LocalRemoteOk = staticCompositionLocalOf<RemoteOkInput> { error("Missing remote input handler") }

internal class RemoteMenuAction { var pending: (() -> Unit)? = null; var keyHeld = false }

/** No timers or click callbacks here: accepted events go to the native TV control exactly once. */
@Composable
internal fun Modifier.remoteOk(menu: RemoteMenuAction? = null): Modifier {
    val input = LocalRemoteOk.current
    val owner = remember { Any() }
    val scope = rememberCoroutineScope()
    val currentMenu = rememberUpdatedState(menu)
    DisposableEffect(owner) {
        onDispose { input.cancel(owner); currentMenu.value?.let { it.pending = null; it.keyHeld = false } }
    }
    return this.onFocusChanged {
        if (!it.isFocused) { input.cancel(owner); currentMenu.value?.let { it.pending = null; it.keyHeld = false } }
    }.onPreviewKeyEvent { composeEvent ->
        val event = composeEvent.nativeKeyEvent
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                val consumed = when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        val accepted = input.down(owner, event.downTime, event.keyCode, event.deviceId, event.repeatCount)
                        if (accepted) currentMenu.value?.keyHeld = true
                        !accepted
                    }
                    KeyEvent.ACTION_UP -> {
                        val accepted = input.up(owner, event.downTime, event.keyCode, event.deviceId, event.isCanceled)
                        val action = currentMenu.value?.pending
                        currentMenu.value?.let { it.pending = null; it.keyHeld = false }
                        if (accepted && action != null) scope.launch {
                            // Let TV Card consume this release/reset its pressed state before opening a dialog.
                            yield()
                            action()
                        }
                        !accepted
                    }
                    else -> true
                }
                if (Log.isLoggable("DumbTvInput", Log.DEBUG)) {
                    Log.d("DumbTvInput", "action=${event.action} key=${event.keyCode} repeat=${event.repeatCount} down=${event.downTime} time=${event.eventTime} device=${event.deviceId} consumed=$consumed")
                }
                consumed
            }
            else -> false
        }
    }
}
