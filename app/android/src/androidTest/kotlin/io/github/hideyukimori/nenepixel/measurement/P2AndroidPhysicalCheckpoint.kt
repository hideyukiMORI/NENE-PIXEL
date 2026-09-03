package io.github.hideyukimori.nenepixel.measurement

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.PowerManager
import android.view.Display
import java.util.Locale
import kotlin.math.abs

internal data class P2AndroidPhysicalCheckpoint(
    val name: String,
    val sampleIndex: Int,
    val displayModeId: Int,
    val physicalWidthPixels: Int,
    val physicalHeightPixels: Int,
    val refreshRateHertz: Float,
    val thermalStatus: Int,
    val powerSaveMode: Boolean,
    val interactive: Boolean,
    val usbPowered: Boolean,
    val batteryLevelPercent: Int,
) {
    fun assertInitialValidity() {
        check(thermalStatus <= P2AndroidPhysicalCheckpointPolicy.MAXIMUM_VALID_THERMAL_STATUS) {
            "Thermal status $thermalStatus exceeds the physical evidence limit."
        }
        check(!powerSaveMode) { "Power-save mode must remain disabled for physical evidence." }
        check(interactive) { "The display must remain interactive for physical evidence." }
        check(usbPowered) { "The device must remain USB powered for physical evidence." }
        check(batteryLevelPercent in BATTERY_PERCENTAGE_RANGE) {
            "Battery level is unavailable for physical evidence."
        }
        check(
            abs(refreshRateHertz - P2AndroidPhysicalCheckpointPolicy.REQUIRED_REFRESH_RATE_HERTZ) <=
                P2AndroidPhysicalCheckpointPolicy.REFRESH_RATE_TOLERANCE_HERTZ,
        ) {
            "Active display refresh rate ${refreshRateHertz.formatHertz()} Hz is not the required 90 Hz profile."
        }
    }

    fun assertCompatibleWith(baseline: P2AndroidPhysicalCheckpoint) {
        assertInitialValidity()
        check(displayModeId == baseline.displayModeId) { "Active display mode changed during measurement." }
        check(physicalWidthPixels == baseline.physicalWidthPixels) {
            "Active display width changed during measurement."
        }
        check(physicalHeightPixels == baseline.physicalHeightPixels) {
            "Active display height changed during measurement."
        }
        check(
            abs(refreshRateHertz - baseline.refreshRateHertz) <=
                P2AndroidPhysicalCheckpointPolicy.REFRESH_RATE_TOLERANCE_HERTZ,
        ) {
            "Active display refresh rate changed during measurement."
        }
    }

    fun reportValues(): List<Pair<String, Any>> =
        listOf(
            "display_mode_id" to displayModeId,
            "display_width_pixels" to physicalWidthPixels,
            "display_height_pixels" to physicalHeightPixels,
            "refresh_rate_hertz" to refreshRateHertz,
            "thermal_status" to thermalStatus,
            "power_save_mode" to powerSaveMode,
            "interactive" to interactive,
            "usb_powered" to usbPowered,
            "battery_level_percent" to batteryLevelPercent,
        )

    private fun Float.formatHertz(): String = String.format(Locale.ROOT, "%.3f", this)

    private companion object {
        val BATTERY_PERCENTAGE_RANGE: IntRange = 0..100
    }
}

internal object P2AndroidPhysicalCheckpointCapture {
    fun capture(
        context: Context,
        display: Display,
        name: String,
        sampleIndex: Int,
    ): P2AndroidPhysicalCheckpoint {
        val mode = display.mode
        val powerManager = context.getSystemService(PowerManager::class.java)
        val battery =
            requireNotNull(context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))) {
                "Battery status is unavailable for the physical checkpoint."
            }
        val plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        return P2AndroidPhysicalCheckpoint(
            name = name,
            sampleIndex = sampleIndex,
            displayModeId = mode.modeId,
            physicalWidthPixels = mode.physicalWidth,
            physicalHeightPixels = mode.physicalHeight,
            refreshRateHertz = mode.refreshRate,
            thermalStatus = powerManager.currentThermalStatus,
            powerSaveMode = powerManager.isPowerSaveMode,
            interactive = powerManager.isInteractive,
            usbPowered = plugged == BatteryManager.BATTERY_PLUGGED_USB,
            batteryLevelPercent = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
        )
    }

    fun defaultDisplay(context: Context): Display {
        val displayManager = context.getSystemService(DisplayManager::class.java)
        return requireNotNull(displayManager.getDisplay(Display.DEFAULT_DISPLAY)) {
            "The default physical display is unavailable for measurement."
        }
    }
}

internal object P2AndroidPhysicalCheckpointPolicy {
    const val CHECKPOINT_INTERVAL: Int = 25
    const val MAXIMUM_VALID_THERMAL_STATUS: Int = 1
    const val REQUIRED_REFRESH_RATE_HERTZ: Float = 90.0f
    const val REFRESH_RATE_TOLERANCE_HERTZ: Float = 0.5f
}
