package com.batteryforensics.charts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineSpec
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.shader.color
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.common.shader.DynamicShader

/**
 * Forensic metric charts for battery %, temperature, and cellular signal.
 * Series prepared by [ChartSeriesBuilder]; rendered with Vico line charts.
 * Sparklines remain available via [MetricSparkline] for compact teasers.
 */
@Composable
fun ForensicMetricCharts(
    series: ChartSeries,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (series.batteryPercent.size >= 2) {
            Text("Battery %", style = MaterialTheme.typography.labelMedium)
            VicoLineChart(
                values = series.batteryPercent,
                contentDescription = "Battery percent chart",
                lineColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        if (series.temperatureC.size >= 2) {
            Text("Temperature °C", style = MaterialTheme.typography.labelMedium)
            VicoLineChart(
                values = series.temperatureC,
                contentDescription = "Temperature chart",
                lineColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        if (series.cellularRssiDbm.size >= 2) {
            Text("Cellular RSSI dBm", style = MaterialTheme.typography.labelMedium)
            VicoLineChart(
                values = series.cellularRssiDbm,
                contentDescription = "Cellular signal chart",
                lineColor = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
fun VicoLineChart(
    values: List<Float>,
    contentDescription: String,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    if (values.size < 2) return
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values) {
        modelProducer.runTransaction {
            lineSeries { series(values) }
        }
    }
    val lineSpec = rememberLineSpec(shader = DynamicShader.color(lineColor))
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(lines = listOf(lineSpec)),
            startAxis = rememberStartAxis(),
            bottomAxis = rememberBottomAxis(),
        ),
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(148.dp)
            .semantics { this.contentDescription = contentDescription },
    )
}
