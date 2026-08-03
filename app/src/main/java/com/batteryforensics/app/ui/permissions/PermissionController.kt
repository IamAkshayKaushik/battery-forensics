package com.batteryforensics.app.ui.permissions

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.batteryforensics.permissions.AppPermissions
import com.batteryforensics.permissions.PermissionKind
import com.batteryforensics.permissions.PermissionSpec

/**
 * Activity Result helpers for runtime permissions + battery-optimization intent.
 * Re-checks on ON_RESUME so Settings deep-link grants refresh the UI.
 */
@Composable
fun rememberPermissionController(
    onChanged: () -> Unit = {},
): PermissionController {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    var promptedOnce by remember { mutableStateOf(false) }

    val multiLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        promptedOnce = true
        revision++
        onChanged()
    }

    val batteryOptLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        revision++
        onChanged()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                revision++
                onChanged()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return remember(multiLauncher, batteryOptLauncher, revision, promptedOnce, activity) {
        fun launchRuntime(perms: List<String>) {
            val missing = AppPermissions.missingRuntime(context, perms)
            if (missing.isEmpty()) return
            // After a prior ask, if the system won't show a dialog again → App Settings.
            val permanentlyDenied = promptedOnce &&
                activity != null &&
                missing.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
            if (permanentlyDenied) {
                context.startActivity(AppPermissions.appSettingsIntent(context).withActivityFlags())
                return
            }
            multiLauncher.launch(missing.toTypedArray())
        }

        PermissionController(
            revision = revision,
            requestRuntime = { launchRuntime(it) },
            requestSpec = { spec ->
                when (spec.kind) {
                    PermissionKind.BATTERY_OPTIMIZATION -> {
                        batteryOptLauncher.launch(
                            AppPermissions.batteryOptimizationRequestIntent(context),
                        )
                    }
                    else -> launchRuntime(spec.runtimePermissions)
                }
            },
            openAppSettings = {
                context.startActivity(AppPermissions.appSettingsIntent(context).withActivityFlags())
            },
            missingCritical = { AppPermissions.criticalMissing(context) },
            missingRuntime = { AppPermissions.missingRuntime(context, it) },
        )
    }
}

class PermissionController(
    val revision: Int,
    val requestRuntime: (List<String>) -> Unit,
    val requestSpec: (PermissionSpec) -> Unit,
    val openAppSettings: () -> Unit,
    val missingCritical: () -> List<PermissionSpec>,
    val missingRuntime: (List<String>) -> List<String>,
) {
    fun requestMonitoringPermissions() {
        requestRuntime(AppPermissions.monitoringRuntime)
    }

    /**
     * Setup-banner CTA: request every missing critical item.
     * Runtime perms first; if those are done, launch battery-optimization intent.
     * Never silently no-op while the banner still lists missing items.
     */
    fun requestCriticalPermissions() {
        val missing = missingCritical()
        if (missing.isEmpty()) return
        val runtimeNeeded = missing
            .filter { it.kind != PermissionKind.BATTERY_OPTIMIZATION }
            .flatMap { it.runtimePermissions }
            .distinct()
            .let { missingRuntime(it) }
        when {
            runtimeNeeded.isNotEmpty() -> requestRuntime(runtimeNeeded)
            missing.any { it.kind == PermissionKind.BATTERY_OPTIMIZATION } -> {
                requestSpec(missing.first { it.kind == PermissionKind.BATTERY_OPTIMIZATION })
            }
            else -> openAppSettings()
        }
    }

    fun requestFlightRecorderPermissions() {
        requestRuntime(
            (AppPermissions.notificationRuntime + AppPermissions.cellularRuntime).distinct(),
        )
    }

    fun requestCellularPermissions() {
        requestRuntime(AppPermissions.cellularRuntime)
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun Intent.withActivityFlags(): Intent =
    apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
