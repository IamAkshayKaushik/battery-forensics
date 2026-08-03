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
import com.batteryforensics.app.ui.components.EvidenceBlock
import com.batteryforensics.app.ui.components.ForensicScreen
import com.batteryforensics.app.ui.components.MetricRow
import com.batteryforensics.app.ui.components.SectionHeader
import com.batteryforensics.app.ui.components.StatusPanel
import com.batteryforensics.app.ui.permissions.rememberPermissionController
import com.batteryforensics.app.ui.viewmodel.DiagnosticsViewModel
import com.batteryforensics.app.ui.viewmodel.DifferentialViewModel
import com.batteryforensics.permissions.AppPermissions

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
            body = "Runs the rule engine on the last 12 hours of samples, then (when enabled) Shizuku dumpsys for Doze / wake locks / alarms.",
            primaryAction = if (state.investigating) "Working…" else "Run investigation",
            onPrimary = {
                permissions.requestMonitoringPermissions()
                if (!state.investigating) viewModel.investigate()
            },
            secondaryAction = "Compare nights",
            onSecondary = differentialViewModel::compareNights,
        )

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
            SectionHeader("Differential analysis")
            Text(report.summary, style = MaterialTheme.typography.bodyMedium)
            Text(
                report.confidence.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
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
                EvidenceBlock(
                    title = d.title,
                    subtitle = "${d.probabilityPercent}% · ${d.confidence.starsLabel} · ${d.category}",
                    lines = buildList {
                        add(d.explanation)
                        d.evidence.forEach { e ->
                            add("• [${e.confidenceLevel}] ${e.description}: ${e.observedValue}")
                        }
                        if (d.counterEvidence.isNotEmpty()) {
                            add("Counter-evidence:")
                            d.counterEvidence.forEach { e ->
                                add("• ${e.description}: ${e.observedValue}")
                            }
                        }
                        add("Actions:")
                        d.recommendedActions.forEach { add("→ $it") }
                    },
                )
            }
        }
    }
}
