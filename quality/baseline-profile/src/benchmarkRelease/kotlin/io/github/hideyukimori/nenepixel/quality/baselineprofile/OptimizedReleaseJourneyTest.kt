package io.github.hideyukimori.nenepixel.quality.baselineprofile

import android.app.KeyguardManager
import android.app.UiAutomation
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Condition
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
internal class OptimizedReleaseJourneyTest {
    @get:Rule
    val hierarchyCapture: TestWatcher = UiHierarchyFailureCapture()

    @Test
    fun pencilCommitUndoRedoPreservesCanonicalUiState() {
        val device = launchEditor()
        device.awaitObject(toolControl(PENCIL_DESCRIPTION, checked = true))
        device.awaitObject(historyButton(UNDO_LABEL, enabled = false))
        device.awaitObject(historyButton(REDO_LABEL, enabled = false))
        val canvas = device.awaitObject(By.res(CANVAS_DESCRIPTION))
        val bounds = canvas.visibleBounds
        check(bounds.width() > 0 && bounds.height() > 0) { "Canvas bounds must be non-empty: $bounds" }

        val x = bounds.left + bounds.width() / (CANVAS_WIDTH * 2)
        val y = bounds.top + bounds.height() / (CANVAS_HEIGHT * 2)
        check(device.click(x, y)) { "Pencil input was not accepted at ($x, $y)." }

        device.awaitObject(By.res(DIRTY_LABEL))
        device.awaitObject(historyButton(UNDO_LABEL, enabled = true)).click(HISTORY_TAP_DURATION_MILLIS)
        device.awaitObject(By.res(CLEAN_LABEL))
        device.awaitObject(historyButton(UNDO_LABEL, enabled = false))
        device.awaitObject(historyButton(REDO_LABEL, enabled = true)).click(HISTORY_TAP_DURATION_MILLIS)
        device.awaitObject(By.res(DIRTY_LABEL))
        device.awaitObject(historyButton(UNDO_LABEL, enabled = true))
        device.awaitObject(historyButton(REDO_LABEL, enabled = false))
    }

    @Test
    fun palettePencilEraserAndTwoStepUndoPreserveCommandHistory() {
        val device = launchEditor()
        device.awaitObject(paletteEntry(SECOND_PALETTE_DESCRIPTION, checked = false)).click()
        device.awaitObject(paletteEntry(SECOND_PALETTE_DESCRIPTION, checked = true))
        val canvas = device.awaitObject(By.res(CANVAS_DESCRIPTION))
        val bounds = canvas.visibleBounds
        val insetX = bounds.width() / (CANVAS_WIDTH * 2)
        val insetY = bounds.height() / (CANVAS_HEIGHT * 2)
        check(
            device.swipe(
                bounds.left + insetX,
                bounds.top + insetY,
                bounds.right - insetX,
                bounds.bottom - insetY,
                FINITE_DIAGONAL_STEPS,
            ),
        ) { "The long Pencil stroke was not accepted." }
        device.waitForIdle()
        device.awaitObject(By.res(DIRTY_LABEL))

        device.awaitObject(toolControl(ERASER_DESCRIPTION, checked = false)).click()
        device.awaitObject(toolControl(ERASER_DESCRIPTION, checked = true))
        check(
            device.swipe(
                bounds.left + insetX,
                bounds.top + insetY,
                bounds.right - insetX,
                bounds.bottom - insetY,
                FINITE_DIAGONAL_STEPS,
            ),
        ) { "The overlapping Eraser stroke was not accepted." }
        device.waitForIdle()
        device.awaitObject(historyButton(UNDO_LABEL, enabled = true)).click(HISTORY_TAP_DURATION_MILLIS)
        device.awaitObject(By.res(DIRTY_LABEL))
        device.awaitObject(historyButton(REDO_LABEL, enabled = true))
        device.awaitObject(historyButton(UNDO_LABEL, enabled = true)).click(HISTORY_TAP_DURATION_MILLIS)
        device.awaitObject(By.res(CLEAN_LABEL))
        device.awaitObject(historyButton(UNDO_LABEL, enabled = false))
        device.awaitObject(historyButton(REDO_LABEL, enabled = true))
    }

