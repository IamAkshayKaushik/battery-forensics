package com.batteryforensics.diagnostics

import com.batteryforensics.core.model.MonitoringSample
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class NightWindowFinderTest {

    private val zone = ZoneOffset.UTC

    @Test
    fun candidateNights_returnsNewestFirstWithStableIds() {
        val noon = LocalDate.of(2026, 8, 4)
            .atTime(LocalTime.NOON)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val nights = NightWindowFinder.candidateNights(
            nowEpochMs = noon,
            lookbackDays = 3,
            zone = zone,
        )
        assertThat(nights).hasSize(3)
        assertThat(nights[0].id).isEqualTo("night-0")
        assertThat(nights[0].label).contains("Last night")
        assertThat(nights[1].id).isEqualTo("night-1")
        assertThat(nights[0].startEpochMs).isGreaterThan(nights[1].startEpochMs)
        // Last night: Aug 3 22:00 → Aug 4 08:00 UTC
        val expectedStart = LocalDate.of(2026, 8, 3).atTime(22, 0).atZone(zone).toInstant().toEpochMilli()
        val expectedEnd = LocalDate.of(2026, 8, 4).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        assertThat(nights[0].startEpochMs).isEqualTo(expectedStart)
        assertThat(nights[0].endEpochMs).isEqualTo(expectedEnd)
    }

    @Test
    fun samplesIn_filtersInclusiveStartExclusiveEnd() {
        val window = NightWindow(
            id = "night-0",
            label = "Last night",
            startEpochMs = 1000L,
            endEpochMs = 5000L,
        )
        val samples = listOf(
            sample(999),
            sample(1000),
            sample(2500),
            sample(4999),
            sample(5000),
        )
        val inside = NightWindowFinder.samplesIn(samples, window)
        assertThat(inside.map { it.timestampEpochMs }).containsExactly(1000L, 2500L, 4999L).inOrder()
    }

    @Test
    fun defaultHealthyAndProblem_picksNight1VsNight0WhenEnoughSamples() {
        val noon = LocalDate.of(2026, 8, 4)
            .atTime(LocalTime.NOON)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val nights = NightWindowFinder.candidateNights(noon, lookbackDays = 3, zone = zone)
        val samples = buildList {
            // Samples in night-1 (healthy) and night-0 (problem)
            nights[1].let { w ->
                add(sample(w.startEpochMs + 60_000, 95))
                add(sample(w.startEpochMs + 3_600_000, 93))
                add(sample(w.startEpochMs + 7_200_000, 92))
            }
            nights[0].let { w ->
                add(sample(w.startEpochMs + 60_000, 90))
                add(sample(w.startEpochMs + 3_600_000, 80))
                add(sample(w.startEpochMs + 7_200_000, 70))
            }
        }
        val pick = NightWindowFinder.defaultHealthyAndProblem(nights, samples, minSamples = 3)
        assertThat(pick).isNotNull()
        assertThat(pick!!.healthy.id).isEqualTo("night-1")
        assertThat(pick.problem.id).isEqualTo("night-0")
    }

    private fun sample(t: Long, pct: Int = 80) = MonitoringSample(
        timestampEpochMs = t,
        batteryPercent = pct,
        voltageMv = 3900,
        currentMicroamps = -100_000,
        chargeCounterMah = 2500,
        temperatureC = 30f,
        isCharging = false,
        chargePlug = null,
        screenOn = false,
        brightnessPercent = 0,
        refreshRateHz = 60f,
        thermalStatus = 0,
        wifiConnected = true,
        wifiRssiDbm = -50,
        cellularRssiDbm = -90,
        networkType = "lte",
    )
}
