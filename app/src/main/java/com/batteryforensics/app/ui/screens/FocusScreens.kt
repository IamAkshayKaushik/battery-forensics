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
import com.batteryforensics.app.ui.viewmodel.ChemistryViewModel
import com.batteryforensics.app.ui.viewmodel.NetworkViewModel
import com.batteryforensics.app.ui.viewmodel.ThermalViewModel

@Composable
fun ChemistryScreen(viewModel: ChemistryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ForensicScreen(
        title = "Chemistry",
        subtitle = "Voltage sag, dynamic Ri (ΔV/ΔI), cycle & wear trends — not HEALTH_* alone.",
    ) {
        TextButton(onClick = viewModel::refresh) { Text("Recalculate") }
        Spacer(Modifier.height(8.dp))
        Text("Samples: ${state.sampleCount}", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(12.dp))
        val report = state.report
        if (report == null) {
            EmptyInvestigationHint(state.message ?: "No chemistry report yet.")
        } else {
            MetricRow("Voltage sag", report.voltageSag.sagMv?.let { "$it mV" } ?: "—", report.voltageSag.confidence.name)
            MetricRow(
                "Median Ri",
                report.medianInternalResistanceMilliohms?.let { "${"%.0f".format(it)} mΩ" } ?: "—",
                "DERIVED",
            )
            MetricRow(
                "Est. capacity",
                report.wear.estimatedCapacityMah?.let { "${"%.0f".format(it)} mAh" } ?: "—",
                report.wear.confidence.name,
            )
            MetricRow(
                "Wear slope",
                report.wear.capacitySlopeMahPerDay?.let { "${"%.2f".format(it)} mAh/day" } ?: "—",
                report.wear.confidence.name,
            )
            MetricRow(
                "Cycle estimate",
                report.wear.cycleEstimate?.let { "%.1f".format(it) } ?: "—",
                report.wear.confidence.name,
            )
            MetricRow(
                "Charge efficiency ratio",
                report.chargingEfficiency.efficiencyRatio?.let { "%.2f".format(it) } ?: "—",
                report.chargingEfficiency.confidence.name,
            )
            MetricRow("Charge counter span", report.chargeCounterSpanMah?.let { "$it mAh" } ?: "—", "MEASURED")
            Spacer(Modifier.height(12.dp))
            Text("Notes", style = MaterialTheme.typography.titleMedium)
            report.notes.forEach {
                Text("• $it", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ThermalScreen(viewModel: ThermalViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ForensicScreen(
        title = "Thermal",
        subtitle = "Heating/cooling rates, charging heat, throttling — labeled by confidence.",
    ) {
        TextButton(onClick = viewModel::refresh) { Text("Refresh") }
        Spacer(Modifier.height(8.dp))
        val report = state.report
        if (report == null) {
            EmptyInvestigationHint(state.message ?: "No thermal data yet.")
        } else {
            MetricRow("Max temp", report.maxTempC?.let { "${"%.1f".format(it)}°C" } ?: "—", report.confidence.name)
            MetricRow("Max daily", report.maxDailyTempC?.let { "${"%.1f".format(it)}°C" } ?: "—", "MEASURED")
            MetricRow("Max charging", report.maxChargingTempC?.let { "${"%.1f".format(it)}°C" } ?: "—", "MEASURED")
            MetricRow(
                "Heating rate",
                report.heatingRateCPerMinute?.let { "${"%.2f".format(it)}°C/min" } ?: "—",
                "DERIVED",
            )
            MetricRow(
                "Cooling rate",
                report.coolingRateCPerMinute?.let { "${"%.2f".format(it)}°C/min" } ?: "—",
                "DERIVED",
            )
            MetricRow("Throttling", if (report.throttlingDetected) "Detected" else "Not indicated", report.confidence.name)
            MetricRow("Peak thermal status", report.peakThermalStatus?.toString() ?: "—", "MEASURED")
            Spacer(Modifier.height(12.dp))
            Text("Events", style = MaterialTheme.typography.titleMedium)
            if (report.events.isEmpty()) {
                EmptyInvestigationHint("No thermal events in this window.")
            } else {
                report.events.forEach { e ->
                    Spacer(Modifier.height(8.dp))
                    Text(e.type, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(e.detail, style = MaterialTheme.typography.bodyMedium)
                    Text(e.confidence.name, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun NetworkScreen(viewModel: NetworkViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ForensicScreen(
        title = "Network",
        subtitle = "Signal, transitions, radio-active estimates. RRC is never claimed unless measured.",
    ) {
        TextButton(onClick = viewModel::refresh) { Text("Refresh") }
        Spacer(Modifier.height(8.dp))
        val report = state.report
        if (report == null) {
            EmptyInvestigationHint(state.message ?: "No network samples yet.")
        } else {
            MetricRow("Dominant RAT", report.dominantNetworkType ?: "—", "MEASURED")
            MetricRow(
                "Avg cellular RSSI",
                report.avgCellularRssiDbm?.let { "${"%.0f".format(it)} dBm" } ?: "—",
                "MEASURED",
            )
            MetricRow(
                "Weak cellular ratio",
                report.weakCellularRatio?.let { "${"%.0f".format(it * 100)}%" } ?: "—",
                "DERIVED",
            )
            MetricRow(
                "Avg Wi-Fi RSSI",
                report.avgWifiRssiDbm?.let { "${"%.0f".format(it)} dBm" } ?: "—",
                "MEASURED",
            )
            MetricRow(
                "Wi-Fi connected",
                report.wifiConnectedRatio?.let { "${"%.0f".format(it * 100)}%" } ?: "—",
                "MEASURED",
            )
            MetricRow("Network transitions", report.networkTransitionCount.toString(), "DERIVED")
            MetricRow("Cell-change proxy", report.cellChangeCount.toString(), "INFERRED")
            MetricRow(
                "Radio-active estimate",
                report.radioActiveMinutesEstimate?.let { "${"%.0f".format(it)} min" } ?: "—",
                "INFERRED",
            )
            Spacer(Modifier.height(12.dp))
            Text("Notes", style = MaterialTheme.typography.titleMedium)
            report.notes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
