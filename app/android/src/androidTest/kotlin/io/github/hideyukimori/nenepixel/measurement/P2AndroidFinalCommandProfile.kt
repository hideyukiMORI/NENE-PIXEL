package io.github.hideyukimori.nenepixel.measurement

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import kotlin.math.abs

internal object P2AndroidFinalCommandProfile {
    fun validateRuntime(context: Context) {
        check(Build.FINGERPRINT == BUILD_FINGERPRINT) {
            "Final command build fingerprint must be '$BUILD_FINGERPRINT'."
        }
        check(Build.VERSION.SECURITY_PATCH == SECURITY_PATCH) {
            "Final command security patch must be '$SECURITY_PATCH'."
        }
        check(Runtime.getRuntime().maxMemory() == RUNTIME_MAX_MEMORY_BYTES) {
            "Final command Runtime.maxMemory() must be $RUNTIME_MAX_MEMORY_BYTES bytes."
        }
        val activityManager = context.getSystemService(ActivityManager::class.java)
        check(activityManager.memoryClass == MEMORY_CLASS_MIB) {
            "Final command ActivityManager.memoryClass must be $MEMORY_CLASS_MIB MiB."
        }
    }

    fun validateBaselineCheckpoint(checkpoint: P2AndroidPhysicalCheckpoint) {
        checkpoint.assertInitialValidity()
        check(checkpoint.displayModeId == DISPLAY_MODE_ID) {
            "Final command display mode must be $DISPLAY_MODE_ID."
        }
        check(checkpoint.physicalWidthPixels == DISPLAY_WIDTH_PIXELS) {
            "Final command display width must be $DISPLAY_WIDTH_PIXELS pixels."
        }
        check(checkpoint.physicalHeightPixels == DISPLAY_HEIGHT_PIXELS) {
            "Final command display height must be $DISPLAY_HEIGHT_PIXELS pixels."
        }
        check(
            abs(checkpoint.refreshRateHertz - REFRESH_RATE_HERTZ) <=
                P2AndroidPhysicalCheckpointPolicy.REFRESH_RATE_TOLERANCE_HERTZ,
        ) { "Final command display refresh rate must be 90 Hz." }
    }

    const val BUILD_FINGERPRINT: String =
        "ALLDOCUBE/iPlay80miniPro/T830:16/BP2A.250605.031.A3/94110:user/release-keys"
    const val SECURITY_PATCH: String = "2026-06-05"
    const val RUNTIME_MAX_MEMORY_BYTES: Long = 268_435_456L
    const val MEMORY_CLASS_MIB: Int = 256
    const val DISPLAY_MODE_ID: Int = 1
    const val DISPLAY_WIDTH_PIXELS: Int = 1_200
    const val DISPLAY_HEIGHT_PIXELS: Int = 1_920
    const val REFRESH_RATE_HERTZ: Float = 90.0f
}
