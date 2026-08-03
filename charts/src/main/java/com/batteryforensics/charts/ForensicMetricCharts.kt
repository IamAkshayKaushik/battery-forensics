package com.batteryforensics.charts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Forensic metric charts for battery %, temperature, and cellular signal.
 * Series are prepared by [ChartSeriesBuilder] (unit-tested). Rendering uses
 * [MetricSparkline]; Vico artifacts remain on the classpath for richer charts later.
 */
@Composable
fun ForensicMetricCharts(
    series: ChartSeries,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (series.batteryPercent.size >= 2) {
            Text("Battery %", style = MaterialTheme.typography.labelMedium)
            MetricSparkline(
                values = series.batteryPercent,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .semantics { contentDescription = "Battery percent chart" },
            )
        }
        if (series.temperatureC.size >= 2) {
            Text("Temperature °C", style = MaterialTheme.typography.labelMedium)
            MetricSparkline(
                values = series.temperatureC,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .semantics { contentDescription = "Temperature chart" },
                lineColor = MaterialTheme.colorScheme.error,
            )
        }
        if (series.cellularRssiDbm.size >= 2) {
            Text("Cellular RSSI dBm", style = MaterialTheme.typography.labelMedium)
            MetricSparkline(
                values = series.cellularRssiDbm,
                modifier = Modifier.semantics { contentDescription = "Cellular signal chart" },
                lineColor = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}
