package com.batteryforensics.app.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batteryforensics.app.ui.components.EmptyInvestigationHint
import com.batteryforensics.app.ui.components.ForensicScreen
import com.batteryforensics.app.ui.viewmodel.LiveMonitorViewModel
import com.batteryforensics.charts.MetricSparkline

@Composable
fun LiveMonitorScreen(viewModel: LiveMonitorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ForensicScreen(
        title = "Live Monitor",
        subtitle = "Measured signals only. Derived conclusions appear under Causes.",
    ) {
        val sample = state.latest
        if (sample == null) {
            EmptyInvestigationHint("No samples yet. Capture now or keep periodic monitoring enabled.")
        } else {
            Metric("Battery", "${sample.batteryPercent ?: "—"}%")
            spark("Battery %", state.recent.mapNotNull { it.batteryPercent?.toFloat() })
            Metric("Voltage", "${sample.voltageMv ?: "—"} mV")
            Metric("Current", "${sample.currentMicroamps ?: "—"} µA")
            Metric("Temperature", "${sample.temperatureC ?: "—"} °C")
            spark("Temperature", state.recent.mapNotNull { it.temperatureC })
            Metric("Charging", "${sample.isCharging ?: "—"} (${sample.chargePlug ?: "unplugged"})")
            Metric("Screen", if (sample.screenOn == true) "on" else "off")
            Metric("Brightness", "${sample.brightnessPercent ?: "—"}%")
            Metric("Refresh", "${sample.refreshRateHz ?: "—"} Hz")
            Metric("Thermal status", "${sample.thermalStatus ?: "—"}")
            Metric("Wi-Fi", "${sample.wifiConnected} / ${sample.wifiRssiDbm ?: "—"} dBm")
            Metric("Cellular", "${sample.networkType ?: "—"} / ${sample.cellularRssiDbm ?: "—"} dBm")
            spark(
                "Cellular RSSI",
                state.recent.mapNotNull { it.cellularRssiDbm?.toFloat() },
            )
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = viewModel::captureNow) { Text("Capture sample") }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Spacer(Modifier.height(8.dp))
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text(value, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun spark(label: String, values: List<Float>) {
    if (values.size < 2) return
    Spacer(Modifier.height(4.dp))
    Text("$label trend", style = MaterialTheme.typography.labelMedium)
    MetricSparkline(values = values)
}
