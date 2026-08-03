package com.batteryforensics.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
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
import com.batteryforensics.app.ui.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onOpenChemistry: () -> Unit,
    onOpenThermal: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenExport: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ForensicScreen(
        title = "Battery Forensics",
        subtitle = "Don't guess. Investigate. Evidence-first diagnostics stay on this device.",
    ) {
        Text("Investigator overview", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        MetricRow("Samples captured", state.sampleCount.toString(), "MEASURED")
        state.latestBatteryPercent?.let {
            MetricRow("Latest battery", "$it%", "MEASURED")
        }
        state.latestTempC?.let {
            MetricRow("Latest temperature", "${"%.1f".format(it)}°C", "MEASURED")
        }
        state.latestNetwork?.let {
            MetricRow("Network", it, "MEASURED")
        }
        Spacer(Modifier.height(12.dp))
        EmptyInvestigationHint(
            "This is not a percentage widget. Live samples feed chemistry, thermal, network, and root-cause engines with explicit confidence labels.",
        )
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text("Focus areas", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        FocusLink("Chemistry", "Ri, voltage sag, wear, charge efficiency") { onOpenChemistry() }
        FocusLink("Thermal", "ΔT/Δt, charging heat, throttling") { onOpenThermal() }
        FocusLink("Network", "Signal, transitions, inferred radio-active time") { onOpenNetwork() }
        FocusLink("Export", "JSON / CSV / HTML / ZIP / Markdown AI report") { onOpenExport() }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = viewModel::captureNow, modifier = Modifier.fillMaxWidth()) {
            Text("Capture sample now")
        }
    }
}

@Composable
private fun FocusLink(title: String, detail: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}
