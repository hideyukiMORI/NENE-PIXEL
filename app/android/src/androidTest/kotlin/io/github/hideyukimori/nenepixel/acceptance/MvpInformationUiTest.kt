package io.github.hideyukimori.nenepixel.acceptance

import android.graphics.Bitmap
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.AppLanguageViewModel
import io.github.hideyukimori.nenepixel.EditorRuntimeViewModel
import io.github.hideyukimori.nenepixel.MainActivity
import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguage
import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream

internal class MvpInformationUiTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun informationIsLocalizedScrollableAndRetainedWithoutChangingTheEditor() {
        val fixture = AcceptanceFixture()
        fixture.requireIsolation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var editor: EditorRuntimeViewModel
            lateinit var languages: AppLanguageViewModel
            scenario.onActivity {
                editor =
                    ViewModelProvider(
                        it,
                        EditorRuntimeViewModel.factory(it.application),
                    )[EditorRuntimeViewModel::class.java]
                languages =
                    ViewModelProvider(
                        it,
                        AppLanguageViewModel.factory(it.application),
                    )[AppLanguageViewModel::class.java]
            }
            composeRule.waitUntil(TIMEOUT) { languages.controller.settings.value.status == AppLanguageStatus.Ready }
            val original = languages.controller.settings.value.selection
            val before = editor.runtime.state
            try {
                listOf(
                    AppLanguage.English to "About this version",
                    AppLanguage.Japanese to "このバージョンについて",
                    AppLanguage.SimplifiedChinese to "关于此版本",
                ).forEach { (language, title) ->
                    scenario.onActivity { languages.controller.select(language) }
                    composeRule.waitUntil(TIMEOUT) {
                        languages.controller.settings.value.let {
                            it.selection == language &&
                                it.status == AppLanguageStatus.Ready
                        }
                    }
                    composeRule.onNodeWithTag("editor_file").performClick()
                    composeRule.onNodeWithTag("editor_mvp_information").performScrollTo().performClick()
                    composeRule.onNodeWithTag("editor_mvp_information_title").assertTextEquals(title)
                    scenario.recreate()
                    composeRule.waitUntil(TIMEOUT) {
                        composeRule.onAllNodes(hasTestTag("editor_mvp_information_title")).fetchSemanticsNodes().size ==
                            1
                    }
                    composeRule.onNodeWithTag("editor_mvp_information_title").assertTextEquals(title)
                    capture(fixture, language.name.lowercase() + "-top")
                    composeRule.onNodeWithTag("editor_mvp_compatibility_title").performScrollTo().assertExists()
                    capture(fixture, language.name.lowercase() + "-bottom")
                    composeRule.onNodeWithTag("editor_close_information").performClick()
                    composeRule.onNodeWithTag("editor_mvp_information_title").assertDoesNotExist()
                    composeRule.onNodeWithTag("editor_close_panel").performClick()
                    scenario.onActivity {
                        val current =
                            ViewModelProvider(
                                it,
                                EditorRuntimeViewModel.factory(it.application),
                            )[EditorRuntimeViewModel::class.java]
                        assertSame(editor, current)
                        assertEquals(before, current.runtime.state)
                    }
                }
            } finally {
                scenario.onActivity { languages.controller.select(original) }
                composeRule.waitUntil(TIMEOUT) {
                    languages.controller.settings.value.let {
                        it.selection == original &&
                            it.status == AppLanguageStatus.Ready
                    }
                }
            }
        }
    }

    private fun capture(
        fixture: AcceptanceFixture,
        suffix: String,
    ) {
        composeRule.waitForIdle()
        val screenshot = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            val bytes =
                ByteArrayOutputStream().use { stream ->
                    check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    stream.toByteArray()
                }
            fixture.writeNew("info-$suffix.png", bytes)
        } finally {
            screenshot.recycle()
        }
    }

    private companion object {
        const val TIMEOUT = 60_000L
    }
}
