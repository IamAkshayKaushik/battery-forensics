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
import com.batteryforensics.app.ui.components.MetricRow
import com.batteryforensics.app.ui.components.SectionHeader
import com.batteryforensics.app.ui.components.StatusPanel
import com.batteryforensics.app.ui.permissions.rememberPermissionController
import com.batteryforensics.app.ui.viewmodel.LiveMonitorViewModel
import com.batteryforensics.charts.MetricSparkline
import com.batteryforensics.permissions.AppPermissions

@Composable
fun LiveMonitorScreen(viewModel: LiveMonitorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permissions = rememberPermissionController()

    ForensicScreen(
        title = "Live Monitor",
        subtitle = "Measured signals only. Derived conclusions appear under Causes.",
    ) {
        val missingCellular = permissions.missingRuntime(AppPermissions.cellularRuntime)
        if (missingCellular.isNotEmpty()) {
            StatusPanel(
                title = "Cellular forensics locked",
                body = "Grant location + phone state to unlock RSSI / network-type evidence. On-device only — no maps, no upload.",
                statusLabel = "NEEDS PERMISSION",
                primaryAction = "Grant now",
                onPrimary = { permissions.requestCellularPermissions() },
                secondaryAction = "App Settings",
                onSecondary = permissions.openAppSettings,
            )
            Spacer(Modifier.height(16.dp))
        }

        val sample = state.latest
        if (sample == null) {
            EmptyInvestigationHint(
                "No samples yet. Capture now, enable periodic monitoring, or start Flight Recorder overnight.",
            )
        } else {
            SectionHeader("Power", "Battery chemistry inputs from the system.")
            MetricRow("Battery", "${sample.batteryPercent ?: "—"}%", "MEASURED")
            spark("Battery %", state.recent.mapNotNull { it.batteryPercent?.toFloat() })
            MetricRow("Voltage", "${sample.voltageMv ?: "—"} mV", "MEASURED")
            MetricRow("Current", "${sample.currentMicroamps ?: "—"} µA", "MEASURED")
            MetricRow("Charge counter", "${sample.chargeCounterMah ?: "—"} mAh", "MEASURED")
            MetricRow("Temperature", "${sample.temperatureC?.let { "%.1f".format(it) } ?: "—"} °C", "MEASURED")
            spark("Temperature", state.recent.mapNotNull { it.temperatureC })
            MetricRow(
                "Charging",
                "${sample.isCharging ?: "—"} (${sample.chargePlug ?: "unplugged"})",
                "MEASURED",
            )

            SectionHeader("Display", "Screen power correlates with drain.")
            MetricRow("Screen", if (sample.screenOn == true) "on" else "off", "MEASURED")
            MetricRow("Brightness", "${sample.brightnessPercent ?: "—"}%", "MEASURED")
            MetricRow("Refresh", "${sample.refreshRateHz ?: "—"} Hz", "MEASURED")

            SectionHeader("Thermal", "Heat wastes energy and throttles workloads.")
            MetricRow("Thermal status", "${sample.thermalStatus ?: "—"}", "MEASURED")

            SectionHeader("Radio", "Wi-Fi and cellular evidence for modem drain.")
            MetricRow(
                "Wi-Fi",
                "${sample.wifiConnected} / ${sample.wifiRssiDbm ?: "—"} dBm",
                "MEASURED",
            )
            MetricRow(
                "Cellular",
                if (sample.cellularRssiDbm != null || sample.networkType != null) {
                    "${sample.networkType ?: "—"} / ${sample.cellularRssiDbm ?: "—"} dBm"
                } else {
                    "Unavailable — grant location + phone state"
                },
                if (sample.cellularRssiDbm != null) "MEASURED" else "LOCKED",
            )
            spark(
                "Cellular RSSI",
                state.recent.mapNotNull { it.cellularRssiDbm?.toFloat() },
            )
            spark(
                "Wi-Fi RSSI",
                state.recent.mapNotNull { it.wifiRssiDbm?.toFloat() },
            )
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = {
            permissions.requestMonitoringPermissions()
            viewModel.captureNow()
        }) {
            Text("Capture sample")
        }
    }
}

@Composable
private fun spark(label: String, values: List<Float>) {
    if (values.size < 2) return
    Spacer(Modifier.height(4.dp))
    Text("$label trend", style = MaterialTheme.typography.labelMedium)
    MetricSparkline(values = values)
}
