package io.github.hideyukimori.nenepixel

import android.app.Instrumentation
import android.content.ComponentName
import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

internal class PngExportUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun exportActionOpensAndroidPickerAndCancellationPreservesDocument() {
        composeRule.onNodeWithContentDescription("File").performClick()
        composeRule.waitUntil {
            composeRule.onAllNodes(hasText("Export PNG") and isEnabled()).fetchSemanticsNodes().size ==
                1
        }
        lateinit var model: EditorRuntimeViewModel
        composeRule.activityRule.scenario.onActivity { activity ->
            model =
                ViewModelProvider(
                    activity,
                    EditorRuntimeViewModel.factory(activity.application),
                )[EditorRuntimeViewModel::class.java]
        }
        val before = model.runtime.state
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pickerPackage = pickerPackage(instrumentation)
        composeRule.onNodeWithText("Export PNG").performClick()
        composeRule.waitUntil { model.persistenceOperations.value.phase is PersistenceOperationPhase.Exporting }
        composeRule.waitUntil(timeoutMillis = PICKER_RETURN_TIMEOUT_MS) {
            instrumentation.uiAutomation.rootInActiveWindow
                ?.packageName
                ?.toString() == pickerPackage
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitUntil(timeoutMillis = PICKER_RETURN_TIMEOUT_MS) {
            model.persistenceOperations.value.phase is PersistenceOperationPhase.Idle
        }
        assertEquals(PersistenceLastOutcome.Cancelled, model.persistenceOperations.value.lastOutcome)
        assertEquals(before, model.runtime.state)
        composeRule.onNodeWithContentDescription("File").performClick()
        composeRule.onNodeWithText("Export PNG").assertExists()
    }

    private fun pickerPackage(instrumentation: Instrumentation): String {
        // Shell resolution is visible even when target-SDK package visibility hides the handler.
        val command =
            "cmd package resolve-activity --brief -a android.intent.action.CREATE_DOCUMENT " +
                "-c android.intent.category.OPENABLE -t image/png"
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        val output = ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        val component = output.lineSequence().last { '/' in it }.trim()
        return checkNotNull(ComponentName.unflattenFromString(component)).packageName
    }

    private companion object {
        const val PICKER_RETURN_TIMEOUT_MS: Long = 10_000L
    }
}
