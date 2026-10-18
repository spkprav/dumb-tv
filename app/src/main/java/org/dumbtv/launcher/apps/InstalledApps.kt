package org.dumbtv.launcher.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

class LaunchableApp(
    val label: String,
    val packageName: String,
    val activityName: String,
    val banner: Drawable?,
    val icon: Drawable,
    val tvApp: Boolean,
)

object InstalledApps {
    fun load(context: Context): List<LaunchableApp> {
        val manager = context.packageManager
        val found = LinkedHashMap<String, LaunchableApp>()
        collect(manager, found, leanback = true)
        collect(manager, found, leanback = false)
        return found.values
            .filter { it.packageName != context.packageName }
            .sortedWith(compareByDescending<LaunchableApp> { it.tvApp }.thenBy { it.label.lowercase() })
    }

    private fun collect(manager: PackageManager, into: LinkedHashMap<String, LaunchableApp>, leanback: Boolean) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(
            if (leanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER,
        )
        val resolved = manager.queryIntentActivities(intent, 0)
        for (info in resolved) {
            val activity = info.activityInfo ?: continue
            val packageName = activity.packageName ?: continue
            if (packageName in into) continue
            val appInfo = activity.applicationInfo
            val icon = activity.loadIcon(manager) ?: appInfo.loadIcon(manager) ?: continue
            val label = activity.loadLabel(manager)?.toString()?.takeIf { it.isNotBlank() }
                ?: appInfo.loadLabel(manager)?.toString()
                ?: packageName
            into[packageName] = LaunchableApp(
                label = label,
                packageName = packageName,
                activityName = activity.name,
                banner = null,
                icon = icon,
                tvApp = leanback,
            )
        }
    }
}
