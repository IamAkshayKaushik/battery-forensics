package com.batteryforensics.app.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
        TextButton(
            onClick = viewModel::refresh,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Recalculate chemistry" },
        ) { Text("Recalculate") }
        Spacer(Modifier.height(8.dp))
        MetricChipRow(listOf("Samples" to state.sampleCount.toString()))
        Spacer(Modifier.height(12.dp))
        val report = state.report
        if (report == null) {
            EmptyInvestigationHint(
                title = "Chemistry file thin",
                text = state.message
                    ?: "No chemistry report yet. Capture samples across charge cycles for wear depth.",
            )
        } else {
            StatusPanel(
                title = "Wear horizon",
                body = when {
                    state.sampleCount < 48 ->
                        "Short window — long-term wear needs multi-day charge-counter history. Current slope is provisional."
                    report.wear.capacitySlopeMahPerDay == null ->
                        "Wear slope unavailable — need repeated full-charge charge-counter peaks over days/weeks."
                    else ->
                        "Capacity trend from charge-counter peaks. Still Derived — not a lab capacity test."
                },
                statusLabel = report.wear.confidence.name,
            )
            Spacer(Modifier.height(12.dp))
            SectionHeader("Derived chemistry")
            MetricRow(
                "Voltage sag",
                report.voltageSag.sagMv?.let { "$it mV" } ?: "—",
                report.voltageSag.confidence.name,
            )
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
            MetricRow(
                "Charge counter span",
                report.chargeCounterSpanMah?.let { "$it mAh" } ?: "—",
                "MEASURED",
            )
            MetricRow("Ri sample pairs", report.resistanceSamples.size.toString(), "DERIVED")
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
        TextButton(
            onClick = viewModel::refresh,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Refresh thermal analysis" },
        ) { Text("Refresh") }
        Spacer(Modifier.height(8.dp))
        val report = state.report
        if (report == null) {
            EmptyInvestigationHint(
                title = "Thermal quiet",
                text = state.message ?: "No thermal data yet. Capture while charging or under load.",
            )
        } else {
            MetricChipRow(
                buildList {
                    report.maxTempC?.let { add("Max" to "${"%.1f".format(it)}°C") }
                    report.heatingRateCPerMinute?.let { add("Heat" to "${"%.2f".format(it)}°C/min") }
                    add("Throttle" to if (report.throttlingDetected) "Yes" else "No")
                },
            )
            MetricRow(
                "Max temp",
                report.maxTempC?.let { "${"%.1f".format(it)}°C" } ?: "—",
                report.confidence.name,
            )
            MetricRow(
                "Max daily",
                report.maxDailyTempC?.let { "${"%.1f".format(it)}°C" } ?: "—",
                "MEASURED",
            )
            MetricRow(
                "Max charging",
                report.maxChargingTempC?.let { "${"%.1f".format(it)}°C" } ?: "—",
                "MEASURED",
            )
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
            MetricRow(
                "Throttling",
                if (report.throttlingDetected) "Detected" else "Not indicated",
                report.confidence.name,
            )
            MetricRow("Peak thermal status", report.peakThermalStatus?.toString() ?: "—", "MEASURED")
            Spacer(Modifier.height(12.dp))
            SectionHeader("Events")
            if (report.events.isEmpty()) {
                EmptyInvestigationHint(
                    title = "No thermal events",
                    text = "No thermal events in this window. Heat spikes appear here when ΔT/Δt crosses thresholds.",
                )
            } else {
                report.events.forEach { e ->
                    StatusPanel(
                        title = e.type,
                        body = e.detail,
                        statusLabel = e.confidence.name,
                    )
                    Spacer(Modifier.height(8.dp))
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
        TextButton(
            onClick = viewModel::refresh,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Refresh network forensics" },
        ) { Text("Refresh") }
        Spacer(Modifier.height(8.dp))
        val report = state.report
        if (report == null) {
            EmptyInvestigationHint(
                title = "Radio evidence locked",
                text = state.message ?: "No network samples yet. Grant location + phone state, then capture.",
            )
        } else {
            StatusPanel(
                title = "Honesty note",
                body = "Radio-active time is Inferred from weak signal / cell preference — not Measured RRC or modem HAL energy.",
                statusLabel = "INFERRED",
            )
            Spacer(Modifier.height(8.dp))
            MetricChipRow(
                buildList {
                    report.dominantNetworkType?.let { add("RAT" to it) }
                    report.avgCellularRssiDbm?.let { add("RSSI" to "${"%.0f".format(it)} dBm") }
                    report.radioActiveMinutesEstimate?.let { add("Radio≈" to "${"%.0f".format(it)} min") }
                },
            )
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
