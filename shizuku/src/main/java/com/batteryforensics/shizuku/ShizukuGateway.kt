package com.batteryforensics.shizuku

import com.batteryforensics.parser.AlarmSummary
import com.batteryforensics.parser.BatteryStatsSummary
import com.batteryforensics.parser.DeviceIdleSummary
import com.batteryforensics.parser.DozeTimelineSummary
import com.batteryforensics.parser.JobSchedulerSummary
import com.batteryforensics.parser.ParseResult
import com.batteryforensics.parser.PowerSummary
import com.batteryforensics.parser.ThermalServiceSummary
import com.batteryforensics.parser.UsageStatsSummary
import com.batteryforensics.parser.WakeLockSummary
import com.batteryforensics.parser.alarm.AlarmParser
import com.batteryforensics.parser.batterystats.BatteryStatsParser
import com.batteryforensics.parser.deviceidle.DeviceIdleParser
import com.batteryforensics.parser.deviceidle.DozeParser
import com.batteryforensics.parser.jobscheduler.JobSchedulerParser
import com.batteryforensics.parser.power.PowerParser
import com.batteryforensics.parser.power.WakeLockParser
import com.batteryforensics.parser.thermalservice.ThermalServiceParser
import com.batteryforensics.parser.usagestats.UsageStatsParser
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku availability facade. When unavailable, advanced dumpsys paths degrade gracefully.
 */
sealed class ShizukuAvailability {
    data object Available : ShizukuAvailability()
    data object NotInstalled : ShizukuAvailability()
    data object PermissionDenied : ShizukuAvailability()
    data object Unsupported : ShizukuAvailability()
}

interface ShizukuGateway {
    fun availability(): ShizukuAvailability
    /** Executes a privileged shell command when Shizuku is granted; null when unavailable. */
    fun runShellCommand(command: String): String?
}

/**
 * Default gateway using reflection so the app never crashes if Shizuku is missing.
 * When Available, runs `sh -c <command>` via Shizuku.newProcess.
 *
 * Permission limits: dumpsys still respects SELinux / service-side checks.
 * Some OEM builds redact fields even under Shizuku.
 */
class GracefulShizukuGateway : ShizukuGateway {
    override fun availability(): ShizukuAvailability {
        return runCatching {
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val ping = clazz.getMethod("pingBinder").invoke(null) as Boolean
            if (!ping) return ShizukuAvailability.NotInstalled
            val permission = clazz.getMethod("checkSelfPermission").invoke(null) as Int
            if (permission == 0) ShizukuAvailability.Available else ShizukuAvailability.PermissionDenied
        }.getOrElse { ShizukuAvailability.NotInstalled }
    }

    override fun runShellCommand(command: String): String? {
        if (availability() != ShizukuAvailability.Available) return null
        return runCatching {
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            // Shizuku.newProcess(String[] cmd, String[] env, String dir)
            val method = clazz.methods.firstOrNull {
                it.name == "newProcess" && it.parameterTypes.size >= 1
            } ?: return null
            val process = method.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null,
            ) as? Process ?: return null
            process.inputStream.use { input ->
                BufferedReader(InputStreamReader(input)).readText()
            }.also {
                runCatching { process.destroy() }
            }.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}

/**
 * Collects typed dumpsys summaries when Shizuku is available.
 * Raw dump text is never exposed to UI callers.
 */
class ShizukuDiagnosticsCollector(
    private val gateway: ShizukuGateway = GracefulShizukuGateway(),
) {
    data class PrivilegedSnapshot(
        val availability: ShizukuAvailability,
        val batteryStats: BatteryStatsSummary? = null,
        val power: PowerSummary? = null,
        val wakeLocks: WakeLockSummary? = null,
        val deviceIdle: DeviceIdleSummary? = null,
        val doze: DozeTimelineSummary? = null,
        val alarms: AlarmSummary? = null,
        val jobs: JobSchedulerSummary? = null,
        val usageStats: UsageStatsSummary? = null,
        val thermalService: ThermalServiceSummary? = null,
        val errors: List<String> = emptyList(),
    )

    fun collect(): PrivilegedSnapshot {
        val avail = gateway.availability()
        if (avail != ShizukuAvailability.Available) {
            return PrivilegedSnapshot(
                availability = avail,
                errors = listOf("Shizuku unavailable ($avail) — using public API collectors only"),
            )
        }
        val errors = mutableListOf<String>()
        fun dump(service: String): String? = gateway.runShellCommand("dumpsys $service").also {
            if (it == null) errors += "Failed to dump $service"
        }

        fun <T> parse(raw: String?, parser: (String) -> ParseResult<T>): T? {
            if (raw == null) return null
            return when (val r = parser(raw)) {
                is ParseResult.Success -> r.value
                is ParseResult.Failure -> {
                    errors += r.message
                    null
                }
            }
        }

        val powerRaw = dump("power")
        return PrivilegedSnapshot(
            availability = avail,
            batteryStats = parse(dump("batterystats")) { BatteryStatsParser().parse(it) },
            power = parse(powerRaw) { PowerParser().parse(it) },
            wakeLocks = parse(powerRaw) { WakeLockParser().parse(it) },
            deviceIdle = parse(dump("deviceidle")) { DeviceIdleParser().parse(it) },
            doze = parse(gateway.runShellCommand("dumpsys deviceidle")) { DozeParser().parse(it) },
            alarms = parse(dump("alarm")) { AlarmParser().parse(it) },
            jobs = parse(dump("jobscheduler")) { JobSchedulerParser().parse(it) },
            usageStats = parse(dump("usagestats")) { UsageStatsParser().parse(it) },
            thermalService = parse(dump("thermalservice")) { ThermalServiceParser().parse(it) },
            errors = errors,
        )
    }
}
