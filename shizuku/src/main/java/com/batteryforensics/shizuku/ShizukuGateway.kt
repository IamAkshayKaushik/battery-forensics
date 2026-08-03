package com.batteryforensics.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.batteryforensics.parser.ActivitySummary
import com.batteryforensics.parser.AlarmSummary
import com.batteryforensics.parser.BatteryStatsSummary
import com.batteryforensics.parser.CmdBatterySummary
import com.batteryforensics.parser.DeviceIdleSummary
import com.batteryforensics.parser.DozeTimelineSummary
import com.batteryforensics.parser.JobSchedulerSummary
import com.batteryforensics.parser.ParseResult
import com.batteryforensics.parser.PowerSummary
import com.batteryforensics.parser.ThermalServiceSummary
import com.batteryforensics.parser.UsageStatsSummary
import com.batteryforensics.parser.WakeLockSummary
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
import com.batteryforensics.parser.thermalservice.ThermalServiceParser
import com.batteryforensics.parser.usagestats.UsageStatsParser
import com.batteryforensics.ruleengine.PrivilegedEvidence
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku availability facade. When unavailable, advanced dumpsys paths degrade gracefully.
 */
sealed class ShizukuAvailability {
    /** Binder up and app authorized — dumpsys path usable. */
    data object Available : ShizukuAvailability()
    /** Shizuku manager package not found. */
    data object NotInstalled : ShizukuAvailability()
    /** Manager present but server binder not reachable (not started). */
    data object NotRunning : ShizukuAvailability()
    /** Binder up but user has not granted this app. */
    data object PermissionDenied : ShizukuAvailability()
    /** Pre-v11 or otherwise unusable. */
    data object Unsupported : ShizukuAvailability()
}

fun ShizukuAvailability.label(): String = when (this) {
    ShizukuAvailability.Available -> "Authorized — dumpsys collectors active"
    ShizukuAvailability.NotInstalled -> "Not installed"
    ShizukuAvailability.NotRunning -> "Installed but not running — start Shizuku"
    ShizukuAvailability.PermissionDenied -> "Running — not authorized"
    ShizukuAvailability.Unsupported -> "Unsupported on this device"
}

fun ShizukuAvailability.limitedFeatures(): List<String> = when (this) {
    ShizukuAvailability.Available -> emptyList()
    else -> listOf(
        "Doze / deviceidle depth",
        "Wake lock attribution",
        "AlarmManager wakeup packages",
        "JobScheduler pending jobs",
        "App Standby buckets",
    )
}

/**
 * Pure state mapping for tests and [GracefulShizukuGateway.availability].
 * Binder-alive wins over package visibility gaps (API 30+ queries).
 */
fun resolveShizukuAvailability(
    sdkInt: Int,
    managerInstalled: Boolean,
    binderAlive: Boolean,
    preV11: Boolean,
    permissionGranted: Boolean,
): ShizukuAvailability {
    if (sdkInt < Build.VERSION_CODES.P) return ShizukuAvailability.Unsupported
    if (binderAlive) {
        if (preV11) return ShizukuAvailability.Unsupported
        return if (permissionGranted) {
            ShizukuAvailability.Available
        } else {
            ShizukuAvailability.PermissionDenied
        }
    }
    return when {
        managerInstalled -> ShizukuAvailability.NotRunning
        else -> ShizukuAvailability.NotInstalled
    }
}

interface ShizukuGateway {
    fun availability(context: Context): ShizukuAvailability
    /** Executes a privileged shell command when Shizuku is granted; null when unavailable. */
    fun runShellCommand(command: String): String?
    fun requestPermission(requestCode: Int = REQUEST_CODE)
    fun addPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener)
    fun removePermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener)
    fun addBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener)
    fun removeBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener)
    fun addBinderDeadListener(listener: Shizuku.OnBinderDeadListener)
    fun removeBinderDeadListener(listener: Shizuku.OnBinderDeadListener)
    /** Launch intent for the Shizuku manager app, if installed. */
    fun managerLaunchIntent(context: Context): Intent?

    companion object {
        const val REQUEST_CODE = 0xBF01
        const val MANAGER_PACKAGE = "moe.shizuku.manager"
        /** Legacy / alternate package id seen on some installs. */
        const val LEGACY_MANAGER_PACKAGE = "moe.shizuku.privileged.api"
        val MANAGER_PACKAGES = listOf(MANAGER_PACKAGE, LEGACY_MANAGER_PACKAGE)
    }
}

/**
 * Real Shizuku API for binder/permission; reflection only for [Shizuku.newProcess]
 * (package-private / deprecated but still the practical dumpsys path on API 13.1.x).
 */
