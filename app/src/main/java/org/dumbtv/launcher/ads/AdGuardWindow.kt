package org.dumbtv.launcher.ads

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager

/** All window/audio changes run on the main thread. Does not request audio focus or pause video. */
class AdGuardWindow(
    private val context: Context,
    private val log: (String, String) -> Unit,
    private val stop: (String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val windows = context.getSystemService(WindowManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val policy = AdGuardPolicy()
    private var view: View? = null
    private var ownsMute = false
    private var closed = false
    private var lastObservation = 0L
    private val watchdog = object : Runnable {
        override fun run() {
            if (closed || view == null) return
            if (SystemClock.elapsedRealtime() - lastObservation > 12000L) {
                close("No fresh OCR for 12 seconds")
                stop("Ad guard stopped: no fresh OCR")
            } else main.postDelayed(this, 1000L)
        }
    }

    fun observe(ad: Boolean?, fresh: Boolean) = main.post {
        if (closed) return@post
        try {
            if (fresh && ad != null) lastObservation = SystemClock.elapsedRealtime()
            val cover = policy.observe(if (fresh) ad else null)
            if (cover && view == null) show()
            else if (!cover && view != null) restore("Ad badge absent in two consecutive samples")
        } catch (failure: Exception) {
            log("guard_error", "${failure.javaClass.simpleName}: ${failure.message}")
            close("Guard error")
            stop("Ad guard failed: ${failure.message}")
        }
    }

    private fun show() {
        check(Settings.canDrawOverlays(context)) { "Allow display over other apps first" }
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windows.defaultDisplay.getRealMetrics(metrics)
        val black = View(context).apply {
            setBackgroundColor(Color.BLACK)
            isFocusableInTouchMode = true
            contentDescription = "Ad cover. Press Back or OK to stop and restore audio."
            setOnKeyListener { _, key, event ->
                if (key in listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_ESCAPE)) {
                    if (event.action == KeyEvent.ACTION_UP) {
                        close("Remote exit")
                        stop("Stopped by remote")
                    }
                    true
                } else key in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
            }
        }
        @Suppress("DEPRECATION")
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(metrics.widthPixels * 8 / 10, metrics.heightPixels * 8 / 10,
            type, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE).apply { gravity = Gravity.CENTER; title = "Ad cover" }
        view = black
        windows.addView(black, params)
        black.requestFocus()
        log("overlay_shown", "Black center 80% × 80%; 10% margins; Back/OK stops guard")
        main.postDelayed(watchdog, 1000L)
        // Audio failure must not suppress the visual cover or remote escape.
        runCatching {
            log("audio_capabilities", "fixed=${audio.isVolumeFixed}; muted=${audio.isStreamMute(AudioManager.STREAM_MUSIC)}; volume=${audio.getStreamVolume(AudioManager.STREAM_MUSIC)}")
            if (!audio.isStreamMute(AudioManager.STREAM_MUSIC)) {
                ownsMute = true
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) log("audio_muted", "Media stream muted by ad guard")
                else {
                    log("audio_mute_error", "TV did not confirm media mute; cover remains active")
                    android.widget.Toast.makeText(context, "Ad cover active; TV did not confirm mute", android.widget.Toast.LENGTH_LONG).show()
                }
            } else log("audio_already_muted", "Existing mute will be preserved")
        }.onFailure {
            log("audio_mute_error", it.toString())
            android.widget.Toast.makeText(context, "Ad cover active; mute failed", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun restore(reason: String) {
        main.removeCallbacks(watchdog)
        view?.let { black ->
            runCatching { windows.removeViewImmediate(black) }
                .onFailure { log("overlay_remove_error", it.toString()) }
            log("overlay_hidden", reason)
        }
        view = null
        if (ownsMute) {
            runCatching {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                check(!audio.isStreamMute(AudioManager.STREAM_MUSIC)) { "TV still reports muted" }
                ownsMute = false
                log("audio_restored", reason)
            }.onFailure { log("audio_restore_error", it.toString()) }
        }
    }

    fun close(reason: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { close(reason) }; return }
        closed = true
        restore(reason)
    }
}