    @Test
    fun newDocumentSurvivesOneRestoredOrientationRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = launchEditor()
        device.awaitObject(appButton(NEW_DOCUMENT_LABEL)).click()
        device.awaitObject(dimensionField(DOCUMENT_WIDTH_DESCRIPTION)).text = "3"
        device.waitForIdle()
        check(device.awaitObject(dimensionField(DOCUMENT_WIDTH_DESCRIPTION)).text == "3") { "Width did not commit." }
        device.awaitObject(dimensionField(DOCUMENT_HEIGHT_DESCRIPTION)).text = "2"
        device.waitForIdle()
        check(device.awaitObject(dimensionField(DOCUMENT_HEIGHT_DESCRIPTION)).text == "2") { "Height did not commit." }
        check(
            device.awaitObject(dimensionField(DOCUMENT_WIDTH_DESCRIPTION)).text == "3" &&
                device.awaitObject(dimensionField(DOCUMENT_HEIGHT_DESCRIPTION)).text == "2",
        ) { "Document dimensions changed before Create." }
        device.awaitObject(appButton(CREATE_LABEL)).click()
        check(device.wait(Until.gone(By.pkg(APPLICATION_PACKAGE).res(CREATE_DOCUMENT_TITLE)), UI_TIMEOUT_MILLIS)) {
            "The new-document dialog did not close after Create."
        }
        device.awaitObject(By.res(NEW_CANVAS_DESCRIPTION))
        device.awaitObject(By.res(CLEAN_LABEL))
        device.awaitObject(toolControl(PENCIL_DESCRIPTION, checked = true))
        device.awaitObject(paletteEntry(FIRST_PALETTE_DESCRIPTION, checked = true))
        device.awaitObject(historyButton(UNDO_LABEL, enabled = false))
        device.awaitObject(historyButton(REDO_LABEL, enabled = false))

        withRestoredOrientation(device, instrumentation.uiAutomation) { originalRotation ->
            val changedRotation =
                if (originalRotation == Surface.ROTATION_0) Surface.ROTATION_90 else Surface.ROTATION_0
            check(instrumentation.uiAutomation.setRotation(changedRotation.toUiAutomationRotation())) {
                "The test device rejected the requested orientation change."
            }
            check(device.waitForRotation(changedRotation, UI_TIMEOUT_MILLIS)) {
                "Orientation did not change from $originalRotation to $changedRotation."
            }
            device.awaitObject(By.res(NEW_CANVAS_DESCRIPTION))
            device.awaitObject(By.res(CLEAN_LABEL))
            device.awaitObject(historyButton(UNDO_LABEL, enabled = false))
            device.awaitObject(historyButton(REDO_LABEL, enabled = false))
        }
    }

    private fun launchEditor(): UiDevice {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val keyguardManager = instrumentation.context.getSystemService(KeyguardManager::class.java)
        check(!keyguardManager.isDeviceLocked) { "The optimized-release test device must be unlocked." }

        val device = UiDevice.getInstance(instrumentation)
        device.executeShellCommand("am force-stop $APPLICATION_PACKAGE")
        val startOutput = device.executeShellCommand("am start -W -n $ACTIVITY_NAME")
        check("Error:" !in startOutput && "Exception" !in startOutput) {
            "The optimized release did not start: $startOutput"
        }
        device.awaitObject(By.res(CLEAN_LABEL))
        return device
    }

    private fun UiDevice.awaitObject(selector: BySelector): UiObject2 =
        checkNotNull(wait(Until.findObject(selector), UI_TIMEOUT_MILLIS)) {
            "Timed out waiting for UI object matching $selector."
        }

    private fun historyButton(
        label: String,
        enabled: Boolean,
    ): BySelector = By.clickable(true).enabled(enabled).res(label)

    private fun paletteEntry(
        description: String,
        checked: Boolean,
    ): BySelector = By.res(description).checkable(true).checked(checked)

    private fun toolControl(
        description: String,
        checked: Boolean,
    ): BySelector = By.checkable(true).checked(checked).res(description)

    private fun UiDevice.readAutoRotation(): Boolean {
        val value = executeShellCommand("settings get system accelerometer_rotation").trim()
        check(value == "0" || value == "1") { "Unexpected accelerometer_rotation value: $value" }
        return value == "1"
    }

    private fun withRestoredOrientation(
        device: UiDevice,
        uiAutomation: UiAutomation,
        block: (Int) -> Unit,
    ) {
        val original = RotationState(device.displayRotation, device.readAutoRotation())
        val testResult = runCatching { block(original.displayRotation) }
        val primaryFailure = testResult.exceptionOrNull()
        if (primaryFailure != null) device.captureBeforeRotationRestore(primaryFailure, ORIENTATION_PRE_RESTORE_CAPTURE)
        val restorationFailure =
            runCatching {
                check(uiAutomation.setRotation(original.displayRotation.toUiAutomationRotation())) {
                    "The test device rejected restoration to rotation ${original.displayRotation}."
                }
                check(device.waitForRotation(original.displayRotation, UI_TIMEOUT_MILLIS)) {
                    "Display rotation was not restored to ${original.displayRotation}."
                }
                if (original.autoRotation) {
                    check(uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)) {
                        "The test device rejected auto-rotation restoration."
                    }
                }
                check(device.readAutoRotation() == original.autoRotation) {
                    "Auto-rotation state was not restored."
                }
                check(device.waitForRotation(original.displayRotation, UI_TIMEOUT_MILLIS)) {
                    "Display rotation changed after restoring its original mode."
                }
            }.exceptionOrNull()
        if (restorationFailure != null) {
            if (primaryFailure == null) throw restorationFailure
            primaryFailure.addSuppressed(restorationFailure)
        }
        testResult.getOrThrow()
    }

    private fun Int.toUiAutomationRotation(): Int =
        when (this) {
            Surface.ROTATION_0 -> UiAutomation.ROTATION_FREEZE_0
            Surface.ROTATION_90 -> UiAutomation.ROTATION_FREEZE_90
            Surface.ROTATION_180 -> UiAutomation.ROTATION_FREEZE_180
            Surface.ROTATION_270 -> UiAutomation.ROTATION_FREEZE_270
            else -> error("Unexpected display rotation: $this")
        }

    private companion object {
        const val ACTIVITY_NAME = "$APPLICATION_PACKAGE/.MainActivity"
        const val CANVAS_DESCRIPTION = "editor_canvas_16_16"
        const val PENCIL_DESCRIPTION = "editor_pencil_tool"
        const val ERASER_DESCRIPTION = "editor_eraser_tool"
        const val FIRST_PALETTE_DESCRIPTION = "editor_palette_entry_1"
        const val SECOND_PALETTE_DESCRIPTION = "editor_palette_entry_2"
        const val NEW_DOCUMENT_LABEL = "editor_new_document"
        const val DOCUMENT_WIDTH_DESCRIPTION = "editor_document_width"
        const val DOCUMENT_HEIGHT_DESCRIPTION = "editor_document_height"
        const val CREATE_LABEL = "editor_create"
        const val CREATE_DOCUMENT_TITLE = "editor_create_document_title"
        const val NEW_CANVAS_DESCRIPTION = "editor_canvas_3_2"
        const val CLEAN_LABEL = "editor_clean_document"
        const val DIRTY_LABEL = "editor_dirty_document"
        const val UNDO_LABEL = "editor_undo"
        const val REDO_LABEL = "editor_redo"
        const val CANVAS_WIDTH = 16
        const val CANVAS_HEIGHT = 16
        const val UI_TIMEOUT_MILLIS = 5_000L
        const val HISTORY_TAP_DURATION_MILLIS = 100L
        const val FINITE_DIAGONAL_STEPS = 16
        const val ORIENTATION_PRE_RESTORE_CAPTURE =
            "issue76-failure-newDocumentSurvivesOneRestoredOrientationRecreation-before-orientation-restore.xml"
    }

    private data class RotationState(
        val displayRotation: Int,
        val autoRotation: Boolean,
    )
}

