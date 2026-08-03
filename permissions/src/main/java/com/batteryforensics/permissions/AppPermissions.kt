package com.batteryforensics.permissions

import android.Manifest
import android.os.Build

object AppPermissions {
    /** Permissions requested only when the related feature is enabled. */
    val monitoringOptional: List<String>
        get() = buildList {
            add(Manifest.permission.ACCESS_WIFI_STATE)
            add(Manifest.permission.ACCESS_NETWORK_STATE)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    const val RATIONALE =
        "Battery Forensics requests only the permissions needed to measure signals that help explain drain. Nothing leaves your device."
}
