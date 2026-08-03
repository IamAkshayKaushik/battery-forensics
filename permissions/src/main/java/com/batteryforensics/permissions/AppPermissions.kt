package com.batteryforensics.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Runtime / special permissions this app may request, with privacy-first rationales.
 * Nothing leaves the device — every permission exists to explain battery drain.
 */
enum class PermissionKind {
    NOTIFICATIONS,
    LOCATION,
    PHONE_STATE,
    BATTERY_OPTIMIZATION,
}

data class PermissionSpec(
    val kind: PermissionKind,
    /** Manifest runtime permissions to request together (empty for special intents). */
    val runtimePermissions: List<String>,
    val title: String,
    val rationale: String,
    val unlocks: String,
)

object AppPermissions {
    const val RATIONALE =
        "Battery Forensics requests only the permissions needed to measure signals that help explain drain. Nothing leaves your device."

    val allSpecs: List<PermissionSpec>
        get() = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(
                    PermissionSpec(
                        kind = PermissionKind.NOTIFICATIONS,
                        runtimePermissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
                        title = "Notifications",
                        rationale = "Shows the Flight Recorder is actively sampling so you can stop it anytime. No marketing alerts.",
                        unlocks = "Reliable Flight Recorder foreground sampling",
                    ),
                )
            }
            add(
                PermissionSpec(
                    kind = PermissionKind.LOCATION,
                    runtimePermissions = listOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ),
                    title = "Location (radio evidence)",
                    rationale = "Android gates cell-signal APIs behind location. We use it only for RSSI / CellInfo forensics — never maps, never uploaded.",
                    unlocks = "Cellular signal strength and radio-active evidence",
                ),
            )
            add(
                PermissionSpec(
                    kind = PermissionKind.PHONE_STATE,
                    runtimePermissions = listOf(Manifest.permission.READ_PHONE_STATE),
                    title = "Phone state",
                    rationale = "Reads network type (LTE/5G/…) so Causes can correlate radio changes with drain. No call logs or contacts.",
                    unlocks = "Network type transitions for modem forensics",
                ),
            )
            add(
                PermissionSpec(
                    kind = PermissionKind.BATTERY_OPTIMIZATION,
                    runtimePermissions = emptyList(),
                    title = "Battery optimization exemption",
                    rationale = "Optional. Lets Flight Recorder keep a steady sample cadence while you sleep. Sampling stays on-device.",
                    unlocks = "Fewer gaps in overnight Flight Recorder timelines",
                ),
            )
        }

    /** Runtime permissions typically needed before enabling monitoring / Flight Recorder. */
    val monitoringRuntime: List<String>
        get() = allSpecs.flatMap { it.runtimePermissions }.distinct()

    /** Permissions required for cellular forensics (location + phone). */
    val cellularRuntime: List<String>
        get() = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )

    /** Notifications only (API 33+). */
    val notificationRuntime: List<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun missingRuntime(context: Context, permissions: List<String>): List<String> =
        permissions.filterNot { isGranted(context, it) }

    fun statusOf(context: Context, spec: PermissionSpec): PermissionGrantStatus {
        return when (spec.kind) {
            PermissionKind.BATTERY_OPTIMIZATION -> {
                if (isIgnoringBatteryOptimizations(context)) {
                    PermissionGrantStatus.Granted
                } else {
                    PermissionGrantStatus.NotGranted
                }
            }
            else -> {
                val missing = missingRuntime(context, spec.runtimePermissions)
                when {
                    missing.isEmpty() -> PermissionGrantStatus.Granted
                    else -> PermissionGrantStatus.NotGranted
                }
            }
        }
    }

    fun criticalMissing(context: Context): List<PermissionSpec> =
        allSpecs.filter { statusOf(context, it) != PermissionGrantStatus.Granted }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife")
    fun batteryOptimizationRequestIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }

    /** Legacy list kept for call sites that only need the string array. */
    val monitoringOptional: List<String>
        get() = monitoringRuntime
}

enum class PermissionGrantStatus {
    Granted,
    NotGranted,
}