private fun appButton(label: String): BySelector =
    By
        .pkg(APPLICATION_PACKAGE)
        .clickable(true)
        .enabled(true)
        .res(label)

private fun dimensionField(description: String): BySelector =
    By
        .pkg(APPLICATION_PACKAGE)
        .clazz("android.widget.EditText")
        .clickable(true)
        .enabled(true)
        .res(description)

private fun UiDevice.waitForRotation(
    expected: Int,
    timeoutMillis: Long,
): Boolean = wait(Condition<UiDevice, Boolean> { device -> device.displayRotation == expected }, timeoutMillis)

private fun UiDevice.captureBeforeRotationRestore(
    primaryFailure: Throwable,
    fileName: String,
) {
    val captureFailure =
        runCatching {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val outputDirectory = checkNotNull(instrumentation.context.getExternalFilesDir(null))
            val output = File(outputDirectory, fileName)
            dumpWindowHierarchy(output)
            check(output.isFile && output.length() > 0L) { "Pre-restoration hierarchy was not written: $output" }
        }.exceptionOrNull()
    if (captureFailure != null) primaryFailure.addSuppressed(captureFailure)
}

private class UiHierarchyFailureCapture : TestWatcher() {
    override fun failed(
        error: Throwable,
        description: Description,
    ) {
        val captureFailure =
            runCatching {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val outputDirectory = checkNotNull(instrumentation.context.getExternalFilesDir(null))
                val methodName = checkNotNull(description.methodName)
                val output = File(outputDirectory, "issue76-failure-$methodName.xml")
                UiDevice.getInstance(instrumentation).dumpWindowHierarchy(output)
                check(output.isFile && output.length() > 0L) { "Failure hierarchy was not written: $output" }
            }.exceptionOrNull()
        if (captureFailure != null) error.addSuppressed(captureFailure)
    }
}

private const val APPLICATION_PACKAGE = "io.github.hideyukimori.nenepixel"
