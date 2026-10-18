package org.dumbtv.launcher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/** Optional, user-enabled fallback for TV firmware that overrides the default HOME role. */
class HomeButtonService : AccessibilityService() {
    private var homeActivities = emptySet<ComponentName>()
    private var lastRedirect = 0L

    override fun onServiceConnected() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        homeActivities = packageManager.queryIntentActivities(home, 0)
            .flatMap {
                val activity = it.activityInfo
                listOfNotNull(
                    ComponentName(activity.packageName, activity.name),
                    activity.targetActivity?.let { target -> ComponentName(activity.packageName, target) },
                )
            }
            .filter { it.packageName != packageName }
            .toSet()
        // Observe only other home packages; never request or inspect window contents.
        serviceInfo = serviceInfo.apply {
            packageNames = homeActivities.map { it.packageName }.distinct().toTypedArray()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val owner = event.packageName?.toString() ?: return
        val activity = event.className?.toString() ?: return
        // Only redirect a declared HOME activity, not settings or other screens in its package.
        if (ComponentName(owner, activity) in homeActivities) openHome()
    }

    private fun openHome(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRedirect < 500) return true
        return runCatching {
            startActivity(Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
            lastRedirect = now
            true
        }.getOrDefault(false)
    }

    override fun onInterrupt() = Unit
}
