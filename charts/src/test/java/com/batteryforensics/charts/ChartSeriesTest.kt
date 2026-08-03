package com.batteryforensics.charts

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChartSeriesTest {
    @Test
    fun fromSamples_buildsBatteryTempAndSignalSeries() {
        val points = listOf(
            ChartSamplePoint(0L, batteryPercent = 90f, temperatureC = 30f, cellularRssiDbm = -80f),
            ChartSamplePoint(60_000L, batteryPercent = 88f, temperatureC = 32f, cellularRssiDbm = -95f),
            ChartSamplePoint(120_000L, batteryPercent = 85f, temperatureC = 33f, cellularRssiDbm = null),
        )
        val series = ChartSeriesBuilder.fromPoints(points)
        assertThat(series.batteryPercent).containsExactly(90f, 88f, 85f).inOrder()
        assertThat(series.temperatureC).containsExactly(30f, 32f, 33f).inOrder()
        assertThat(series.cellularRssiDbm).containsExactly(-80f, -95f).inOrder()
        assertThat(series.timestampsEpochMs).hasSize(3)
    }

    @Test
    fun fromSamples_emptyYieldsEmptySeries() {
        val series = ChartSeriesBuilder.fromPoints(emptyList())
        assertThat(series.batteryPercent).isEmpty()
        assertThat(series.temperatureC).isEmpty()
    }
}
