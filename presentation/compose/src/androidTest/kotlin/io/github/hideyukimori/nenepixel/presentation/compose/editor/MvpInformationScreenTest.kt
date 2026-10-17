package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Issue #232: the secondary information dialog keeps disclosure, browser link and Close reachable. */
internal class MvpInformationScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun englishPrivacyIsReachableAndCloseDismisses() {
        verifyDisclosure("en", "Privacy and communication")
    }

    @Test
    fun japanesePrivacyIsReachableAndCloseDismisses() {
        verifyDisclosure("ja", "プライバシーと通信")
    }

    @Test
    fun simplifiedChinesePrivacyIsReachableAndCloseDismisses() {
        verifyDisclosure("zh-Hans", "隐私与通信")
    }

    @Test
    fun unavailableBrowserKeepsOfflineSummaryAndClose() {
        show(
            "en",
            object : UriHandler {
                override fun openUri(uri: String): Unit =
                    throw IllegalArgumentException("Synthetic unavailable browser")
            },
        )
        openAndScroll()
        rule.onNodeWithTag("editor_mvp_privacy_details").performScrollTo().performClick()
        rule.onNodeWithTag("editor_mvp_privacy_link_unavailable").performScrollTo().assertIsDisplayed()
        close()
    }

    private fun verifyDisclosure(
        language: String,
        title: String,
    ) {
        val opened = mutableListOf<String>()
        show(
            language,
            object : UriHandler {
                override fun openUri(uri: String) {
                    opened += uri
                }
            },
        )
        openAndScroll()
        rule.onNodeWithTag("editor_mvp_privacy_title").assertTextEquals(title).assertIsDisplayed()
        rule.onNodeWithTag("editor_mvp_private_data_title").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("editor_mvp_external_data_title").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("editor_mvp_privacy_details").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(
                listOf("https://github.com/hideyukiMORI/NENE-PIXEL/blob/main/docs/PRIVACY_AND_DATA_HANDLING.md"),
                opened,
            )
        }
        close()
    }

    private fun openAndScroll() {
        rule.onNodeWithTag("editor_mvp_information").performClick()
        rule.onNodeWithTag("editor_mvp_information_title").assertIsDisplayed()
        rule.onNodeWithTag("editor_mvp_privacy_title").performScrollTo().assertIsDisplayed()
    }

    private fun close() {
        rule.onNodeWithTag("editor_close_information").assertIsDisplayed().performClick()
        rule.onNodeWithTag("editor_mvp_information_title").assertDoesNotExist()
        rule.onNodeWithTag("editor_mvp_information").assertIsDisplayed()
    }

    private fun show(
        language: String,
        uriHandler: UriHandler,
    ) {
        val host = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration =
            Configuration(host.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(language)) }
        val context = host.createConfigurationContext(configuration)
        rule.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides configuration,
                LocalResources provides context.resources,
                LocalUriHandler provides uriHandler,
            ) {
                MaterialTheme { MvpInformationControls() }
            }
        }
    }
}
