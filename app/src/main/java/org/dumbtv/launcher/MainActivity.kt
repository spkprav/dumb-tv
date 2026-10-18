package org.dumbtv.launcher

import org.dumbtv.launcher.privacy.FirewallPreferences
import android.net.VpnService
import android.media.projection.MediaProjectionManager
import android.media.projection.MediaProjectionConfig
import org.dumbtv.launcher.ads.AdCaptureProbeService
import androidx.core.content.ContextCompat
import org.dumbtv.launcher.privacy.TrackerFirewallService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.dumbtv.launcher.dns.PrivateDns
import org.dumbtv.launcher.ui.DumbTvTheme
import org.dumbtv.launcher.ui.HomeScreen

class MainActivity : ComponentActivity() {
    private val homeModel: HomeViewModel by viewModels()
    private var dnsBusy by mutableStateOf(false)
    private var homeRequest by mutableStateOf(0)
    private var homeOverride by mutableStateOf(false)
    private var defaultHome by mutableStateOf(false)
    private val vpnApproval = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && FirewallPreferences.enabled(this)) startFirewall()
        else {
            FirewallPreferences.setEnabled(this, false)
            Toast.makeText(this, "Firewall is off. Enable it in Privacy & tracking when ready.", Toast.LENGTH_LONG).show()
        }
    }

    private fun startFirewall() {
        runCatching { ContextCompat.startForegroundService(this, Intent(this, TrackerFirewallService::class.java)) }
            .onFailure {
                TrackerFirewallService.status.value = TrackerFirewallService.State(error = "The TV could not start the local firewall. Try again in Privacy & tracking.")
            }
    }

    private fun requestFirewall(offerApproval: Boolean) {
        if (!FirewallPreferences.enabled(this) || TrackerFirewallService.status.value.let { it.active || it.starting }) return
        runCatching {
            val request = VpnService.prepare(this)
            if (request == null) startFirewall()
            else if (offerApproval) {
                FirewallPreferences.markApprovalOffered(this)
                vpnApproval.launch(request)
            }
        }.onFailure {
            TrackerFirewallService.status.value = TrackerFirewallService.State(error = "VPN approval is unavailable on this TV.")
        }
    }

    private fun toggleFirewall() {
        if (TrackerFirewallService.status.value.starting) return
        if (TrackerFirewallService.status.value.active) {
            FirewallPreferences.setEnabled(this, false)
            stopService(Intent(this, TrackerFirewallService::class.java))
        } else {
            FirewallPreferences.setEnabled(this, true)
            requestFirewall(offerApproval = true)
        }
    }

    private var captureConsentPending = false
    private val captureApproval = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        captureConsentPending = false
        val data = result.data
        if (result.resultCode != RESULT_OK || data == null) {
            AdCaptureProbeService.status.value = AdCaptureProbeService.State(message = "Screen capture was not approved.")
        } else {
            runCatching {
                ContextCompat.startForegroundService(this, Intent(this, AdCaptureProbeService::class.java)
                    .putExtra(AdCaptureProbeService.RESULT, result.resultCode).putExtra(AdCaptureProbeService.DATA, data))
                Toast.makeText(this, "Ad protection is starting. Open any streaming app.", Toast.LENGTH_LONG).show()
            }.onFailure {
                stopService(Intent(this, AdCaptureProbeService::class.java))
                AdCaptureProbeService.status.value = AdCaptureProbeService.State(message = "Could not start ad protection. Try again.")
            }
        }
    }

    private fun startAdCaptureTest() {
        if (captureConsentPending || AdCaptureProbeService.status.value.active) return
        if (!Settings.canDrawOverlays(this)) {
            runCatching {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
            }.onFailure {
                Toast.makeText(this, "Enable Display over other apps for Dumb TV in Android Settings.", Toast.LENGTH_LONG).show()
            }
            AdCaptureProbeService.status.value = AdCaptureProbeService.State(message = "Allow display over other apps, then select Start again.")
            return
        }
        runCatching {
            val manager = getSystemService(MediaProjectionManager::class.java)
            val request = if (Build.VERSION.SDK_INT >= 34)
                manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
                else manager.createScreenCaptureIntent()
            captureConsentPending = true
            captureApproval.launch(request)
        }.onFailure {
            captureConsentPending = false
            AdCaptureProbeService.status.value = AdCaptureProbeService.State(message = "Screen capture approval is unavailable on this TV.")
        }
    }

    private val homePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updateDefaultHome()
    }

    private fun updateDefaultHome() {
        val accessibility = getSystemService(AccessibilityManager::class.java)
        homeOverride = accessibility.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.let { info -> info.packageName == packageName && info.name == HomeButtonService::class.java.name } }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        defaultHome = packageManager.resolveActivity(home, 0)?.activityInfo?.packageName == packageName
    }

    private fun chooseHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (runCatching { homePicker.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME)) }.isSuccess) return
            }
        }
        if (runCatching { homePicker.launch(Intent(Settings.ACTION_HOME_SETTINGS)) }.isFailure) {
            Toast.makeText(this, "Choose Dumb TV under Settings → Default apps → Home. See README for TV setup.", Toast.LENGTH_LONG).show()
        }
    }

    private var dns by mutableStateOf(
        PrivateDns.Snapshot(
            supported = true,
            allowed = false,
            enabled = false,
            mode = null,
            hostname = null,
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dns = PrivateDns.read(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { homeModel.wallpapers.rotateWhileVisible() }
        }
        setContent {
            DumbTvTheme {
                HomeScreen(
                    viewModel = homeModel,
                    onToggleFirewall = ::toggleFirewall,
                    onAdCaptureTest = ::startAdCaptureTest,
                    defaultHome = defaultHome,
                    homeOverride = homeOverride,
                    homeRequest = homeRequest,
                    onHomeOverride = {
                        runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                            .onFailure { Toast.makeText(this, "Open TV Settings → Accessibility → Dumb TV Home button override", Toast.LENGTH_LONG).show() }
                    },
                    onChooseHome = ::chooseHome,
                    dns = dns,
                    dnsBusy = dnsBusy,
                    onToggleDns = { toggleDns() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) homeRequest++
    }

    override fun onResume() {
        super.onResume()
        dns = PrivateDns.read(this)
        updateDefaultHome()
        homeModel.onHomeResumed()
        // Offer Android consent once; subsequent launches only resume an authorized VPN.
        requestFirewall(offerApproval = !FirewallPreferences.approvalOffered(this))
    }

    private fun toggleDns() {
        if (dnsBusy) return
        lifecycleScope.launch {
            dnsBusy = true
            try {
                val result = if (dns.enabled) PrivateDns.disable(this@MainActivity) else PrivateDns.enable(this@MainActivity)
                dns = PrivateDns.read(this@MainActivity)
                val message = when (result) {
                    is PrivateDns.Result.Unreachable -> result.reason + " DNS settings were left unchanged."
                    PrivateDns.Result.Failed -> "The TV refused the DNS change."
                    PrivateDns.Result.Unsupported -> "This Android version has no Private DNS."
                    PrivateDns.Result.NeedsPermission -> "Allow DNS control once using the ADB command in Settings."
                    PrivateDns.Result.Changed -> null
                }
                if (message != null) Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                if (result == PrivateDns.Result.Changed && dns.enabled) {
                    repeat(8) {
                        delay(1_000)
                        dns = PrivateDns.read(this@MainActivity)
                        if (dns.verified) return@launch
                    }
                    Toast.makeText(this@MainActivity, "DNS is configured, but Android has not confirmed an active connection. You can turn it off in Settings.", Toast.LENGTH_LONG).show()
                }
            } finally {
                dnsBusy = false
            }
        }
    }
}
