package com.batteryforensics.database

import androidx.room.Room
import com.batteryforensics.database.entity.MonitoringSampleEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Room smoke: in-memory insert/query round-trip under Robolectric.
 * Device instrumentation remains optional; this verifies schema v3 DAO wiring.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoomSmokeTest {
    private lateinit var db: BatteryForensicsDatabase

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, BatteryForensicsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndQuery_preservesHdrAndBattery() = runBlocking {
        val dao = db.monitoringSampleDao()
        dao.insert(
            MonitoringSampleEntity(
                timestampEpochMs = 1_000L,
                batteryPercent = 88,
                voltageMv = 4000,
                currentMicroamps = -120_000,
                chargeCounterMah = 2800,
                temperatureC = 32f,
                isCharging = false,
                chargePlug = null,
                screenOn = false,
                brightnessPercent = 0,
                refreshRateHz = 60f,
                thermalStatus = 0,
                wifiConnected = true,
                wifiRssiDbm = -40,
                cellularRssiDbm = -95,
                networkType = "lte",
                hdrActive = true,
            ),
        )
        val rows = dao.samplesBetween(0L, 2_000L)
        assertThat(rows).hasSize(1)
        assertThat(rows[0].batteryPercent).isEqualTo(88)
        assertThat(rows[0].hdrActive).isTrue()
    }
}
