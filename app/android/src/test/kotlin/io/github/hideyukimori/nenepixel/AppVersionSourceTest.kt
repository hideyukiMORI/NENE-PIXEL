package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppVersionDisplay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The host owns the platform lookup, so every outcome becomes the one typed display value and never
 * an exception, a null, or a substituted version.
 */
internal class AppVersionSourceTest {
    @Test
    fun installedPackageMetadataBecomesTheDisplayedVersion() {
        val source = AppVersionSource { AppVersionMetadata("0.1.0", 1L) }

        assertEquals(AppVersionDisplay.Available("0.1.0", 1L), source.read())
    }

    @Test
    fun aPlatformWithoutALongVersionCodeStillReportsTheVersionName() {
        val source = AppVersionSource { AppVersionMetadata("0.1.0", null) }

        assertEquals(AppVersionDisplay.Available("0.1.0", null), source.read())
    }

    @Test
    fun absentVersionNameIsUnavailableRatherThanAnEmptyLine() {
        listOf(null, "", "   ").forEach { name ->
            val source = AppVersionSource { AppVersionMetadata(name, 1L) }

            assertEquals(AppVersionDisplay.Unavailable, source.read(), "versionName=$name")
        }
    }

    @Test
    fun failedPlatformLookupIsUnavailableInsteadOfACrash() {
        val source = AppVersionSource { error("package manager is unavailable") }

        assertEquals(AppVersionDisplay.Unavailable, source.read())
    }
}
