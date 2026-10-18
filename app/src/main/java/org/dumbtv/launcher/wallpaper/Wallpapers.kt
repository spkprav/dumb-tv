package org.dumbtv.launcher.wallpaper

import android.content.Context
import android.os.SystemClock
import org.dumbtv.launcher.R
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class Wallpapers(context: Context) {
    private val resources = context.resources
    private var bundled: Bitmap? = null
    private var nextDueAt = 0L
    private var previousRequest = 0
    private var previousInterval = -1
    private val preferences = context.getSharedPreferences("wallpapers", Context.MODE_PRIVATE)
    private val directory = File(context.cacheDir, "wallpapers-v1")
    val minutes = MutableStateFlow(preferences.getInt("minutes", 5).takeIf { it in WallpaperCatalog.intervals } ?: 5)
    val bitmap = MutableStateFlow<Bitmap?>(null)
    val title = MutableStateFlow("Mountain lake · Built-in")
    private val nextRequest = MutableStateFlow(0)
    private val rotation = WallpaperRotation()
    private var lastId = preferences.getString("last", null)

    fun cycleInterval() {
        val choices = WallpaperCatalog.intervals
        minutes.value = choices[(choices.indexOf(minutes.value) + 1) % choices.size]
        preferences.edit().putInt("minutes", minutes.value).apply()
    }

    fun next() {
        if (minutes.value == 0) {
            minutes.value = 5
            preferences.edit().putInt("minutes", 5).apply()
        }
        nextRequest.value++
    }

    /** Retains the image and rotation deadline across app launches; no reload on every resume. */
    suspend fun rotateWhileVisible() {
        if (bundled == null) {
            bundled = withContext(Dispatchers.IO) {
                BitmapFactory.decodeResource(resources, R.drawable.lake_wallpaper,
                    BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 })
                    ?.also { it.prepareToDraw() }
            }
            if (bitmap.value == null) bitmap.value = bundled
        }
        combine(minutes, nextRequest) { interval, request -> interval to request }.collectLatest { (interval, request) ->
            val now = SystemClock.elapsedRealtime()
            if (interval == 0) {
                bitmap.value = bundled
                title.value = "Mountain lake · Built-in"
                previousInterval = interval
                previousRequest = request
                nextDueAt = 0
                return@collectLatest
            }
            if (request != previousRequest || previousInterval == 0) nextDueAt = 0
            else if (previousInterval > 0 && interval != previousInterval) nextDueAt = now + interval * 60_000L
            previousInterval = interval
            previousRequest = request
            while (true) {
                val remaining = nextDueAt - SystemClock.elapsedRealtime()
                if (remaining > 0) delay(remaining)
                var changed = false
                // Keep offline retries bounded rather than trying the entire gallery each time.
                for (attempt in 0 until 2) {
                    val entry = rotation.next(lastId)
                    val loaded = try {
                        withContext(Dispatchers.IO) { load(entry)?.also { it.prepareToDraw() } }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                    if (loaded != null) {
                        bitmap.value = loaded
                        title.value = entry.title + " · Pexels"
                        lastId = entry.id
                        preferences.edit().putString("last", lastId).apply()
                        changed = true
                        break
                    }
                }
                nextDueAt = SystemClock.elapsedRealtime() + if (changed) interval * 60_000L else 60_000L
            }
        }
    }

    private fun load(entry: WallpaperCatalog.Entry): Bitmap? {
        require(WallpaperCatalog.allowed(entry.url))
        directory.mkdirs()
        trimCache()
        val cached = File(directory, entry.id + ".jpg")
        if (cached.isFile) {
            if (cached.length() <= MAX_BYTES) decode(cached.readBytes())?.let {
                cached.setLastModified(System.currentTimeMillis())
                return it
            }
            cached.delete()
        }
        val connection = URL(entry.url).openConnection() as HttpsURLConnection
        val bytes = try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            if (connection.responseCode != 200 || connection.contentType?.substringBefore(';') != "image/jpeg") return null
            if (connection.contentLengthLong > MAX_BYTES) return null
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16_384)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_BYTES) return null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
        val image = decode(bytes) ?: return null
        val temporary = File(directory, entry.id + ".tmp")
        try {
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(cached)) temporary.delete()
            trimCache()
        } catch (_: Exception) {
            temporary.delete() // A full cache must not prevent displaying a downloaded image.
        }
        return image
    }

    private fun trimCache() {
        val approved = WallpaperCatalog.entries.mapTo(HashSet()) { it.id + ".jpg" }
        val files = directory.listFiles().orEmpty()
        files.filter { it.name !in approved }.forEach { it.delete() }
        val retained = files.filter { it.name in approved && it.isFile }.sortedBy { it.lastModified() }
        var total = retained.sumOf { it.length() }
        for (file in retained) {
            if (total <= MAX_CACHE_BYTES) break
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        if (bytes.size > MAX_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096) return null
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            var sample = 1
            while (bounds.outWidth / sample > 1920 || bounds.outHeight / sample > 1080) sample *= 2
            inSampleSize = sample
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private companion object {
        const val MAX_BYTES = 6 * 1024 * 1024
        const val MAX_CACHE_BYTES = 48L * 1024 * 1024
    }
}
