package com.batteryforensics.parser

import com.batteryforensics.parser.activity.ActivityParser
import com.batteryforensics.parser.alarm.AlarmParser
import com.batteryforensics.parser.batterystats.BatteryStatsParser
import com.batteryforensics.parser.cmd.CmdBatteryParser
import com.batteryforensics.parser.cmd.CmdJobSchedulerParser
import com.batteryforensics.parser.deviceidle.DeviceIdleParser
import com.batteryforensics.parser.deviceidle.DozeParser
import com.batteryforensics.parser.jobscheduler.JobSchedulerParser
import com.batteryforensics.parser.power.PowerParser
import com.batteryforensics.parser.power.WakeLockParser
import com.batteryforensics.parser.usagestats.UsageStatsParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ParserFixtureTest {
    @Test
    fun batteryStats_parsesCapacityAndScreen() {
        val raw = readFixture("fixtures/batterystats_sample.txt")
        val result = BatteryStatsParser().parse(raw)
        assertThat(result).isInstanceOf(ParseResult.Success::class.java)
        val summary = (result as ParseResult.Success).value
        assertThat(summary.estimatedBatteryCapacityMah).isEqualTo(4500)
        assertThat(summary.screenOnDischargeMah).isEqualTo(180.0)
    }

    @Test
    fun deviceIdle_parsesState() {
        val raw = readFixture("fixtures/deviceidle_sample.txt")
        val result = DeviceIdleParser().parse(raw) as ParseResult.Success
        assertThat(result.value.deepEnabled).isTrue()
        assertThat(result.value.state).isEqualTo("ACTIVE")
    }

    @Test
    fun doze_listsKnownStates() {
        val raw = readFixture("fixtures/deviceidle_sample.txt")
        val result = DozeParser().parse(raw) as ParseResult.Success
        assertThat(result.value.historyHints).isNotEmpty()
    }

    @Test
    fun doze_detectsMotionAndLocationInterruptions() {
        val raw = readFixture("fixtures/deviceidle_rich.txt")
        val result = DozeParser().parse(raw) as ParseResult.Success
        val doze = result.value
        assertThat(doze.state).isEqualTo("IDLE")
        assertThat(doze.observedStates).containsAtLeast(
            "ACTIVE", "INACTIVE", "IDLE_PENDING", "SENSING", "LOCATING", "IDLE", "IDLE_MAINTENANCE",
        )
        assertThat(doze.motionTriggeredInterruptions).isAtLeast(1)
        assertThat(doze.locationTriggeredInterruptions).isAtLeast(1)
        assertThat(doze.transitions).isNotEmpty()
        assertThat(doze.transitions.any { it.reasonHint == "MOTION" }).isTrue()
        assertThat(doze.transitions.any { it.reasonHint == "LOCATION" }).isTrue()
    }

    @Test
    fun wakeLock_classifiesModemWifiSensorsHal() {
        val raw = """
            Wake Locks: size=12
            uid=1000 pid=1 tag=*modem* type=PARTIAL
            uid=1000 pid=2 tag=wifi_wake type=PARTIAL
            uid=1000 pid=3 tag=sensor_hub type=PARTIAL
            uid=1000 pid=4 tag=PowerManagerService.WakeLocks type=PARTIAL
            uid=10123 pid=55 tag=AudioMix type=PARTIAL
            uid=10123 pid=55 tag=GpsLocationProvider type=PARTIAL
            kernel wakelock: wlan_rx
            kernel wakelock: radio
        """.trimIndent()
        val wl = WakeLockParser().parse(raw) as ParseResult.Success
        assertThat(wl.value.totalLocks).isEqualTo(12)
        assertThat(wl.value.modemLocks).isAtLeast(1)
        assertThat(wl.value.wifiLocks).isAtLeast(1)
        assertThat(wl.value.sensorLocks).isAtLeast(1)
        assertThat(wl.value.powerHalLocks).isAtLeast(1)
        assertThat(wl.value.appLocks).isAtLeast(1)
        assertThat(wl.value.taxonomyNotes).isNotEmpty()
    }

    @Test
    fun alarm_classifiesRtcVsElapsedAndWakeupsPerHour() {
        val raw = """
            Current Alarm Manager state:
            50 wakeup alarms
            RTC_WAKEUP alarms: 18
            ELAPSED_REALTIME_WAKEUP alarms: 32
            com.example.sync: 12 wakeup
            com.google.android.gms: 20 wakeup
            Alarm{a1 type=0 when=RTC_WAKEUP}
            Alarm{a2 type=2 when=ELAPSED_REALTIME_WAKEUP}
            Elapsed realtime since boot: 7200000ms
        """.trimIndent()
        val result = AlarmParser().parse(raw) as ParseResult.Success
        val a = result.value
        assertThat(a.wakeupAlarmCount).isAtLeast(2)
        assertThat(a.rtcWakeupCount).isEqualTo(18)
        assertThat(a.elapsedRealtimeWakeupCount).isEqualTo(32)
        assertThat(a.wakeupsPerHour).isNotNull()
        assertThat(a.wakeupsPerHour!!).isGreaterThan(0.0)
        assertThat(a.impactEstimate).isNotEmpty()
        assertThat(a.topPackages.map { it.packageName }).contains("com.google.android.gms")
    }

    @Test
    fun usageStats_parsesFullStandbyBucketModel() {
        val raw = """
            App Standby:
            com.a.app standby_bucket=ACTIVE
            com.b.app standby_bucket=WORKING_SET
            com.c.app standby_bucket=FREQUENT
            com.d.app standby_bucket=RARE
            com.e.app standby_bucket=RESTRICTED
            com.bypass.app standby_bucket=ACTIVE exempted=true
            standby bucket: RARE
        """.trimIndent()
        val result = UsageStatsParser().parse(raw) as ParseResult.Success
        val u = result.value
        assertThat(u.bucketCounts.keys).containsAtLeast(
            "ACTIVE", "WORKING_SET", "FREQUENT", "RARE", "RESTRICTED",
        )
        assertThat(u.bucketCounts["ACTIVE"]).isAtLeast(1)
        assertThat(u.bypassPackageHints).contains("com.bypass.app")
        assertThat(u.elevatedBucketPackages).isNotEmpty()
    }

    @Test
    fun power_parsesWakeLockCount() {
        val raw = readFixture("fixtures/power_sample.txt")
        val result = PowerParser().parse(raw) as ParseResult.Success
        assertThat(result.value.wakeLockCount).isEqualTo(3)
        val wl = WakeLockParser().parse(raw) as ParseResult.Success
        assertThat(wl.value.totalLocks).isEqualTo(3)
    }

    @Test
    fun alarm_parsesWakeups() {
        val raw = """
            Current Alarm Manager state:
            42 wakeup alarms
            com.example.sync: 12 wakeup
            Alarm{a1 type 2}
            Alarm{a2 type 2}
        """.trimIndent()
        val result = AlarmParser().parse(raw) as ParseResult.Success
        assertThat(result.value.wakeupAlarmCount).isAtLeast(2)
    }

    @Test
    fun jobs_parsesPending() {
        val raw = "Pending jobs: 7\nJobStatus{abc}"
        val result = JobSchedulerParser().parse(raw) as ParseResult.Success
        assertThat(result.value.pendingJobCount).isEqualTo(7)
    }

    @Test
    fun usageStats_findsBuckets() {
        val raw = "standby_bucket=ACTIVE\nstandby bucket: RARE"
        val result = UsageStatsParser().parse(raw) as ParseResult.Success
        assertThat(result.value.standbyBucketHints).isNotEmpty()
    }

    @Test
    fun activity_parsesFgsHints() {
        val raw = """
            ACTIVITY MANAGER
            mResumedActivity=ActivityRecord{com.example/.Main}
            isForeground=true com.example.tracker.SyncService
        """.trimIndent()
        val result = ActivityParser().parse(raw) as ParseResult.Success
        assertThat(result.value.topResumedActivity).contains("Main")
    }

    @Test
    fun cmdBattery_parsesLevel() {
        val raw = "Current battery status:\n  level: 77\n  temperature: 320\n  status: 2"
        val result = CmdBatteryParser().parse(raw) as ParseResult.Success
        assertThat(result.value.level).isEqualTo(77)
        assertThat(result.value.temperatureTenthsC).isEqualTo(320)
    }

    @Test
    fun cmdJobs_parsesPending() {
        val raw = "Pending jobs: 11\nRunning jobs: 2"
        val result = CmdJobSchedulerParser().parse(raw) as ParseResult.Success
        assertThat(result.value.pendingJobCount).isEqualTo(11)
        assertThat(result.value.runningJobCount).isEqualTo(2)
    }

    @Test
    fun emptyDump_failsGracefully() {
        val result = BatteryStatsParser().parse("")
        assertThat(result).isInstanceOf(ParseResult.Failure::class.java)
    }

    private fun readFixture(path: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "Missing fixture $path"
        }.bufferedReader().readText()
}
