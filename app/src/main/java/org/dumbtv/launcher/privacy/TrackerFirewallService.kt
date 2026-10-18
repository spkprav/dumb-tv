package org.dumbtv.launcher.privacy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import org.dumbtv.launcher.MainActivity
import org.dumbtv.launcher.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream

/** Local per-app deny tunnel. It never forwards traffic or contacts a VPN provider. */
class TrackerFirewallService : VpnService() {
    data class State(val active: Boolean = false, val starting: Boolean = false,
        val blocked: List<String> = emptyList(), val error: String? = null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var tunnel: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            FirewallPreferences.setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!FirewallPreferences.enabled(this)) { stopSelf(); return START_NOT_STICKY }
        if (status.value.active || status.value.starting) return START_STICKY
        status.value = State(starting = true)
        try {
            val manager = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Tracker firewall", NotificationManager.IMPORTANCE_LOW))
            val stop = PendingIntent.getService(this, 1, Intent(this, TrackerFirewallService::class.java).setAction(STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startForeground(73, NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_block)
                .setContentTitle("TV tracker firewall").setContentText("Selected TV apps have no network access")
                .setOngoing(true).setContentIntent(open).addAction(0, "Turn off", stop).build())
        } catch (_: Exception) {
            status.value = State(error = "Android could not start the VPN foreground service. Check VPN support and try again.")
            stopSelf()
            return START_NOT_STICKY
        }
        scope.launch {
            try {
                val installed = TrackerTargets.all.mapNotNull { target ->
                    try {
                        val info = packageManager.getApplicationInfo(target.packageName, 0)
                        val sharing = packageManager.getPackagesForUid(info.uid)?.toList().orEmpty()
                        target.packageName.takeIf { TrackerTargets.safeToBlock(info.uid, sharing) }
                    }
                    catch (_: PackageManager.NameNotFoundException) { null }
                }.toSet()
                val targets = TrackerTargets.selectInstalled(installed)
                // An empty VPN allowed-app list means ALL apps: never establish that configuration.
                check(targets.isNotEmpty()) { "No supported tracker apps with a separate app identity were found. Nothing was blocked." }
                val builder = Builder().setSession("TV tracker firewall").setMtu(1280).setBlocking(true)
                    .addAddress("10.191.0.1", 32).addRoute("0.0.0.0", 0)
                    .addAddress("fd71:191::1", 128).addRoute("::", 0)
                for (target in targets) builder.addAllowedApplication(target.packageName)
                val established = builder.establish() ?: error("Android did not authorize the VPN. Try enabling it again.")
                if (!isActive) { established.close(); return@launch }
                tunnel = established
                status.value = State(active = true, blocked = targets.map { it.title })
                // Poll sleeps while idle and bounds shutdown time even if no further packets arrive.
                FileInputStream(established.fileDescriptor).use { input ->
                    val packet = ByteArray(32767)
                    val descriptors = arrayOf(StructPollfd().apply {
                        fd = established.fileDescriptor
                        events = OsConstants.POLLIN.toShort()
                    })
                    while (isActive) {
                        if (Os.poll(descriptors, 1000) <= 0 || !isActive) continue
                        val events = descriptors[0].revents.toInt()
                        if (events and (OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL) != 0) break
                        if (events and OsConstants.POLLIN != 0 && input.read(packet) < 0) break
                    }
                }
                if (isActive) { status.value = State(error = "The firewall stopped. Enable it again to resume blocking."); stopSelf() }
            } catch (failure: Exception) {
                if (isActive) {
                    status.value = State(error = failure.message ?: "The TV could not start the firewall.")
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        // A system disconnect or another VPN must not trigger an automatic takeover.
        FirewallPreferences.setEnabled(this, false)
        status.value = State(error = "Android disconnected the firewall. Enable it again when ready.")
        stopSelf()
        super.onRevoke()
    }
    override fun onDestroy() {
        scope.cancel()
        runCatching { tunnel?.close() }
        tunnel = null
        status.value = State(error = status.value.error)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object {
        val status = MutableStateFlow(State())
        private const val CHANNEL = "tracker_firewall"
        private const val STOP = "org.dumbtv.launcher.STOP_FIREWALL"
    }
}
