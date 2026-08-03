package com.batteryforensics.charts

/**
 * Pure chart series extraction for battery % / temperature / cellular signal.
 * UI (Vico / Canvas) consumes these lists — keep logic unit-testable without Compose.
 */
data class ChartSamplePoint(
    val timestampEpochMs: Long,
    val batteryPercent: Float? = null,
    val temperatureC: Float? = null,
    val cellularRssiDbm: Float? = null,
)

data class ChartSeries(
    val timestampsEpochMs: List<Long>,
    val batteryPercent: List<Float>,
    val temperatureC: List<Float>,
    val cellularRssiDbm: List<Float>,
)

object ChartSeriesBuilder {
    fun fromPoints(points: List<ChartSamplePoint>): ChartSeries {
        if (points.isEmpty()) {
            return ChartSeries(
                timestampsEpochMs = emptyList(),
                batteryPercent = emptyList(),
                temperatureC = emptyList(),
                cellularRssiDbm = emptyList(),
            )
        }
        val ordered = points.sortedBy { it.timestampEpochMs }
        return ChartSeries(
            timestampsEpochMs = ordered.map { it.timestampEpochMs },
            batteryPercent = ordered.mapNotNull { it.batteryPercent },
            temperatureC = ordered.mapNotNull { it.temperatureC },
            cellularRssiDbm = ordered.mapNotNull { it.cellularRssiDbm },
        )
    }
}
