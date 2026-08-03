package com.batteryforensics.wifi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.batteryforensics.core.model.MetricKind

data class WifiReading(
    val connected: Boolean,
    val rssiDbm: Int?,
    val kind: MetricKind = MetricKind.Measured,
)

class WifiMetricsCollector(
    private val context: Context,
) {
    @Suppress("DEPRECATION")
    fun read(): WifiReading {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivity.activeNetwork
        val caps = network?.let { connectivity.getNetworkCapabilities(it) }
        val connected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        val canReadRssi = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_WIFI_STATE,
        ) == PackageManager.PERMISSION_GRANTED

        val rssi = if (connected && canReadRssi) {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifi.connectionInfo?.rssi?.takeIf { it != -127 && it != 0 }
        } else {
            null
        }

        return WifiReading(connected = connected, rssiDbm = rssi)
    }
}
