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
