package com.batteryforensics.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.batteryforensics.core.model.MetricKind

data class TelephonyReading(
    val cellularRssiDbm: Int?,
    val networkType: String?,
    val kind: MetricKind = MetricKind.Measured,
)

/**
 * Collects network type always (when phone permission allows) and cellular RSSI
 * from [TelephonyManager.allCellInfo] when location permission is granted (API 28+).
 */
class TelephonyMetricsCollector(
    private val context: Context,
) {
    fun read(): TelephonyReading {
        val hasPhone = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE,
        ) == PackageManager.PERMISSION_GRANTED
        val hasLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

        if (!hasPhone && !hasLocation) {
            return TelephonyReading(cellularRssiDbm = null, networkType = null)
        }

        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val networkType = if (hasPhone || hasLocation) {
            runCatching { mapNetworkType(telephony) }.getOrDefault("unknown")
        } else {
            null
        }

        val rssi = if (hasLocation) {
            runCatching { readRssiFromCellInfo(telephony) }.getOrNull()
        } else {
            null
        }

        return TelephonyReading(
            cellularRssiDbm = rssi,
            networkType = networkType,
            kind = if (rssi != null) MetricKind.Measured else MetricKind.Measured,
        )
    }

    @Suppress("DEPRECATION")
    private fun mapNetworkType(telephony: TelephonyManager): String {
        val type = telephony.dataNetworkType
        return when (type) {
            TelephonyManager.NETWORK_TYPE_NR -> "5g"
            TelephonyManager.NETWORK_TYPE_LTE -> "lte"
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_UMTS,
            -> "3g"
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_GPRS,
            -> "2g"
            TelephonyManager.NETWORK_TYPE_UNKNOWN -> "unknown"
            else -> "other"
        }
    }

    @Suppress("DEPRECATION")
    private fun readRssiFromCellInfo(telephony: TelephonyManager): Int? {
        val cells: List<CellInfo> = telephony.allCellInfo ?: return null
        val registered = cells.filter { it.isRegistered }
        val candidates = if (registered.isNotEmpty()) registered else cells
        return candidates.mapNotNull { cellDbm(it) }.minOrNull()
    }

    @Suppress("DEPRECATION")
    private fun cellDbm(info: CellInfo): Int? = when (info) {
        is CellInfoLte -> info.cellSignalStrength.dbm.takeIf { it != Int.MAX_VALUE && it < 0 }
        is CellInfoGsm -> info.cellSignalStrength.dbm.takeIf { it != Int.MAX_VALUE && it < 0 }
        is CellInfoWcdma -> info.cellSignalStrength.dbm.takeIf { it != Int.MAX_VALUE && it < 0 }
        is CellInfoCdma -> info.cellSignalStrength.dbm.takeIf { it != Int.MAX_VALUE && it < 0 }
        else -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is CellInfoNr) {
                info.cellSignalStrength.dbm.takeIf { it != Int.MAX_VALUE && it < 0 }
            } else {
                null
            }
        }
    }
}
