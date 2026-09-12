package io.github.hideyukimori.nenepixel

import android.os.Bundle
import android.os.Process
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguage
import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguageStatus
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/** Two explicit instrumentation invocations with force-stop between them; no synthetic process death. */
internal class AppLanguageProcessTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun prepareSavedJapaneseSelection() {
        awaitEditor()
        composeRule.onNodeWithTag("editor_appearance").performClick()
        composeRule.onNodeWithTag("editor_language_japanese").performScrollTo().performClick()
        awaitJapanese()
        reportProcess()
    }

    @Test
    fun verifySavedJapaneseSelectionAfterRestart() {
        val prior = requireNotNull(InstrumentationRegistry.getArguments().getString("priorProcessId")).toInt()
        assertNotEquals(prior, Process.myPid())
        awaitJapanese()
        composeRule.onNodeWithTag("editor_pencil_tool").assertContentDescriptionEquals("鉛筆ツール")
        reportProcess()
    }

    private fun awaitEditor() {
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodes(hasTestTag("editor_appearance")).fetchSemanticsNodes().size == 1
        }
    }

    private fun awaitJapanese() {
        composeRule.waitUntil(TIMEOUT) {
            var ready = false
            composeRule.activityRule.scenario.onActivity { activity ->
                val model =
                    ViewModelProvider(activity, AppLanguageViewModel.factory(activity.application))[
                        AppLanguageViewModel::class.java,
                    ]
                ready =
                    model.controller.settings.value.let {
                        it.selection == AppLanguage.Japanese && it.status == AppLanguageStatus.Ready
                    }
            }
            ready
        }
        composeRule.waitForIdle()
    }

    private fun reportProcess() {
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply {
                putInt("languageProcessId", Process.myPid())
            },
        )
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
