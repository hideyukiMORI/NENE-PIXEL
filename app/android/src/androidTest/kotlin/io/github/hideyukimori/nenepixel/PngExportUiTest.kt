package io.github.hideyukimori.nenepixel

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Instrumentation
import android.content.ComponentName
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class PngExportUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun exportActionOpensAndroidPickerAndCancellationPreservesDocument() {
        verifyExportCancellation { }
    }

    @Test
    fun languageChangeWhilePickerIsOpenRetainsItsLeaseAndDocument() {
        val storage =
            AndroidAppLanguageStorage(
                InstrumentationRegistry.getInstrumentation().targetContext.applicationContext,
                Dispatchers.IO,
            )
        val original = (runBlocking { storage.read() } as AppLanguageRead.Loaded).language
        val selected = if (original == AppLanguage.Japanese) AppLanguage.English else AppLanguage.Japanese
        try {
            verifyExportCancellation { model ->
                val operation = model.persistenceOperations.value.phase
                assertEquals(AppLanguageWrite.Saved, runBlocking { storage.write(selected) })
                assertEquals(operation, model.persistenceOperations.value.phase)
                assertNull(model.pickerBroker.pendingRequest.value)
            }
        } finally {
            assertEquals(AppLanguageWrite.Saved, runBlocking { storage.write(original) })
        }
    }

    private fun verifyExportCancellation(beforeReturn: (EditorRuntimeViewModel) -> Unit) {
        composeRule.onNodeWithTag("editor_file").performClick()
        composeRule.waitUntil {
            composeRule.onAllNodes(hasTestTag("editor_export_png") and isEnabled()).fetchSemanticsNodes().size ==
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
        composeRule.onNodeWithTag("editor_export_png").performClick()
        composeRule.waitUntil { model.persistenceOperations.value.phase is PersistenceOperationPhase.Exporting }
        composeRule.waitUntil(timeoutMillis = PICKER_RETURN_TIMEOUT_MS) {
            instrumentation.uiAutomation.rootInActiveWindow
                ?.packageName
                ?.toString() == pickerPackage
        }
        dismissPickerKeyboard(instrumentation)
        beforeReturn(model)
        assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
        composeRule.waitUntil(timeoutMillis = PICKER_RETURN_TIMEOUT_MS) {
            model.persistenceOperations.value.phase is PersistenceOperationPhase.Idle
        }
        assertEquals(PersistenceLastOutcome.Cancelled, model.persistenceOperations.value.lastOutcome)
        assertEquals(before, model.runtime.state)
        composeRule.onNodeWithTag("editor_file").performClick()
        composeRule.onNodeWithTag("editor_export_png").assertExists()
    }

    private fun dismissPickerKeyboard(instrumentation: Instrumentation) {
        val automation = instrumentation.uiAutomation
        val service = automation.serviceInfo
        val originalFlags = service.flags
        try {
            service.flags = originalFlags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = service
            automation.waitForIdle(100L, PICKER_RETURN_TIMEOUT_MS)
            if (automation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }) {
                assertTrue(automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
                composeRule.waitUntil(PICKER_RETURN_TIMEOUT_MS) {
                    automation.windows.none { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                }
            }
        } finally {
            service.flags = originalFlags
            automation.serviceInfo = service
        }
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
