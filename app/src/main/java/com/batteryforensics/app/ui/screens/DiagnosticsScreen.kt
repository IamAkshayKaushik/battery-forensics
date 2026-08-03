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
import com.batteryforensics.app.ui.viewmodel.DiagnosticsViewModel
import com.batteryforensics.app.ui.viewmodel.DifferentialViewModel

@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel = hiltViewModel(),
    differentialViewModel: DifferentialViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val diff by differentialViewModel.uiState.collectAsStateWithLifecycle()
    ForensicScreen(
        title = "Root Causes",
        subtitle = "Ranked hypotheses with evidence. Confidence levels are labeled — never mixed.",
    ) {
        TextButton(onClick = viewModel::investigate) { Text("Run investigation") }
        TextButton(onClick = differentialViewModel::compareNights) { Text("Compare healthy vs problem night") }
        Spacer(Modifier.height(12.dp))
        diff.report?.let { report ->
            Text("Differential analysis", style = MaterialTheme.typography.titleLarge)
            Text(report.summary, style = MaterialTheme.typography.bodyMedium)
            Text(report.confidence.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
        if (state.diagnoses.isEmpty()) {
            EmptyInvestigationHint(
                state.message ?: "No rules triggered yet. Capture a longer sample window, then investigate.",
            )
        } else {
            state.diagnoses.forEach { d ->
                Text(d.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${d.probabilityPercent}% · ${d.confidence.starsLabel} · ${d.category}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(d.explanation, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                d.evidence.forEach { e ->
                    Text("• [${e.confidenceLevel}] ${e.description}: ${e.observedValue}", style = MaterialTheme.typography.bodyMedium)
                }
                if (d.counterEvidence.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Counter-evidence", style = MaterialTheme.typography.labelLarge)
                    d.counterEvidence.forEach { e ->
                        Text("• ${e.description}: ${e.observedValue}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Actions", style = MaterialTheme.typography.labelLarge)
                d.recommendedActions.forEach { Text("→ $it", style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
