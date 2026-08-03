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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batteryforensics.app.ui.components.EmptyInvestigationHint
import com.batteryforensics.app.ui.components.ForensicScreen
import com.batteryforensics.app.ui.components.SectionHeader
import com.batteryforensics.app.ui.components.StatusPanel
import com.batteryforensics.app.ui.viewmodel.ExportViewModel

@Composable
fun ExportScreen(viewModel: ExportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ForensicScreen(
        title = "Export",
        subtitle = "Save to app storage and share via system chooser. Nothing uploaded.",
    ) {
        StatusPanel(
            title = "Diagnostic bundle",
            body = "JSON, CSV, HTML, Markdown AI report, ZIP (Room DB when present), SQL text, optional .bfz (gzip of ZIP). All local — you choose where to share.",
            primaryAction = "Generate, save & prepare share",
            onPrimary = viewModel::generate,
        )
        Spacer(Modifier.height(12.dp))
        if (state.markdownPreview.isBlank()) {
            EmptyInvestigationHint(
                "Run Generate after capturing samples. Exports land under app-specific files/exports/.",
            )
        } else {
            SectionHeader("Generated")
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
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "Share export via FileProvider" },
                ) {
                    Text("Share via FileProvider…")
                }
            }
            Spacer(Modifier.height(8.dp))
            SectionHeader("Markdown preview")
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
