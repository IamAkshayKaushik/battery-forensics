package com.batteryforensics.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.batteryforensics.core.evidence.Diagnosis
import com.batteryforensics.timeline.TimelineEvent
import com.batteryforensics.timeline.TimelineSeverity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ForensicScreen(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .semantics { contentDescription = "$title screen" },
        verticalArrangement = Arrangement.Top,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(24.dp))
        content()
    }
}

@Composable
fun EmptyInvestigationHint(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(16.dp)
            .semantics { contentDescription = "Empty state: $text" },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Nothing to investigate yet",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

@Composable
fun MetricRow(label: String, value: String, confidence: String) {
    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$label $value $confidence" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        ConfidenceBadge(confidence)
    }
}

@Composable
fun ConfidenceBadge(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetricChipRow(chips: List<Pair<String, String>>) {
    if (chips.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        chips.forEach { (label, value) ->
            Text(
                "$label · $value",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .heightIn(min = 32.dp)
                    .semantics { contentDescription = "$label $value" },
            )
        }
    }
}

/** Interactive / status container — used for actions and setup, not decoration. */
@Composable
fun StatusPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    statusLabel: String? = null,
    primaryAction: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryAction: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(16.dp)
            .semantics { contentDescription = "$title ${statusLabel.orEmpty()}" },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            statusLabel?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
        if (primaryAction != null || secondaryAction != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (primaryAction != null && onPrimary != null) {
                    TextButton(
                        onClick = onPrimary,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = primaryAction },
                    ) { Text(primaryAction) }
                }
                if (secondaryAction != null && onSecondary != null) {
                    TextButton(
                        onClick = onSecondary,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = secondaryAction },
                    ) { Text(secondaryAction) }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, detail: String? = null) {
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.titleLarge)
    detail?.let {
        Spacer(Modifier.height(4.dp))
        Text(
            it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
fun EvidenceBlock(
    title: String,
    subtitle: String,
    lines: List<String>,
    onClick: (() -> Unit)? = null,
) {
    ForensicCauseCard(
        title = title,
        subtitle = subtitle,
        lines = lines,
        onClick = onClick,
    )
}

@Composable
fun ForensicCauseCard(
    title: String,
    subtitle: String,
    lines: List<String>,
    onClick: (() -> Unit)? = null,
    initiallyExpanded: Boolean = false,
) {
    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                if (onClick != null) onClick() else expanded = !expanded
            }
            .padding(14.dp)
            .semantics {
                contentDescription = "$title $subtitle. ${if (expanded) "Expanded" else "Collapsed"}"
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            subtitle,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        AnimatedVisibility(
            visible = expanded || onClick != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val shown = if (onClick != null) lines.take(2) else lines
                shown.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
                if (onClick == null && !expanded && lines.size > 2) {
                    Text(
                        "Tap for full evidence",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (onClick == null && !expanded) {
            Text(
                lines.firstOrNull().orEmpty().take(120) + if ((lines.firstOrNull()?.length ?: 0) > 120) "…" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Tap for evidence & actions",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun DiagnosisCard(diagnosis: Diagnosis, onClick: (() -> Unit)? = null) {
    var expanded by remember(diagnosis.id) { mutableStateOf(false) }
    val chips = diagnosis.supportingMetrics.take(4).map { it.label to it.value }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                if (onClick != null) onClick() else expanded = !expanded
            }
            .padding(14.dp)
            .semantics {
                contentDescription =
                    "${diagnosis.title}, ${diagnosis.probabilityPercent} percent, ${diagnosis.confidence.starsLabel}"
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                diagnosis.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${diagnosis.probabilityPercent}%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            "${diagnosis.confidence.starsLabel} · ${diagnosis.category}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(diagnosis.explanation, style = MaterialTheme.typography.bodyMedium)
        MetricChipRow(chips)
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(Modifier.height(4.dp))
                Text("Evidence", style = MaterialTheme.typography.titleSmall)
                diagnosis.evidence.forEach { e ->
                    Text(
                        "• [${e.confidenceLevel}] ${e.description}: ${e.observedValue}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (diagnosis.counterEvidence.isNotEmpty()) {
                    Text("Counter-evidence", style = MaterialTheme.typography.titleSmall)
                    diagnosis.counterEvidence.forEach { e ->
                        Text("• ${e.description}: ${e.observedValue}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("Actions", style = MaterialTheme.typography.titleSmall)
                diagnosis.recommendedActions.forEach {
                    Text("→ $it", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (!expanded && onClick == null) {
            Text(
                "Tap for full evidence",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun TimelineEventRow(event: TimelineEvent) {
    val timeFmt = remember {
        SimpleDateFormat("MMM d · HH:mm:ss", Locale.getDefault())
    }
    val color = severityColor(event.severity)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics {
                contentDescription =
                    "${event.eventType} ${event.title} ${event.detail} ${event.severity}"
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                timeFmt.format(Date(event.timestampEpochMs)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Text(event.title, style = MaterialTheme.typography.titleMedium)
            Text(event.detail, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${event.eventType} · ${event.severity}",
                style = MaterialTheme.typography.labelLarge,
                color = color,
            )
        }
    }
}

@Composable
private fun severityColor(severity: TimelineSeverity): Color = when (severity) {
    TimelineSeverity.INFO -> MaterialTheme.colorScheme.primary
    TimelineSeverity.NOTICE -> MaterialTheme.colorScheme.tertiary
    TimelineSeverity.WARNING -> MaterialTheme.colorScheme.secondary
    TimelineSeverity.CRITICAL -> MaterialTheme.colorScheme.error
}
