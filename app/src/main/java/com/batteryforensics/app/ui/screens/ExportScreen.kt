package com.batteryforensics.app.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batteryforensics.app.ui.components.EmptyInvestigationHint
import com.batteryforensics.app.ui.components.ForensicScreen
import com.batteryforensics.app.ui.viewmodel.ExportViewModel

@Composable
fun ExportScreen(viewModel: ExportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ForensicScreen(
        title = "Export",
        subtitle = "Save to app storage and share via system chooser. Nothing uploaded.",
    ) {
        TextButton(onClick = viewModel::generate) { Text("Generate, save & prepare share") }
        Spacer(Modifier.height(12.dp))
        if (state.markdownPreview.isBlank()) {
            EmptyInvestigationHint(
                "Exports: JSON, CSV, HTML, Markdown AI report, ZIP (incl. Room DB when present), SQL text snapshot. Saved under app-specific files/exports/.",
            )
        } else {
            Text("Formats generated", style = MaterialTheme.typography.titleMedium)
            Text(state.formatsSummary, style = MaterialTheme.typography.bodyMedium)
            state.savedPath?.let {
                Spacer(Modifier.height(8.dp))
                Text("Saved to", style = MaterialTheme.typography.titleMedium)
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            if (state.shareReady) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        viewModel.shareIntentOrNull()?.let { context.startActivity(it) }
                    },
                ) {
                    Text("Share via FileProvider…")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Markdown preview", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                state.markdownPreview,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            )
            Spacer(Modifier.height(12.dp))
            Text("JSON length: ${state.jsonLength} chars", style = MaterialTheme.typography.labelLarge)
        }
        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
