package org.dumbtv.launcher.dns

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

/**
 * Ad blocking through the TV's own Private DNS setting.
 * The launcher never opens a VPN. If the chosen server does not answer on
 * port 853, the setting is left untouched.
 */
object PrivateDns {
    const val HOST = "dns.adguard-dns.com"
    private const val LEGACY_HOST = "adblock.dns.mullvad.net"
    private const val MODE = "private_dns_mode"
    private const val SPECIFIER = "private_dns_specifier"
    private const val OPPORTUNISTIC = "opportunistic"
    private const val HOSTNAME = "hostname"
    private const val PREFS = "private_dns_restore"

    data class Snapshot(
        val supported: Boolean,
        val allowed: Boolean,
        val enabled: Boolean,
        val mode: String?,
        val hostname: String?,
        val verified: Boolean = false,
    )

    sealed class Result {
        data object Changed : Result()
        data object NeedsPermission : Result()
        data class Unreachable(val reason: String) : Result()
        data object Unsupported : Result()
        data object Failed : Result()
    }

    fun read(context: Context): Snapshot {
        val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        val allowed = context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
        if (!supported) {
            return Snapshot(supported = false, allowed = allowed, enabled = false, mode = null, hostname = null)
        }
        val mode = Settings.Global.getString(context.contentResolver, MODE)
        val hostname = Settings.Global.getString(context.contentResolver, SPECIFIER)
        return Snapshot(
            supported = true,
            allowed = allowed,
            enabled = mode == HOSTNAME && hostname == HOST,
            mode = mode,
            hostname = hostname,
            verified = mode == HOSTNAME && hostname == HOST && context.getSystemService(ConnectivityManager::class.java).let { network ->
                network.getLinkProperties(network.activeNetwork)?.let { it.isPrivateDnsActive && it.privateDnsServerName == HOST } == true
            },
        )
    }

    suspend fun enable(context: Context): Result = withContext(Dispatchers.IO) { enableOnWorker(context) }

    private fun enableOnWorker(context: Context): Result {
        val current = read(context)
        if (!current.supported) return Result.Unsupported
        if (!current.allowed) return Result.NeedsPermission
        if (current.enabled) return Result.Changed
        try {
            DnsTlsProbe.check(HOST)
        } catch (_: UnknownHostException) {
            return Result.Unreachable("Could not resolve $HOST. Check the TV’s internet connection.")
        } catch (_: SocketTimeoutException) {
            return Result.Unreachable("DNS connection timed out. Your router or ISP may block encrypted DNS on port 853.")
        } catch (_: SSLException) {
            return Result.Unreachable("Could not verify the DNS server’s secure connection. Check the TV’s date and time.")
        } catch (_: Exception) {
            return Result.Unreachable("Could not complete a secure DNS lookup. Check your connection and port 853 access.")
        }
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("mode", if (current.hostname == LEGACY_HOST) OPPORTUNISTIC else current.mode ?: OPPORTUNISTIC)
                .putString("hostname", current.hostname)
                .apply()
            val resolver = context.contentResolver
            if (!Settings.Global.putString(resolver, SPECIFIER, HOST)) return Result.Failed
            if (Settings.Global.getString(resolver, SPECIFIER) != HOST) {
                Settings.Global.putString(resolver, SPECIFIER, current.hostname)
                return Result.Failed
            }
            if (!Settings.Global.putString(resolver, MODE, HOSTNAME) ||
                Settings.Global.getString(resolver, MODE) != HOSTNAME) {
                Settings.Global.putString(resolver, SPECIFIER, current.hostname)
                Settings.Global.putString(resolver, MODE, current.mode)
                return Result.Failed
            }
            Result.Changed
        } catch (_: SecurityException) {
            Result.NeedsPermission
        }
    }

    fun disable(context: Context): Result {
        val current = read(context)
        if (!current.supported) return Result.Unsupported
        if (!current.allowed) return Result.NeedsPermission
        if (!current.enabled) return Result.Changed
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val mode = prefs.getString("mode", OPPORTUNISTIC) ?: OPPORTUNISTIC
            val hostname = prefs.getString("hostname", null)
            val restoreMode = if (mode == HOSTNAME && (hostname == HOST || hostname == LEGACY_HOST)) OPPORTUNISTIC else mode
            val resolver = context.contentResolver
            if (!Settings.Global.putString(resolver, MODE, restoreMode)) return Result.Failed
            if (restoreMode == HOSTNAME && !hostname.isNullOrBlank()) {
                if (!Settings.Global.putString(resolver, SPECIFIER, hostname)) {
                    Settings.Global.putString(resolver, SPECIFIER, current.hostname)
                    Settings.Global.putString(resolver, MODE, current.mode)
                    return Result.Failed
                }
            }
            if (Settings.Global.getString(resolver, MODE) != restoreMode) return Result.Failed
            Result.Changed
        } catch (_: SecurityException) {
            Result.NeedsPermission
        }
    }

}
