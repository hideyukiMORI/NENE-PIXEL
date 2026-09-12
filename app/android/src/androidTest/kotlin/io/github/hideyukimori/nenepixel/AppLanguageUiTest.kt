package io.github.hideyukimori.nenepixel

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguage
import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class AppLanguageUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()
    private var original: AppLanguage = AppLanguage.System

    @Before
    fun readInitialLanguage() {
        original = (runBlocking { storage().read() } as AppLanguageRead.Loaded).language
        awaitLanguage(original)
        selectFromHost(AppLanguage.English)
        awaitTag("editor_canvas_16_16")
        composeRule.waitUntil(TIMEOUT) {
            var idle = false
            composeRule.activityRule.scenario.onActivity {
                idle =
                    it
                        .editor()
                        .persistenceOperations.value.phase is PersistenceOperationPhase.Idle
            }
            idle
        }
        if (composeRule.onAllNodes(hasTestTag("editor_discard_unsaved")).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("editor_discard_unsaved").performClick()
            awaitTag("editor_clean_document")
        }
    }

    @After
    fun restoreInitialLanguage() {
        assertEquals(AppLanguageWrite.Saved, runBlocking { storage().write(original) })
    }

    @Test
    fun languagePickerRetainsTheRuntimeDocumentAndUndoHistory() {
        composeRule.onNodeWithTag("editor_canvas_16_16").performTouchInput { click(center) }
        lateinit var runtime: EditorRuntime
        lateinit var document: DocumentState
        composeRule.activityRule.scenario.onActivity {
            runtime = it.editor().runtime
            document = runtime.state.documentState
        }
        val cases =
            listOf(
                LanguageExpectation(AppLanguage.Japanese, "editor_language_japanese", "鉛筆ツール"),
                LanguageExpectation(AppLanguage.SimplifiedChinese, "editor_language_chinese", "铅笔工具"),
                LanguageExpectation(AppLanguage.English, "editor_language_english", "Pencil tool"),
            )
        cases.forEach { expected ->
            composeRule.onNodeWithTag("editor_appearance").performClick()
            composeRule.onNodeWithTag(expected.tag).performScrollTo().performClick()
            awaitLanguage(expected.language)
            composeRule.onNodeWithTag(expected.tag).assertIsSelected()
            composeRule.onNodeWithTag("editor_close_panel").performClick()
            composeRule.onNodeWithTag("editor_pencil_tool").assertContentDescriptionEquals(expected.pencil)
            composeRule.activityRule.scenario.onActivity {
                assertSame(runtime, it.editor().runtime)
                assertSame(
                    document,
                    it
                        .editor()
                        .runtime.state.documentState,
                )
            }
        }
        composeRule.onNodeWithTag("editor_undo").performClick()
        composeRule.onNodeWithTag("editor_clean_document").assertExists()
        composeRule.onNodeWithTag("editor_redo").performClick()
        composeRule.onNodeWithTag("editor_dirty_document").assertExists()
    }

    @Test
    fun typedValidationAndRawInputSurviveLanguageChangeAndRecreation() {
        composeRule.onNodeWithTag("editor_file").performClick()
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodes(hasTestTag("editor_new_document") and isEnabled()).fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithTag("editor_new_document").performClick()
        composeRule.onNodeWithTag("editor_document_width").performTextReplacement("257")
        composeRule.onNodeWithTag("editor_document_height").performTextReplacement("4")
        composeRule.onNodeWithTag("editor_create").performClick()
        composeRule.onNodeWithTag("editor_dimension_rejection").assertTextEquals("Width must be within 1–256.")
        selectFromHost(AppLanguage.Japanese)
        composeRule.activityRule.scenario.recreate()
        awaitTag("editor_dimension_rejection")
        composeRule.onNodeWithTag("editor_dimension_rejection").assertTextEquals("幅は1～256で入力してください。")
        assertEquals(
            "257",
            composeRule
                .onNodeWithTag(
                    "editor_document_width",
                ).fetchSemanticsNode()
                .config[SemanticsProperties.EditableText]
                .text,
        )
        assertEquals(
            "4",
            composeRule
                .onNodeWithTag(
                    "editor_document_height",
                ).fetchSemanticsNode()
                .config[SemanticsProperties.EditableText]
                .text,
        )
        composeRule.onNodeWithTag("editor_cancel").performClick()
    }

    @Test
    fun externalPlatformSelectionIsReadOnActivityStartAndSystemRestoresFallback() {
        assertEquals(AppLanguageWrite.Saved, runBlocking { storage().write(AppLanguage.SimplifiedChinese) })
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) composeRule.activityRule.scenario.recreate()
        awaitLanguage(AppLanguage.SimplifiedChinese)
        composeRule.onNodeWithTag("editor_pencil_tool").assertContentDescriptionEquals("铅笔工具")
        selectFromHost(AppLanguage.System)
        val expected =
            InstrumentationRegistry.getInstrumentation().targetContext.getString(
                io.github.hideyukimori.nenepixel.presentation.compose.R.string.pencil_tool,
            )
        composeRule.onNodeWithTag("editor_pencil_tool").assertContentDescriptionEquals(expected)
        assertEquals(AppLanguage.System, (runBlocking { storage().read() } as AppLanguageRead.Loaded).language)
    }

    @Test
    fun regionalOsOverrideMapsToJapaneseAndSystemClearsIt() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val service =
                InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(
                    LocaleManager::class.java,
                )
            service.applicationLocales = LocaleList.forLanguageTags("ja-JP")
            awaitLanguage(AppLanguage.Japanese)
            composeRule.onNodeWithTag("editor_pencil_tool").assertContentDescriptionEquals("鉛筆ツール")
            selectFromHost(AppLanguage.System)
            assertEquals("", service.applicationLocales.toLanguageTags())
        }
    }

    @Test
    fun discardConfirmationRetainsItsRequestAcrossLanguageChange() {
        composeRule.onNodeWithTag("editor_canvas_16_16").performTouchInput { click(center) }
        lateinit var model: EditorRuntimeViewModel
        composeRule.activityRule.scenario.onActivity { model = it.editor() }
        val document = model.runtime.state.documentState
        composeRule.onNodeWithTag("editor_file").performClick()
        composeRule.onNodeWithTag("editor_new_document").performClick()
        composeRule.onNodeWithTag("editor_create").performClick()
        composeRule.waitUntil(TIMEOUT) {
            model.persistenceOperations.value.phase is PersistenceOperationPhase.NeedsConfirmation
        }
        val operation = model.persistenceOperations.value.phase
        selectFromHost(AppLanguage.Japanese)
        composeRule.onNodeWithTag("editor_keep_current").assertContentDescriptionEquals("現在の作業を保持")
        assertEquals(operation, model.persistenceOperations.value.phase)
        composeRule.onNodeWithTag("editor_keep_current").performClick()
        composeRule.waitUntil(TIMEOUT) { model.persistenceOperations.value.phase is PersistenceOperationPhase.Idle }
        assertSame(document, model.runtime.state.documentState)
    }

    private fun selectFromHost(language: AppLanguage) {
        composeRule.activityRule.scenario.onActivity { it.languages().controller.select(language) }
        awaitLanguage(language)
    }

    private fun awaitLanguage(language: AppLanguage) {
        composeRule.waitUntil(TIMEOUT) {
            var ready = false
            composeRule.activityRule.scenario.onActivity {
                val state =
                    it
                        .languages()
                        .controller.settings.value
                ready = state.selection == language && state.status == AppLanguageStatus.Ready
            }
            ready
        }
        composeRule.waitForIdle()
    }

    private fun awaitTag(tag: String) {
        composeRule.waitUntil(TIMEOUT) { composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size == 1 }
    }

    private fun storage(): AndroidAppLanguageStorage =
        AndroidAppLanguageStorage(
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext,
            Dispatchers.IO,
        )

    private fun MainActivity.languages(): AppLanguageViewModel =
        ViewModelProvider(this, AppLanguageViewModel.factory(application))[AppLanguageViewModel::class.java]

    private fun MainActivity.editor(): EditorRuntimeViewModel =
        ViewModelProvider(this, EditorRuntimeViewModel.factory(application))[EditorRuntimeViewModel::class.java]

    private data class LanguageExpectation(
        val language: AppLanguage,
        val tag: String,
        val pencil: String,
    )

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
