package org.dumbtv.launcher.privacy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat

/** Never presents consent UI or overrides an explicit turn-off from the background. */
class FirewallBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        if (!FirewallPreferences.enabled(context)) return
        runCatching {
            if (VpnService.prepare(context) == null) {
                ContextCompat.startForegroundService(context, Intent(context, TrackerFirewallService::class.java))
            }
        }.onFailure {
            TrackerFirewallService.status.value = TrackerFirewallService.State(
                error = "Automatic start was unavailable. Open Dumb TV to resume protection.")
        }
    }
}
