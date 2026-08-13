package com.batteryforensics.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import com.batteryforensics.app.ui.components.ConfidenceBadge
import com.batteryforensics.app.ui.components.DiagnosisCard
import com.batteryforensics.app.ui.components.EmptyInvestigationHint
import com.batteryforensics.app.ui.components.ForensicScreen
import com.batteryforensics.app.ui.components.MetricRow
import com.batteryforensics.app.ui.components.SectionHeader
import com.batteryforensics.app.ui.components.StatusPanel
import com.batteryforensics.app.ui.permissions.rememberPermissionController
import com.batteryforensics.app.ui.viewmodel.DiagnosticsViewModel
import com.batteryforensics.app.ui.viewmodel.DifferentialViewModel
import com.batteryforensics.diagnostics.NightWindow
import com.batteryforensics.permissions.AppPermissions
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment

@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel = hiltViewModel(),
    differentialViewModel: DifferentialViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val diff by differentialViewModel.uiState.collectAsStateWithLifecycle()
    val permissions = rememberPermissionController()

    ForensicScreen(
        title = "Root Causes",
        subtitle = "Ranked hypotheses with evidence. Confidence levels are labeled — never mixed.",
    ) {
        StatusPanel(
            title = "Investigate",
            body = "Runs the rule engine on the last 12 hours of samples, then (when enabled) Shizuku dumpsys for Doze / wake locks / alarms / Wi-Fi / location / sensors.",
            primaryAction = if (state.investigating) "Working…" else "Run investigation",
            onPrimary = {
                permissions.requestMonitoringPermissions()
                if (!state.investigating) viewModel.investigate()
            },
        )
        if (state.investigating) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                Text(
                    "Collecting samples and privileged dumpsys…",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader(
            "Differential — pick nights",
            "Healthy baseline vs problem overnight (22:00–08:00 local). Picks persist on-device.",
        )
        Text("Healthy night", style = MaterialTheme.typography.titleSmall)
        NightChipRow(
            nights = diff.nights,
            selectedId = diff.healthyNightId,
            onSelect = differentialViewModel::selectHealthy,
            contentDescPrefix = "Healthy",
        )
        MetricRow("Healthy samples", diff.healthySampleCount.toString(), "MEASURED")
        Spacer(Modifier.height(8.dp))
        Text("Problem night", style = MaterialTheme.typography.titleSmall)
        NightChipRow(
            nights = diff.nights,
            selectedId = diff.problemNightId,
            onSelect = differentialViewModel::selectProblem,
            contentDescPrefix = "Problem",
        )
        MetricRow("Problem samples", diff.problemSampleCount.toString(), "MEASURED")
        TextButton(
            onClick = differentialViewModel::compareNights,
            enabled = !diff.comparing,
            modifier = Modifier.semantics { contentDescription = "Compare selected nights" },
        ) {
            Text(if (diff.comparing) "Comparing…" else "Compare selected nights")
        }

        Spacer(Modifier.height(16.dp))
        state.privileged?.let { priv ->
            StatusPanel(
                title = "Advanced dumpsys (Shizuku)",
                body = buildString {
                    append(priv.availabilityLabel)
                    if (priv.lines.isNotEmpty()) {
                        append("\n")
                        append(priv.lines.joinToString("\n"))
                    }
                    if (priv.errors.isNotEmpty()) {
                        append("\n")
                        append(priv.errors.take(3).joinToString("\n"))
                    }
                },
                statusLabel = if (priv.usedDumpsys) "USED" else "LIMITED",
                primaryAction = "Request Shizuku access",
                onPrimary = viewModel::requestShizukuPermission,
            )
            Spacer(Modifier.height(16.dp))
        }

        if (permissions.missingRuntime(AppPermissions.cellularRuntime).isNotEmpty()) {
            EmptyInvestigationHint(
                "Grant location to unlock cellular forensics — modem rules stay weak without RSSI.",
            )
            TextButton(onClick = { permissions.requestCellularPermissions() }) {
                Text("Grant cellular permissions")
            }
            Spacer(Modifier.height(12.dp))
        }

        diff.report?.let { report ->
            SectionHeader("Differential analysis", report.summary)
            ConfidenceBadge(report.confidence.name)
            Spacer(Modifier.height(8.dp))
            report.deltas.take(8).forEach { d ->
                MetricRow(d.label, "${d.healthyValue} → ${d.problemValue} (${d.deltaDisplay})", d.confidence.name)
            }
            Spacer(Modifier.height(20.dp))
        }
        diff.message?.let {
            EmptyInvestigationHint(it)
            Spacer(Modifier.height(12.dp))
        }

        if (state.sampleCount > 0) {
            MetricRow("Samples evaluated", state.sampleCount.toString(), "MEASURED")
            Spacer(Modifier.height(8.dp))
        }

        if (state.diagnoses.isEmpty()) {
            EmptyInvestigationHint(
                state.message
                    ?: "No rules triggered yet. Capture a longer sample window, start Flight Recorder overnight, then investigate.",
            )
        } else {
            SectionHeader("Ranked causes", "${state.diagnoses.size} hypotheses with evidence")
            state.diagnoses.forEach { d ->
                DiagnosisCard(diagnosis = d)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun NightChipRow(
    nights: List<NightWindow>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    contentDescPrefix: String,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        nights.take(5).forEach { night ->
            FilterChip(
                selected = night.id == selectedId,
                onClick = { onSelect(night.id) },
                label = { Text(night.label.substringBefore(" (").ifBlank { night.label }) },
                modifier = Modifier.semantics {
                    contentDescription = "$contentDescPrefix ${night.label}"
                },
            )
        }
    }
    Spacer(Modifier.size(4.dp))
}
