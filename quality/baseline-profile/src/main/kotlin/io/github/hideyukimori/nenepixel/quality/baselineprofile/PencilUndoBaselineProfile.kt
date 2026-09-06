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
            device.awaitObject(By.text(CLEAN_LABEL))
            device.awaitObject(By.desc(PENCIL_DESCRIPTION))
            device.awaitObject(undoButton(enabled = false))
            val canvas = device.awaitObject(By.desc(CANVAS_DESCRIPTION))
            val bounds = canvas.visibleBounds
            check(bounds.width() > 0 && bounds.height() > 0) { "Canvas bounds must be non-empty: $bounds" }

            val x = bounds.left + bounds.width() / (CANVAS_WIDTH * 2)
            val y = bounds.top + bounds.height() / (CANVAS_HEIGHT * 2)
            check(device.click(x, y)) { "Pencil input was not accepted at ($x, $y)." }

            device.awaitObject(By.text(DIRTY_LABEL))
            device.awaitObject(undoButton(enabled = true)).click()
            device.awaitObject(By.text(CLEAN_LABEL))
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

    private fun UiDevice.awaitObject(selector: BySelector): UiObject2 =
        checkNotNull(wait(Until.findObject(selector), UI_TIMEOUT_MILLIS)) {
            "Timed out waiting for UI object matching $selector."
        }

    private fun undoButton(enabled: Boolean): BySelector =
        By.clickable(true).enabled(enabled).hasDescendant(By.text(UNDO_LABEL))

    private companion object {
        const val APPLICATION_PACKAGE = "io.github.hideyukimori.nenepixel"
        const val CANVAS_DESCRIPTION = "16 by 16 pixel canvas"
        const val PENCIL_DESCRIPTION = "Pencil tool"
        const val CLEAN_LABEL = "No unsaved changes"
        const val DIRTY_LABEL = "Unsaved changes"
        const val UNDO_LABEL = "Undo"
        const val CANVAS_WIDTH = 16
        const val CANVAS_HEIGHT = 16
        const val UI_TIMEOUT_MILLIS = 5_000L
        const val MAX_ITERATIONS = 15
        const val STABLE_ITERATIONS = 3
    }
}
