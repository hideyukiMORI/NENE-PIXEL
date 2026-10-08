package io.github.hideyukimori.nenepixel.quality.baselineprofile

import android.app.KeyguardManager
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern
import kotlin.math.floor

@RunWith(AndroidJUnit4::class)
internal class PencilUndoBaselineProfile {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun collectCanonicalPencilUndoJourney() {
        requireUnlockedDevice()
        baselineProfileRule.collect(
            packageName = APPLICATION_PACKAGE,
            maxIterations = MAX_ITERATIONS,
            stableIterations = STABLE_ITERATIONS,
            includeInStartupProfile = false,
            strictStability = true,
        ) {
            pressHome()
            startActivityAndWait()

            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            device.declineRecoveryOffer()
            device.awaitObject(By.res(CLEAN_LABEL))
            device.awaitObject(By.res(PENCIL_DESCRIPTION))
            device.awaitObject(undoButton(enabled = false))
            val canvas = device.awaitObject(By.res(CANVAS_DESCRIPTION))
            val bounds = canvas.visibleBounds
            check(bounds.width() > 0 && bounds.height() > 0) { "Canvas bounds must be non-empty: $bounds" }

            val fit = minOf(bounds.width().toDouble() / CANVAS_WIDTH, bounds.height().toDouble() / CANVAS_HEIGHT)
            val originX = bounds.left + (bounds.width() - fit * CANVAS_WIDTH) / CENTERING_DIVISOR
            val originY = bounds.top + (bounds.height() - fit * CANVAS_HEIGHT) / CENTERING_DIVISOR
            val x = floor(originX + fit / CENTERING_DIVISOR).toInt()
            val y = floor(originY + fit / CENTERING_DIVISOR).toInt()
            check(device.click(x, y)) { "Pencil input was not accepted at ($x, $y)." }

            device.awaitObject(By.res(DIRTY_LABEL))
            device.awaitObject(undoButton(enabled = true)).click(CANONICAL_TAP_DURATION_MILLIS)
            device.awaitObject(By.res(CLEAN_LABEL))
            device.awaitObject(undoButton(enabled = false))
        }
    }

    private fun requireUnlockedDevice() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        check(!keyguardManager.isDeviceLocked) {
            "The physical Baseline Profile device must be unlocked before generation."
        }
    }

    /**
     * The previous iteration is stopped inside the autosave window, so the next launch may offer
     * its recovery record. The offer replaces the clean label until it is declined.
     * Both control taps use the same fixed pointer-down interval; a shorter press lets the ripple's delayed reset
     * path into the profile.
     */
    private fun UiDevice.declineRecoveryOffer() {
        val first = awaitObject(By.res(CLEAN_OR_DISCARD))
        if (first.resourceName == DISCARD_RECOVERY) first.click(CANONICAL_TAP_DURATION_MILLIS)
    }

    private fun UiDevice.awaitObject(selector: BySelector): UiObject2 =
        checkNotNull(wait(Until.findObject(selector), UI_TIMEOUT_MILLIS)) {
            "Timed out waiting for UI object matching $selector."
        }

    private fun undoButton(enabled: Boolean): BySelector = By.clickable(true).enabled(enabled).res(UNDO_LABEL)

    private companion object {
        const val APPLICATION_PACKAGE = "io.github.hideyukimori.nenepixel"
        const val CANVAS_DESCRIPTION = "editor_canvas_16_16"
        const val PENCIL_DESCRIPTION = "editor_pencil_tool"
        const val CLEAN_LABEL = "editor_clean_document"
        const val DIRTY_LABEL = "editor_dirty_document"
        const val DISCARD_RECOVERY = "editor_discard_unsaved"
        val CLEAN_OR_DISCARD: Pattern = Pattern.compile("$CLEAN_LABEL|$DISCARD_RECOVERY")
        const val UNDO_LABEL = "editor_undo"
        const val CANVAS_WIDTH = 16
        const val CANVAS_HEIGHT = 16
        const val CENTERING_DIVISOR = 2.0
        const val UI_TIMEOUT_MILLIS = 5_000L
        const val CANONICAL_TAP_DURATION_MILLIS = 100L
        const val MAX_ITERATIONS = 15
        const val STABLE_ITERATIONS = 3
    }
}
