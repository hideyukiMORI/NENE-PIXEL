package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.presentation.compose.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The settings line selects one localized resource per typed outcome, so a failed host lookup shows
 * the translated unavailable text instead of an empty or substituted version.
 */
internal class AppVersionDisplayTest {
    @Test
    fun aReportedNameAndCodeUseTheTwoArgumentVersionResource() {
        assertEquals(R.string.app_version, AppVersionDisplay.Available("0.1.0", 1L).labelResource())
    }

    @Test
    fun aReportedNameWithoutACodeUsesTheNameOnlyResource() {
        assertEquals(R.string.app_version_name, AppVersionDisplay.Available("0.1.0", null).labelResource())
    }

    @Test
    fun anUnavailableVersionUsesTheLocalizedFallbackResource() {
        assertEquals(R.string.app_version_unavailable, AppVersionDisplay.Unavailable.labelResource())
    }

    @Test
    fun everyShippedLocaleRendersTheNameAndCodeOnOneLine() {
        val version = AppVersionDisplay.Available("0.1.0", 1L)
        val rendered =
            shippedLocales.map { directory ->
                string(directory, "app_version").format(version.versionName, version.versionCode)
            }
        assertEquals(listOf("Version 0.1.0 (1)", "バージョン 0.1.0 (1)", "版本 0.1.0 (1)"), rendered)
    }

    @Test
    fun everyShippedLocaleStatesThatTheVersionIsUnavailable() {
        val rendered = shippedLocales.map { directory -> string(directory, "app_version_unavailable") }

        assertEquals(listOf("Version unavailable", "バージョンを取得できませんでした", "无法获取版本信息"), rendered)
    }

    private fun string(
        directory: String,
        name: String,
    ): String {
        val file = Path.of("src/main/res", directory, "strings.xml").toFile()
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val elements = document.documentElement.getElementsByTagName("string")
        val values =
            (0 until elements.length)
                .map { index -> elements.item(index) as Element }
                .filter { element -> element.getAttribute("name") == name }
        return values.single().textContent
    }

    private companion object {
        val shippedLocales = listOf("values", "values-ja", "values-b+zh+Hans")
    }
}
