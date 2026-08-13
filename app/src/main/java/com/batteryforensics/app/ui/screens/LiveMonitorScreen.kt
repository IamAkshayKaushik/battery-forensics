package com.batteryforensics.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batteryforensics.app.ui.components.EmptyInvestigationHint
import com.batteryforensics.app.ui.components.ForensicScreen
import com.batteryforensics.app.ui.components.MetricChipRow
import com.batteryforensics.app.ui.components.MetricRow
import com.batteryforensics.app.ui.components.SectionHeader
import com.batteryforensics.app.ui.components.StatusPanel
import com.batteryforensics.app.ui.permissions.rememberPermissionController
import com.batteryforensics.app.ui.viewmodel.LiveMonitorViewModel
import com.batteryforensics.charts.ChartSamplePoint
import com.batteryforensics.charts.ChartSeriesBuilder
import com.batteryforensics.charts.ForensicMetricCharts
import com.batteryforensics.permissions.AppPermissions

@Composable
fun LiveMonitorScreen(viewModel: LiveMonitorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permissions = rememberPermissionController()
    val chartSeries = remember(state.recent) {
        ChartSeriesBuilder.fromPoints(
            state.recent.map {
                ChartSamplePoint(
                    timestampEpochMs = it.timestampEpochMs,
                    batteryPercent = it.batteryPercent?.toFloat(),
                    temperatureC = it.temperatureC,
                    cellularRssiDbm = it.cellularRssiDbm?.toFloat(),
                )
            },
        )
    }
    val freshness = remember(state.latest) {
        state.latest?.timestampEpochMs?.let { ts ->
            val mins = (System.currentTimeMillis() - ts) / 60_000L
            when {
                mins < 1 -> "just now"
                mins < 60 -> "${mins}m ago"
                else -> "${mins / 60}h ago"
            }
        }
    }

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
                title = "No live samples",
                text = "Capture now, enable periodic monitoring, or start Flight Recorder overnight.",
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .semantics { contentDescription = "Last sample $freshness" },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Last sample", style = MaterialTheme.typography.labelLarge)
                Text(
                    freshness ?: "—",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(12.dp))
            MetricChipRow(
                buildList {
                    add("Battery" to "${sample.batteryPercent ?: "—"}%")
                    sample.temperatureC?.let { add("Temp" to "${"%.1f".format(it)}°C") }
                    sample.currentMicroamps?.let { add("Current" to "${it / 1000} mA") }
                    sample.wifiRssiDbm?.let { add("Wi-Fi" to "$it dBm") }
                },
            )
            Spacer(Modifier.height(12.dp))

            SectionHeader("Trends", "Vico charts from recent measured samples.")
            if (state.recent.size >= 2) {
                ForensicMetricCharts(series = chartSeries)
            } else {
                EmptyInvestigationHint(
                    title = "Charts need two points",
                    text = "Capture another sample to reveal battery, temperature, and signal trends.",
                )
            }
            Spacer(Modifier.height(12.dp))

            SectionHeader("Power", "Battery chemistry inputs from the system.")
            MetricRow("Battery", "${sample.batteryPercent ?: "—"}%", "MEASURED")
            MetricRow("Voltage", "${sample.voltageMv ?: "—"} mV", "MEASURED")
            MetricRow("Current", "${sample.currentMicroamps ?: "—"} µA", "MEASURED")
            MetricRow("Charge counter", "${sample.chargeCounterMah ?: "—"} mAh", "MEASURED")
            MetricRow("Temperature", "${sample.temperatureC?.let { "%.1f".format(it) } ?: "—"} °C", "MEASURED")
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
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                permissions.requestMonitoringPermissions()
                viewModel.captureNow()
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Capture sample" },
        ) {
            Text("Capture sample")
        }
    }
}
