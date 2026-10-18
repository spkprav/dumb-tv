package org.dumbtv.launcher.ads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.AtomicFile
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import org.dumbtv.launcher.MainActivity
import org.dumbtv.launcher.R
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executor
import kotlin.math.roundToInt

/** User-approved OCR timeline with a bounded mute/overlay trial. */
class AdCaptureProbeService : Service() {
    data class State(val active: Boolean = false, val frames: Int = 0, val directory: String? = null,
        val latestBadge: String? = null, val message: String = "Ready to start continuous ad checks", val tagged: Int = 0, val controlMessage: String = "Waiting for an ad")
    private val thread = HandlerThread("AdCaptureProbe")
    private lateinit var worker: Handler
    private val executor = Executor { task -> worker.post(task) }
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var latest: Image? = null
    private var latestReceivedAt = 0L
    private var previousSourceTimestamp: Long? = null
    private var previousSignature: String? = null
    private var startedAt = 0L
    private var startedEpoch = 0L
    private var slot = 0
    private var directory: File? = null
    private val records = mutableListOf<JSONObject>()
    private val queue = java.util.ArrayDeque<JSONObject>()
    private var ocr: LocalFrameOcr? = null
    private var ocrInitializationError: Exception? = null
    private var ocrBusy = false
    private var samplingEnded = false
    private var releasingProjection = false
    private var resultMessage = "Monitoring"
    private var totalCaptured = 0
    private var totalTagged = 0
    private var diagnostic: JSONObject? = null
    private var storageFailure: Exception? = null
    private lateinit var guard: AdGuardWindow
    @Volatile private var finishing = false

    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate(); thread.start(); worker = Handler(thread.looper)
        guard = AdGuardWindow(this,
            log = { name, message ->
                status.value = status.value.copy(controlMessage = "$name: $message")
                worker.post { event(name, JSONObject().put("message", message)) }
            },
            stop = { reason -> worker.post { endSampling(reason) } })
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            guard.close("Stopped by you")
            worker.post { endSampling("Stopped by you") }; return START_NOT_STICKY
        }
        if (status.value.active || intent == null) { if (intent == null) stopSelf(); return START_NOT_STICKY }
        status.value = State(active = true, message = "Starting screen capture…")
        try {
            val manager = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Ad capture test", NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val stop = PendingIntent.getService(this, 2, Intent(this, AdCaptureProbeService::class.java).setAction(STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_block)
                .setContentTitle("Ad protection active").setContentText("Continuous · checks every 3 seconds · Stop restores audio")
                .setContentIntent(open).setOngoing(true).addAction(0, "Stop test", stop).build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(74, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            else startForeground(74, notification)
        } catch (failure: Exception) {
            status.value = State(message = "Screen-capture service failed: ${failure.javaClass.simpleName}")
            stopSelf(); return START_NOT_STICKY
        }
        worker.post { startCapture(intent) }
        return START_NOT_STICKY
    }
    private fun startCapture(intent: Intent) {
        try {
            val consent = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(DATA, Intent::class.java)
                else @Suppress("DEPRECATION") (intent.getParcelableExtra(DATA) as? Intent)
            requireNotNull(consent)
            directory = File(requireNotNull(getExternalFilesDir(null)), "ad-capture").apply { check(mkdirs() || isDirectory) }
            directory!!.listFiles().orEmpty().filter { it.name.matches(Regex("(?:badge|frame)-\\d+\\.(?:png|json)|report\\.json|timeline\\.jsonl|events(?:-previous)?\\.jsonl|index\\.html")) }.forEach { it.delete() }
            event("permission_approved", JSONObject().put("implementation", "continuous_guard_v1").put("interval_ms", CaptureSequence.INTERVAL_MS))
            try { ocr = LocalFrameOcr() }
            catch (failure: Exception) { ocrInitializationError = failure; event("ocr_initialization_error", errorJson(failure)) }
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
            require(metrics.widthPixels > 0 && metrics.heightPixels > 0)
            val scale = (1920f / metrics.widthPixels).coerceAtMost(1f)
            val width = (metrics.widthPixels * scale).roundToInt().coerceAtLeast(1)
            val height = (metrics.heightPixels * scale).roundToInt().coerceAtLeast(1)
            val capture = getSystemService(MediaProjectionManager::class.java).getMediaProjection(intent.getIntExtra(RESULT, 0), consent)
            projection = capture
            capture.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { if (!finishing && !releasingProjection) endSampling("Android ended screen capture") }
            }, worker)
            // Retain one latest image. AcquireLatestImage needs two additional free slots.
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3).also { images ->
                images.setOnImageAvailableListener({ source ->
                    if (!samplingEnded && !finishing) try {
                        source.acquireLatestImage()?.let { image ->
                            latest?.close(); latest = image
                            latestReceivedAt = SystemClock.elapsedRealtime()
                            if (startedAt == 0L) {
                                startedAt = latestReceivedAt; startedEpoch = System.currentTimeMillis()
                                event("capture_started")
                                worker.post { tick(width, height) }
                            }
                        }
                    } catch (failure: Exception) { fail("Could not acquire a screen frame", failure) }
                }, worker)
            }
            display = capture.createVirtualDisplay("Dumb TV capture and tag", width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, worker)
            status.value = status.value.copy(directory = directory!!.absolutePath, message = "Waiting for first frame…")
            worker.postDelayed({ if (startedAt == 0L && !finishing) endSampling("No frames received from Android") }, 10000L)
        } catch (failure: Exception) { fail("Screen capture could not start on this TV", failure) }
    }
    private fun tick(width: Int, height: Int) {
        if (samplingEnded || finishing) return
        val now = SystemClock.elapsedRealtime()
        val index = slot++
        if (ocrBusy || queue.isNotEmpty()) {
            event("sample_skipped", JSONObject().put("slot", index).put("reason", "Previous OCR still running"))
            guard.observe(null, false)
            scheduleNext(width, height)
            return
        }
        val number = index.toString().padStart(3, '0')
        val record = JSONObject().put("slot", index).put("scheduled_elapsed_ms", CaptureSequence.scheduledMs(index))
            .put("captured_elapsed_ms", now - startedAt).put("captured_epoch_ms", System.currentTimeMillis())
            .put("schedule_lateness_ms", now - startedAt - CaptureSequence.scheduledMs(index))
            .put("capture_status", "pending").put("ocr_status", "pending")
        records.add(record)
        var full: Bitmap? = null
        var badge: Bitmap? = null
        try {
            val image = requireNotNull(latest)
            val plane = image.planes[0]
            val packed = CapturePixels.packedRgba(plane.buffer, width, height, plane.pixelStride, plane.rowStride)
            full = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(packed)) }
            val x = width * 3 / 4
            badge = Bitmap.createBitmap(full, x, 0, width - x, (height / 5).coerceAtLeast(1))
            val screen = File(directory, "frame-$number.png")
            val crop = File(directory, "badge-$number.png")
            screen.outputStream().use { check(full.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            crop.outputStream().use { check(badge.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            val stats = screenStats(full)
            record.put("capture_status", "saved").put("frame", screen.name).put("badge", crop.name)
                .put("width", width).put("height", height).put("crop_x", x).put("crop_y", 0)
                .put("crop_width", width - x).put("crop_height", height / 5)
                .put("source_timestamp_ns", image.timestamp).put("source_age_ms", now - latestReceivedAt)
                .put("reused_source_frame", previousSourceTimestamp == image.timestamp)
                .put("source_stale", now - latestReceivedAt > 5000L)
                .put("screen_signature", stats.second).put("dark_pixel_ratio", stats.first)
                .put("changed_since_previous", previousSignature == null || previousSignature != stats.second)
            previousSignature = stats.second; previousSourceTimestamp = image.timestamp
            queue.add(record)
            totalCaptured++
            status.value = status.value.copy(frames = capturedCount(), latestBadge = crop.absolutePath,
                message = "Monitoring every 3 seconds · $totalCaptured captured · $totalTagged tagged")
            event("frame_captured", JSONObject().put("slot", index).put("frame", screen.name))
        } catch (failure: Exception) {
            record.put("capture_status", "error").put("ocr_status", "not_run").put("screen_tag", "capture_error")
                .put("ad_detected", JSONObject.NULL).put("error", errorJson(failure))
            event("frame_capture_error", JSONObject().put("slot", index).put("error", errorJson(failure)))
        } finally {
            badge?.takeIf { it !== full }?.recycle(); full?.recycle()
        }
        saveRecord(record); trimRecords(); writeArtifacts(); processNext()
        scheduleNext(width, height)
    }
    private fun scheduleNext(width: Int, height: Int) {
        if (samplingEnded || finishing) return
        // Avoid bursts of old captures after a slow operation.
        slot = maxOf(slot, ((SystemClock.elapsedRealtime() - startedAt) / CaptureSequence.INTERVAL_MS + 1).toInt())
        val delay = (startedAt + CaptureSequence.scheduledMs(slot) - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        worker.postDelayed({ tick(width, height) }, delay)
    }
    private fun trimRecords() {
        while (records.size > CaptureSequence.COUNT) {
            val oldest = records.firstOrNull { it.optString("ocr_status") != "pending" } ?: return
            records.remove(oldest)
            val number = oldest.getInt("slot").toString().padStart(3, '0')
            listOf("frame-$number.png", "badge-$number.png", "frame-$number.json").forEach { File(directory, it).delete() }
        }
    }
    private fun processNext() {
        if (ocrBusy || finishing) return
        val record = queue.pollFirst() ?: run { if (samplingEnded) finish(); return }
        val recognizer = ocr
        if (recognizer == null) {
            ocrError(record, ocrInitializationError ?: IllegalStateException("OCR unavailable"))
            processNext(); return
        }
        var bitmap: Bitmap? = null
        try {
            bitmap = requireNotNull(BitmapFactory.decodeFile(File(directory, record.getString("frame")).absolutePath))
            val input = bitmap
            val began = SystemClock.elapsedRealtime()
            ocrBusy = true
            recognizer.process(input, executor) { text, failure ->
                input.recycle()
                ocrBusy = false
                if (finishing) return@process
                if (text == null || failure != null) ocrError(record, failure ?: IllegalStateException("No OCR result"))
                else {
                    totalTagged++
                    val width = record.getInt("width"); val height = record.getInt("height")
                    val badgeText = text.lines.filter { (it.left + it.right) / 2 >= width * 3 / 4 && (it.top + it.bottom) / 2 < height / 5 }
                        .sortedWith(compareBy<LocalFrameOcr.Line> { it.top }.thenBy { it.left }).joinToString("\n") { it.text }
                    val tags = FrameTags.classify(text.text, badgeText, record.getDouble("dark_pixel_ratio"))
                    val lines = JSONArray()
                    text.lines.forEach { line -> lines.put(JSONObject().put("text", line.text)
                        .put("left", line.left).put("top", line.top).put("right", line.right).put("bottom", line.bottom)) }
                    record.put("ocr_status", "complete").put("ocr_elapsed_ms", SystemClock.elapsedRealtime() - began)
                        .put("raw_text", text.text).put("text_lines", lines).put("badge_text", badgeText)
                        .put("screen_tag", tags.screen).put("ad_detected", tags.adDetected)
                        .put("remaining_seconds", tags.remainingSeconds ?: JSONObject.NULL).put("tag_reason", tags.reason)
                        .put("countdown_raw", tags.countdownRaw ?: JSONObject.NULL)
                        .put("countdown_normalized", tags.countdownNormalized ?: JSONObject.NULL)
                        .put("tag_method", "bundled_mlkit_latin+rules_v2")
                    event("frame_tagged", JSONObject().put("slot", record.getInt("slot"))
                        .put("screen_tag", tags.screen).put("remaining_seconds", tags.remainingSeconds ?: JSONObject.NULL))
                    val fresh = !record.optBoolean("source_stale") &&
                        SystemClock.elapsedRealtime() - startedAt - record.getLong("captured_elapsed_ms") <= 6000L
                    record.put("guard_observation_fresh", fresh)
                    saveRecord(record)
                    if (!samplingEnded) guard.observe(tags.adDetected, fresh)
                }
                status.value = status.value.copy(tagged = taggedCount(), message = "Monitoring every 3 seconds · $totalCaptured captured · $totalTagged tagged")
                writeArtifacts(); processNext()
            }
        } catch (failure: Exception) {
            bitmap?.recycle(); ocrBusy = false; ocrError(record, failure); processNext()
        }
    }
    private fun ocrError(record: JSONObject, failure: Exception) {
        if (!samplingEnded) guard.observe(null, false)
        record.put("ocr_status", "error").put("screen_tag", "ocr_error").put("ad_detected", JSONObject.NULL)
            .put("remaining_seconds", JSONObject.NULL).put("error", errorJson(failure))
        saveRecord(record)
        event("frame_ocr_error", JSONObject().put("slot", record.getInt("slot")).put("error", errorJson(failure)))
    }
    private fun endSampling(message: String) {
        if (samplingEnded || finishing) return
        samplingEnded = true; resultMessage = message
        guard.close(message)
        releaseProjection()
        event("sampling_stopped", JSONObject().put("reason", message).put("screenshots", capturedCount()))
        writeArtifacts()
        if (!ocrBusy && queue.isEmpty()) finish()
        else {
            status.value = status.value.copy(message = "Capture ended · finishing tags (${taggedCount()}/${capturedCount()})")
            worker.postDelayed({
                if (!finishing) {
                    records.filter { it.optString("ocr_status") == "pending" }.forEach {
                        ocrError(it, IllegalStateException("OCR did not finish within 30 seconds after capture ended"))
                    }
                    resultMessage += " · OCR deadline reached"
                    finish()
                }
            }, 30000L)
        }
    }
    private fun fail(message: String, failure: Exception) {
        diagnostic = errorJson(failure); event("session_error", diagnostic!!)
        endSampling(message)
    }
    private fun finish() {
        if (finishing) return
        finishing = true
        val count = capturedCount()
        if (taggedCount() != count) resultMessage += " · OCR incomplete"
        if (storageFailure != null) resultMessage += " · Log storage error"
        event("session_finished", JSONObject().put("result", resultMessage).put("screenshots", count).put("tagged", taggedCount()))
        runCatching { writeArtifacts() }
        status.value = status.value.copy(active = false, frames = count, tagged = taggedCount(),
            message = "$resultMessage · $count screenshots · ${taggedCount()} tagged")
        stopSelf()
    }
    private fun releaseProjection() {
        releasingProjection = true
        runCatching { latest?.close() }; latest = null
        runCatching { display?.release() }; display = null
        runCatching { reader?.close() }; reader = null
        runCatching { projection?.stop() }; projection = null
    }
    private fun capturedCount() = records.count { it.optString("capture_status") == "saved" }
    private fun taggedCount() = records.count { it.optString("ocr_status") == "complete" }
    private fun saveRecord(record: JSONObject) {
        directory?.let { folder -> runCatching {
            atomic(File(folder, "frame-${record.getInt("slot").toString().padStart(3, '0')}.json"), record.toString(2))
        }.onFailure { failure ->
            if (failure is Exception) { storageFailure = failure; diagnostic = errorJson(failure) }
        } }
    }
    private fun writeArtifacts() {
        try { writeArtifactsUnchecked() }
        catch (failure: Exception) { storageFailure = failure; diagnostic = errorJson(failure) }
    }
    private fun writeArtifactsUnchecked() {
        val folder = directory ?: return
        atomic(File(folder, "report.json"), JSONObject().put("capture_method", "MediaProjection")
            .put("ocr_engine", "bundled_mlkit_latin").put("mode", "continuous").put("implementation", "continuous_guard_v1").put("retained_frame_limit", CaptureSequence.COUNT)
            .put("total_captured_frames", totalCaptured).put("total_tagged_frames", totalTagged)
            .put("captured_frames", capturedCount()).put("tagged_frames", taggedCount())
            .put("sample_interval_ms", CaptureSequence.INTERVAL_MS).put("capture_duration_ms", if (startedAt == 0L) 0L else SystemClock.elapsedRealtime() - startedAt)
            .put("started_epoch_ms", startedEpoch).put("result", resultMessage).put("error", diagnostic)
            .put("frames", JSONArray(records)).toString(2))
        atomic(File(folder, "timeline.jsonl"), records.joinToString("\n", postfix = if (records.isEmpty()) "" else "\n") { it.toString() })
        val cards = records.joinToString("\n") { record ->
            val frame = record.optString("frame")
            val screenshot = if (frame.isEmpty()) "<p>Capture failed</p>" else "<a href=\"$frame\"><img src=\"$frame\" loading=\"lazy\"></a>"
            "<article><h2>#${record.getInt("slot")} · ${record.optLong("captured_elapsed_ms") / 1000.0}s · ${html(record.optString("screen_tag", "tagging…"))}</h2>$screenshot<p>Ad: ${record.opt("ad_detected") ?: "pending"} · Remaining: ${record.opt("remaining_seconds") ?: "unknown"}</p><details><summary>Recognized text</summary><pre>${html(record.optString("raw_text"))}</pre></details><a href=\"frame-${record.getInt("slot").toString().padStart(3, '0')}.json\">Frame log</a></article>"
        }
        atomic(File(folder, "index.html"), "<!doctype html><meta charset=utf-8><title>TV capture timeline</title><style>body{background:#111824;color:#e7eef8;font:15px system-ui;margin:24px}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(340px,1fr));gap:20px}article{background:#202c3c;padding:14px;border-radius:12px}img{width:100%}h2{font-size:16px}a{color:#aad4ff}pre{white-space:pre-wrap}</style><h1>TV capture timeline</h1><p>${capturedCount()} retained screenshots · ${taggedCount()} tagged · ${html(resultMessage)}</p><main>$cards</main>")
    }
    private fun atomic(file: File, text: String) {
        val target = AtomicFile(file)
        val stream = target.startWrite()
        try { stream.write(text.toByteArray(Charsets.UTF_8)); target.finishWrite(stream) }
        catch (failure: Exception) { target.failWrite(stream); throw failure }
    }
    private fun event(name: String, detail: JSONObject = JSONObject()) {
        directory?.let { folder -> runCatching {
            val events = File(folder, "events.jsonl")
            if (events.length() > 2_000_000L) {
                val previous = File(folder, "events-previous.jsonl")
                previous.delete()
                check(events.renameTo(previous))
            }
            events.appendText(JSONObject().put("event", name)
                .put("epoch_ms", System.currentTimeMillis()).put("elapsed_ms", if (startedAt == 0L) JSONObject.NULL else SystemClock.elapsedRealtime() - startedAt)
                .put("detail", detail).toString() + "\n")
        } }
    }
    private fun screenStats(bitmap: Bitmap): Pair<Double, String> {
        var dark = 0
        val samples = ByteArray(32 * 18 * 3)
        var index = 0
        for (y in 0 until 18) for (x in 0 until 32) {
            val pixel = bitmap.getPixel(x * bitmap.width / 32, y * bitmap.height / 18)
            val r = (pixel shr 16) and 255; val g = (pixel shr 8) and 255; val b = pixel and 255
            if (maxOf(r, g, b) <= 16) dark++
            samples[index++] = r.toByte(); samples[index++] = g.toByte(); samples[index++] = b.toByte()
        }
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(samples).joinToString("") { "%02x".format(it) }
        return dark / (32.0 * 18) to hash
    }
    private fun html(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun errorJson(failure: Exception) = JSONObject().put("type", failure.javaClass.simpleName).put("message", failure.message)
    override fun onDestroy() {
        guard.close("Capture service stopped")
        worker.post {
            if (!finishing) {
                resultMessage = "Capture service stopped"
                records.filter { it.optString("ocr_status") == "pending" }.forEach { ocrError(it, IllegalStateException("Service stopped before OCR completed")) }
                runCatching { writeArtifacts() }
            }
            finishing = true; samplingEnded = true
            releaseProjection(); runCatching { ocr?.close() }
            status.value = status.value.copy(active = false)
            thread.quitSafely()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object {
        val status = MutableStateFlow(State())
        const val DATA = "projection_data"
        const val RESULT = "projection_result"
        const val STOP = "org.dumbtv.launcher.STOP_AD_CAPTURE"
        private const val CHANNEL = "ad_capture_probe"
    }
}
