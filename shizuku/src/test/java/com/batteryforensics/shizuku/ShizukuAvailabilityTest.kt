package com.batteryforensics.shizuku

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShizukuAvailabilityTest {
    @Test
    fun binderAlive_andGranted_isAvailable() {
        val result = resolveShizukuAvailability(
            sdkInt = 28,
            managerInstalled = false, // package visibility miss must not win
            binderAlive = true,
            preV11 = false,
            permissionGranted = true,
        )
        assertThat(result).isEqualTo(ShizukuAvailability.Available)
    }

    @Test
    fun binderAlive_notGranted_isPermissionDenied() {
        val result = resolveShizukuAvailability(
            sdkInt = 28,
            managerInstalled = true,
            binderAlive = true,
            preV11 = false,
            permissionGranted = false,
        )
        assertThat(result).isEqualTo(ShizukuAvailability.PermissionDenied)
    }

    @Test
    fun installedButNoBinder_isNotRunning() {
        val result = resolveShizukuAvailability(
            sdkInt = 28,
            managerInstalled = true,
            binderAlive = false,
            preV11 = false,
            permissionGranted = false,
        )
        assertThat(result).isEqualTo(ShizukuAvailability.NotRunning)
        assertThat(result.label()).contains("not running")
    }

    @Test
    fun notInstalled_noBinder_isNotInstalled() {
        val result = resolveShizukuAvailability(
            sdkInt = 28,
            managerInstalled = false,
            binderAlive = false,
            preV11 = false,
            permissionGranted = false,
        )
        assertThat(result).isEqualTo(ShizukuAvailability.NotInstalled)
    }

    @Test
    fun preV11_withBinder_isUnsupported() {
        val result = resolveShizukuAvailability(
            sdkInt = 28,
            managerInstalled = true,
            binderAlive = true,
            preV11 = true,
            permissionGranted = true,
        )
        assertThat(result).isEqualTo(ShizukuAvailability.Unsupported)
    }

    @Test
    fun belowApi28_isUnsupported() {
        val result = resolveShizukuAvailability(
            sdkInt = 27,
            managerInstalled = true,
            binderAlive = true,
            preV11 = false,
            permissionGranted = true,
        )
        assertThat(result).isEqualTo(ShizukuAvailability.Unsupported)
    }
}
