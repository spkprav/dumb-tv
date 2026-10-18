package org.dumbtv.launcher

import android.app.Application
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.dumbtv.launcher.apps.InstalledApps
import org.dumbtv.launcher.apps.LaunchableApp
import org.dumbtv.launcher.settings.SettingsDestination
import org.dumbtv.launcher.settings.SettingsDestinations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    val jellyfin = org.dumbtv.launcher.jellyfin.JellyfinLibrary(app, viewModelScope)
    val wallpapers = org.dumbtv.launcher.wallpaper.Wallpapers(app)
    val apps = MutableStateFlow<List<LaunchableApp>>(emptyList())
    val loading = MutableStateFlow(true)
    private val preferences = app.getSharedPreferences("app_order", 0)
    // Per-package overrides keep defaults working for apps installed later.
    val appVisibility = MutableStateFlow<Map<String, Boolean>>(preferences.all
        .filterKeys { it.startsWith("visible:") }
        .mapKeys { it.key.removePrefix("visible:") }.mapValues { it.value == true })
    private var refreshJob: Job? = null
    private var appsDirty = true
    private val packageChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { appsDirty = true }
    }

    fun setAppVisible(app: LaunchableApp, visible: Boolean) {
        preferences.edit().putBoolean("visible:${app.packageName}", visible).apply()
        appVisibility.value = appVisibility.value + (app.packageName to visible)
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(app, packageChanges, filter, ContextCompat.RECEIVER_EXPORTED)
        refresh()
    }

    fun onHomeResumed() {
        jellyfin.refresh()
        refresh()
    }

    fun refresh() {
        if (!appsDirty || refreshJob?.isActive == true) return
        appsDirty = false
        refreshJob = viewModelScope.launch {
            loading.value = true
            try {
                val loaded = withContext(Dispatchers.IO) { InstalledApps.load(getApplication()) }
                apps.value = loaded
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                appsDirty = true // Retain the existing grid and retry on the next resume.
            } finally {
                loading.value = false
            }
        }
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(packageChanges)
        super.onCleared()
    }

    fun launch(app: LaunchableApp) {
        val context = getApplication<Application>()
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(if (app.tvApp) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName, app.activityName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure {
                val fallback = context.packageManager.getLaunchIntentForPackage(app.packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (fallback != null) runCatching { context.startActivity(fallback) }
            }
    }

    fun openMovie(movie: org.dumbtv.launcher.jellyfin.JellyfinLibrary.Movie) {
        val context = getApplication<Application>()
        val intent = context.packageManager.getLeanbackLaunchIntentForPackage("org.jellyfin.androidtv")
            ?.putExtra("ItemId", movie.id)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent == null || runCatching { context.startActivity(intent) }.isFailure) {
            android.widget.Toast.makeText(context, "Install Jellyfin for Android TV and sign in to the same server/profile to open this movie.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun openAppInfo(app: LaunchableApp) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
    }

    fun openSettings(destination: SettingsDestination) {
        SettingsDestinations.open(getApplication(), destination)
    }
}
