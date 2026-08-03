package com.batteryforensics.database.mapper

import com.batteryforensics.core.model.MonitoringSample
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SampleMapperTest {
    @Test
    fun roundTrip_preservesFields() {
        val sample = MonitoringSample(
            timestampEpochMs = 42L,
            batteryPercent = 77,
            voltageMv = 3900,
            currentMicroamps = -150000,
            chargeCounterMah = 2500,
            temperatureC = 33.5f,
            isCharging = false,
            chargePlug = null,
            screenOn = true,
            brightnessPercent = 60,
            refreshRateHz = 90f,
            thermalStatus = 1,
            wifiConnected = true,
            wifiRssiDbm = -55,
            cellularRssiDbm = -100,
            networkType = "lte",
        )
        val entity = sample.toEntity()
        val back = entity.toDomain()
        assertThat(back).isEqualTo(sample)
        assertThat(entity.id).isEqualTo(0L)
    }
}
