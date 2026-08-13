package com.batteryforensics.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.mode == "all",
                onClick = viewModel::showAll,
                label = { Text("All events") },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Show all timeline events" },
            )
            FilterChip(
                selected = state.mode == "overnight",
                onClick = viewModel::showOvernight,
                label = { Text("Overnight") },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Show overnight replay" },
            )
        }
        TextButton(
            onClick = viewModel::refresh,
            modifier = Modifier.semantics { contentDescription = "Rebuild timeline" },
        ) { Text("Rebuild timeline") }
        Spacer(Modifier.height(12.dp))
        if (state.message != null && state.events.isEmpty()) {
            EmptyInvestigationHint(
                title = "Timeline quiet",
                text = state.message!!,
            )
            return@ForensicScreen
        }
        val list = if (state.mode == "overnight") state.overnight else state.events
        if (list.isEmpty()) {
            EmptyInvestigationHint(
                title = "No transitions yet",
                text = "No meaningful transitions in this window. Start Flight Recorder overnight, then rebuild.",
            )
        } else {
            SectionHeader(
                if (state.mode == "overnight") "Overnight log" else "Event log",
                "${list.size} meaningful events · severity-tinted rail",
            )
            list.forEachIndexed { index, event ->
                TimelineEventRow(event = event, isLast = index == list.lastIndex)
            }
        }
    }
}
