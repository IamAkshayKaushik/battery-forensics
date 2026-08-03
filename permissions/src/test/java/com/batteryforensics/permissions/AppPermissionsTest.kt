package com.batteryforensics.permissions

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppPermissionsTest {
    @Test
    fun specs_includePrivacyRationales() {
        val specs = AppPermissions.allSpecs
        assertThat(specs).isNotEmpty()
        specs.forEach { spec ->
            assertThat(spec.title).isNotEmpty()
            assertThat(spec.rationale.length).isAtLeast(40)
            assertThat(spec.unlocks).isNotEmpty()
        }
        assertThat(AppPermissions.RATIONALE.lowercase()).contains("device")
    }

    @Test
    fun monitoringRuntime_includesPhoneAndLocation() {
        val perms = AppPermissions.monitoringRuntime
        assertThat(perms).contains("android.permission.READ_PHONE_STATE")
        assertThat(perms).contains("android.permission.ACCESS_FINE_LOCATION")
        assertThat(perms).contains("android.permission.ACCESS_COARSE_LOCATION")
        // POST_NOTIFICATIONS is API 33+ only — must not empty the list on older compile hosts.
        assertThat(perms).isNotEmpty()
    }

    @Test
    fun criticalMissing_includesBatteryOptimizationSpec() {
        val battery = AppPermissions.allSpecs.first { it.kind == PermissionKind.BATTERY_OPTIMIZATION }
        assertThat(battery.runtimePermissions).isEmpty()
    }
}