class GracefulShizukuGateway : ShizukuGateway {
    override fun availability(context: Context): ShizukuAvailability {
        return runCatching {
            val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
            val preV11 = runCatching { Shizuku.isPreV11() }.getOrDefault(false)
            val permissionGranted = runCatching {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
            resolveShizukuAvailability(
                sdkInt = Build.VERSION.SDK_INT,
                managerInstalled = isManagerInstalled(context),
                binderAlive = binderAlive,
                preV11 = preV11,
                permissionGranted = permissionGranted,
            )
        }.getOrElse {
            if (isManagerInstalled(context)) ShizukuAvailability.NotRunning
            else ShizukuAvailability.NotInstalled
        }
    }

    override fun runShellCommand(command: String): String? {
        return runCatching {
            if (!Shizuku.pingBinder()) return null
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return null
            // newProcess is package-private; invoke via reflection while it remains available.
            val method = Shizuku::class.java.declaredMethods.firstOrNull {
                it.name == "newProcess" && it.parameterTypes.size >= 1
            } ?: return null
            method.isAccessible = true
            val process = method.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null,
            ) as? Process ?: return null
            val stdout = process.inputStream.use { input ->
                BufferedReader(InputStreamReader(input)).readText()
            }
            val stderr = runCatching {
                process.errorStream.use { input ->
                    BufferedReader(InputStreamReader(input)).readText()
                }
            }.getOrDefault("")
            runCatching { process.destroy() }
            stdout.takeIf { it.isNotBlank() } ?: stderr.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    override fun requestPermission(requestCode: Int) {
        runCatching {
            if (!Shizuku.pingBinder()) return
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return
            // Always request when binder is up — UI apps must show the Shizuku grant dialog.
            Shizuku.requestPermission(requestCode)
        }
    }

    override fun addPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }
    }

    override fun removePermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    override fun addBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
        runCatching { Shizuku.addBinderReceivedListenerSticky(listener) }
    }

    override fun removeBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
        runCatching { Shizuku.removeBinderReceivedListener(listener) }
    }

    override fun addBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        runCatching { Shizuku.addBinderDeadListener(listener) }
    }

    override fun removeBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        runCatching { Shizuku.removeBinderDeadListener(listener) }
    }

    override fun managerLaunchIntent(context: Context): Intent? {
        val pm = context.packageManager
        for (pkg in ShizukuGateway.MANAGER_PACKAGES) {
            val launch = pm.getLaunchIntentForPackage(pkg)
            if (launch != null) return launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return null
    }

    private fun isManagerInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return ShizukuGateway.MANAGER_PACKAGES.any { pkg ->
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
                true
            }.getOrDefault(false)
        }
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
        val activity: ActivitySummary? = null,
        val cmdBattery: CmdBatterySummary? = null,
        val errors: List<String> = emptyList(),
    ) {
        val hasAnyData: Boolean
            get() = batteryStats != null || power != null || wakeLocks != null ||
                deviceIdle != null || doze != null || alarms != null ||
                jobs != null || usageStats != null || thermalService != null ||
                activity != null || cmdBattery != null

        fun toPrivilegedEvidence(): PrivilegedEvidence = PrivilegedEvidence(
            batteryStats = batteryStats,
            power = power,
            wakeLocks = wakeLocks,
            deviceIdle = deviceIdle,
            doze = doze,
            alarms = alarms,
            jobs = jobs,
            usageStats = usageStats,
            thermalService = thermalService,
            activity = activity,
            cmdBattery = cmdBattery,
            collectionErrors = errors,
        )
    }

    fun collect(context: Context): PrivilegedSnapshot {
        val avail = gateway.availability(context)
        if (avail != ShizukuAvailability.Available) {
            return PrivilegedSnapshot(
                availability = avail,
                errors = listOf(
                    "Shizuku ${avail.label()} — using public API collectors only. " +
                        "Limited: ${avail.limitedFeatures().joinToString()}",
                ),
            )
        }
        val errors = mutableListOf<String>()
        fun dump(service: String): String? = gateway.runShellCommand("dumpsys $service").also {
            if (it == null) errors += "Failed to dump $service"
        }
        fun cmd(command: String): String? = gateway.runShellCommand(command).also {
            if (it == null) errors += "Failed: $command"
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
        val idleRaw = dump("deviceidle")
        val jobsRaw = dump("jobscheduler")
        val jobsFromDumpsys = parse(jobsRaw) { JobSchedulerParser().parse(it) }
        val jobsFromCmd = if (jobsFromDumpsys?.pendingJobCount == null) {
            parse(cmd("cmd jobscheduler get-job-state")) { CmdJobSchedulerParser().parse(it) }
                ?: parse(cmd("cmd jobscheduler")) { CmdJobSchedulerParser().parse(it) }
        } else {
            null
        }
        return PrivilegedSnapshot(
            availability = avail,
            batteryStats = parse(dump("batterystats")) { BatteryStatsParser().parse(it) },
            power = parse(powerRaw) { PowerParser().parse(it) },
            wakeLocks = parse(powerRaw) { WakeLockParser().parse(it) },
            deviceIdle = parse(idleRaw) { DeviceIdleParser().parse(it) },
            doze = parse(idleRaw) { DozeParser().parse(it) },
            alarms = parse(dump("alarm")) { AlarmParser().parse(it) },
            jobs = jobsFromDumpsys ?: jobsFromCmd,
            usageStats = parse(dump("usagestats")) { UsageStatsParser().parse(it) },
            thermalService = parse(dump("thermalservice")) { ThermalServiceParser().parse(it) },
            activity = parse(dump("activity")) { ActivityParser().parse(it) },
            cmdBattery = parse(cmd("cmd battery get status") ?: cmd("cmd battery")) { CmdBatteryParser().parse(it) },
            errors = errors,
        )
    }
}
