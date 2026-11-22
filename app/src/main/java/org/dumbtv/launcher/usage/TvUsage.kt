package org.dumbtv.launcher.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

internal object TvUsage {
    data class Snapshot(
        val hasAccess: Boolean,
        val appMillis: Map<String, Long> = emptyMap(),
        val screenOns: List<Long> = emptyList(),
    )

    internal data class State(
        val appMillis: Map<String, Long> = emptyMap(),
        val screenOns: List<Long> = emptyList(),
        val foreground: Pair<String, String?>? = null,
        val screenActive: Boolean = true,
    ) {
        fun afterRestart() = copy(foreground = null, screenActive = true)
        fun snapshot() = Snapshot(true, appMillis, screenOns)
    }

    @Synchronized
    @Suppress("DEPRECATION") // API 28 and earlier use checkOpNoThrow.
    fun read(context: Context): Snapshot {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val op = AppOpsManager.OPSTR_GET_USAGE_STATS
        val uid = Process.myUid()
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(op, uid, context.packageName)
        } else {
            appOps.checkOpNoThrow(op, uid, context.packageName)
        }
        val preferences = context.getSharedPreferences("tv_usage", Context.MODE_PRIVATE)
        if (mode != AppOpsManager.MODE_ALLOWED) {
            if (preferences.contains("checkpoint")) preferences.edit().clear().commit()
            return Snapshot(hasAccess = false)
        }

        val now = System.currentTimeMillis()
        // Usage events are delivered asynchronously by Android.
        val end = now - 1_000L
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val midnight = today.timeInMillis
        today.add(Calendar.DAY_OF_YEAR, -7)
        val earliest = today.timeInMillis
        val bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        val bootStarted = now - SystemClock.elapsedRealtime()
        val checkpoint = runCatching {
            preferences.getString("checkpoint", null)?.let(::JSONObject)
        }.getOrNull()
        val savedAt = checkpoint?.optLong("time", earliest) ?: earliest
        val sameBoot = checkpoint?.optInt("boot") == bootCount && savedAt >= bootStarted
        val start = when {
            checkpoint == null -> earliest
            savedAt >= end -> end - 1
            sameBoot -> maxOf(earliest, savedAt)
            else -> maxOf(earliest, savedAt, bootStarted)
        }
        val saved = readState(checkpoint, midnight)
        val initial = if (sameBoot && savedAt in earliest until end) saved else saved.afterRestart()
        val timeline = Timeline(midnight, start, initial)
        val manager = context.getSystemService(UsageStatsManager::class.java)
        val events = manager.queryEvents(start, end) ?: return saved.snapshot()
        val event = UsageEvents.Event()

        while (events.getNextEvent(event)) {
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> timeline.resume(event.packageName, event.className, event.timeStamp)
                UsageEvents.Event.ACTIVITY_PAUSED -> timeline.pause(event.packageName, event.className, event.timeStamp)
                UsageEvents.Event.SCREEN_INTERACTIVE -> timeline.screenOn(event.timeStamp)
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> timeline.screenOff(event.timeStamp)
                UsageEvents.Event.DEVICE_SHUTDOWN, UsageEvents.Event.DEVICE_STARTUP -> timeline.reset(event.timeStamp)
            }
        }
        val updated = timeline.checkpoint(end)
        val state = updated.copy(appMillis = updated.appMillis - context.packageName)
        val data = JSONObject()
            .put("day", midnight)
            .put("time", end)
            .put("boot", bootCount)
            .put("totals", JSONObject(state.appMillis))
            .put("wakes", JSONArray(state.screenOns))
            .put("package", state.foreground?.first)
            .put("activity", state.foreground?.second)
            .put("screen_active", state.screenActive)
        preferences.edit().putString("checkpoint", data.toString()).commit()
        return state.snapshot()
    }

    private fun readState(data: JSONObject?, midnight: Long): State {
        if (data == null) return State()
        val totals = if (data.optLong("day") == midnight) data.optJSONObject("totals") else null
        val appMillis = totals?.let { values ->
            values.keys().asSequence().associateWith { values.getLong(it) }
        }.orEmpty()
        val screenOns = data.optJSONArray("wakes")?.let { values ->
            List(values.length()) { index -> values.getLong(index) }
        }.orEmpty()
        val foreground = data.optString("package").takeIf { it.isNotEmpty() }?.let {
            it to data.optString("activity").takeIf(String::isNotEmpty)
        }
        return State(appMillis, screenOns, foreground, data.optBoolean("screen_active", true))
    }

    internal class Timeline(
        private val midnight: Long,
        start: Long = midnight,
        initial: State = State(),
    ) {
        private val totals = initial.appMillis.toMutableMap()
        private val wakes = ArrayDeque(initial.screenOns)
        private var foreground = initial.foreground
        private var screenActive = initial.screenActive
        private var since = start

        fun resume(packageName: String, activity: String?, at: Long) {
            accrue(at)
            foreground = packageName to activity
        }

        fun pause(packageName: String, activity: String?, at: Long) {
            if (foreground == (packageName to activity)) {
                accrue(at)
                foreground = null
            }
        }

        fun screenOn(at: Long) {
            accrue(at)
            screenActive = true
            if (at !in wakes) {
                wakes.addFirst(at)
                if (wakes.size > 3) wakes.removeLast()
            }
        }

        fun screenOff(at: Long) {
            accrue(at)
            screenActive = false
        }

        fun reset(at: Long) {
            accrue(at)
            foreground = null
            screenActive = true
        }

        fun checkpoint(at: Long): State {
            accrue(at)
            return State(totals.toMap(), wakes.toList(), foreground, screenActive)
        }

        private fun accrue(at: Long) {
            val active = foreground
            if (screenActive && active != null) {
                val elapsed = at - maxOf(midnight, since)
                if (elapsed > 0) totals.merge(active.first, elapsed, Long::plus)
            }
            since = at
        }
    }
}
