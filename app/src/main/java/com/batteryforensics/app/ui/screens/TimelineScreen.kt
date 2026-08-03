package com.batteryforensics.app.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import com.batteryforensics.app.ui.components.EmptyInvestigationHint
import com.batteryforensics.app.ui.components.ForensicScreen
import com.batteryforensics.app.ui.components.SectionHeader
import com.batteryforensics.app.ui.components.TimelineEventRow
import com.batteryforensics.app.ui.viewmodel.TimelineViewModel

@Composable
fun TimelineScreen(viewModel: TimelineViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ForensicScreen(
        title = "Timeline",
        subtitle = "Flight-recorder replay of meaningful events — not every sample.",
    ) {
        SectionHeader("Replay mode", "All transitions or overnight standby window.")
        FilterChip(
            selected = state.mode == "all",
            onClick = viewModel::showAll,
            label = { Text("All events") },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Show all timeline events" },
        )
        Spacer(Modifier.height(8.dp))
        FilterChip(
            selected = state.mode == "overnight",
            onClick = viewModel::showOvernight,
            label = { Text("Overnight replay") },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Show overnight replay" },
        )
        TextButton(
            onClick = viewModel::refresh,
            modifier = Modifier.semantics { contentDescription = "Rebuild timeline" },
        ) { Text("Rebuild") }
        Spacer(Modifier.height(12.dp))
        if (state.message != null && state.events.isEmpty()) {
            EmptyInvestigationHint(state.message!!)
            return@ForensicScreen
        }
        val list = if (state.mode == "overnight") state.overnight else state.events
        if (list.isEmpty()) {
            EmptyInvestigationHint("No meaningful transitions in this window yet.")
        } else {
            SectionHeader(
                if (state.mode == "overnight") "Overnight log" else "Event log",
                "${list.size} meaningful events",
            )
            list.forEach { event ->
                TimelineEventRow(event)
            }
        }
    }
}
