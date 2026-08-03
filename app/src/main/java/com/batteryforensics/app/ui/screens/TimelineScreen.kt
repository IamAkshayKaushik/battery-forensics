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
import com.batteryforensics.app.ui.viewmodel.TimelineViewModel

@Composable
fun TimelineScreen(viewModel: TimelineViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ForensicScreen(
        title = "Timeline",
        subtitle = "Flight-recorder replay of meaningful events — not every sample.",
    ) {
        TextButton(onClick = viewModel::refresh) { Text("Rebuild") }
        TextButton(onClick = viewModel::showAll) { Text("All events") }
        TextButton(onClick = viewModel::showOvernight) { Text("Overnight replay") }
        Spacer(Modifier.height(12.dp))
        if (state.message != null && state.events.isEmpty()) {
            EmptyInvestigationHint(state.message!!)
            return@ForensicScreen
        }
        val list = if (state.mode == "overnight") state.overnight else state.events
        if (list.isEmpty()) {
            EmptyInvestigationHint("No meaningful transitions in this window yet.")
        } else {
            list.forEach { event ->
                Text(event.title, style = MaterialTheme.typography.titleMedium)
                Text(event.detail, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${event.eventType} · ${event.severity.name}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
