package org.dumbtv.launcher.privacy

import android.content.Context

/** Desired state is separate from the live tunnel status. */
object FirewallPreferences {
    private fun store(context: Context) = context.getSharedPreferences("tracker_firewall", Context.MODE_PRIVATE)
    fun enabled(context: Context) = store(context).getBoolean("enabled", true)
    fun setEnabled(context: Context, enabled: Boolean) {
        store(context).edit().putBoolean("enabled", enabled).apply()
    }
    fun approvalOffered(context: Context) = store(context).getBoolean("approval_offered", false)
    fun markApprovalOffered(context: Context) {
        store(context).edit().putBoolean("approval_offered", true).apply()
    }
}
