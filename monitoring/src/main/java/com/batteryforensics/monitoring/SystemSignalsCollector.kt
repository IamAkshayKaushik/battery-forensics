package com.batteryforensics.monitoring

import android.Manifest
import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.Surface
import android.view.WindowManager
import androidx.core.content.ContextCompat
import java.io.File

/** Best-effort system signals; null when permission/API unavailable (minSdk 28, version-gated). */
class SystemSignalsCollector(
    private val context: Context,
) {
    data class Reading(
        val orientation: String?,
        val cellId: String?,
        val carrierName: String?,
        val cellularBand: String?,
        val bluetoothOn: Boolean?,
        val bluetoothConnected: Boolean?,
        val locationEnabled: Boolean?,
        val nfcEnabled: Boolean?,
        val hotspotOn: Boolean?,
        val foregroundApp: String?,
        val memoryPressure: String?,
        val storageFreeBytes: Long?,
        val storageFreePercent: Float?,
    )

    fun read(): Reading {
        return Reading(
            orientation = readOrientation(),
            cellId = readCellId(),
            carrierName = readCarrier(),
            cellularBand = null, // band requires privileged/OEM APIs — left null intentionally
            bluetoothOn = readBluetoothOn(),
            bluetoothConnected = readBluetoothConnected(),
            locationEnabled = readLocationEnabled(),
            nfcEnabled = readNfc(),
            hotspotOn = readHotspot(),
            foregroundApp = readForegroundApp(),
            memoryPressure = readMemoryPressure(),
            storageFreeBytes = readStorage().first,
            storageFreePercent = readStorage().second,
        )
    }

    private fun readOrientation(): String? = runCatching {
        @Suppress("DEPRECATION")
        val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay.rotation
        when (rotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> "portrait"
            Surface.ROTATION_90, Surface.ROTATION_270 -> "landscape"
            else -> "unknown"
        }
    }.getOrNull()

    private fun readCellId(): String? {
        val hasLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasLoc) return null
        return runCatching {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            @Suppress("DEPRECATION")
            val cell = tm.allCellInfo?.firstOrNull { it.isRegistered } ?: return null
            cell.toString().substringAfter("ci=").substringBefore(" ").substringBefore(",").take(32)
                .takeIf { it.isNotBlank() && it != "null" }
        }.getOrNull()
    }

    private fun readCarrier(): String? = runCatching {
        val hasPhone = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasPhone) return null
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        tm.networkOperatorName?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun readBluetoothOn(): Boolean? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return null
        }
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: BluetoothAdapter.getDefaultAdapter()
        adapter?.isEnabled
    }.getOrNull()

    private fun readBluetoothConnected(): Boolean? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return null
        }
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: BluetoothAdapter.getDefaultAdapter()
            ?: return null
        if (!adapter.isEnabled) return false
        val headset = adapter.getProfileConnectionState(android.bluetooth.BluetoothProfile.HEADSET)
        val a2dp = adapter.getProfileConnectionState(android.bluetooth.BluetoothProfile.A2DP)
        headset == android.bluetooth.BluetoothProfile.STATE_CONNECTED ||
            a2dp == android.bluetooth.BluetoothProfile.STATE_CONNECTED
    }.getOrNull()

    private fun readLocationEnabled(): Boolean? = runCatching {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE, 0) !=
                Settings.Secure.LOCATION_MODE_OFF
        }
    }.getOrNull()

    private fun readNfc(): Boolean? = runCatching {
        NfcAdapter.getDefaultAdapter(context)?.isEnabled
    }.getOrNull()

    private fun readHotspot(): Boolean? = runCatching {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val method = wifi.javaClass.getDeclaredMethod("isWifiApEnabled")
        method.isAccessible = true
        method.invoke(wifi) as? Boolean
    }.getOrNull()

    private fun readForegroundApp(): String? = runCatching {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 60_000L
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end) ?: return null
        stats.maxByOrNull { it.lastTimeUsed }?.packageName
    }.getOrNull()

    private fun readMemoryPressure(): String? = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        when {
            info.lowMemory -> "critical"
            info.availMem.toDouble() / info.totalMem < 0.15 -> "moderate"
            else -> "low"
        }
    }.getOrNull()

    private fun readStorage(): Pair<Long?, Float?> = runCatching {
        val path: File = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val free = stat.availableBlocksLong * stat.blockSizeLong
        val total = stat.blockCountLong * stat.blockSizeLong
        val pct = if (total > 0) (free.toDouble() / total * 100.0).toFloat() else null
        free to pct
    }.getOrDefault(null to null)
}
