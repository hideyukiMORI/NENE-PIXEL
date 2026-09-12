package io.github.hideyukimori.nenepixel

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

internal class AppLanguageStorageTest {
    @Test
    fun malformedLegacyPreferenceReportsFailureWithoutReplacingItsValue() {
        assumeTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
        // Instrumentation runs as the app UID; isolate the fixture in its own preference namespace.
        val context =
            object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
                override fun getSharedPreferences(
                    name: String,
                    mode: Int,
                ): SharedPreferences = super.getSharedPreferences("issue-102-test-$name", mode)
            }
        val preferences = context.getSharedPreferences("nene-pixel-app-language-v1", Context.MODE_PRIVATE)
        val storage = AndroidAppLanguageStorage(context, Dispatchers.IO)
        try {
            assertTrue(preferences.edit().putInt("language", 1).commit())
            assertEquals(AppLanguageRead.Failed, runBlocking { storage.read() })
            assertEquals(AppLanguageWrite.Failed, runBlocking { storage.write(AppLanguage.Japanese) })
            assertEquals(1, preferences.getInt("language", 0))
        } finally {
            assertTrue(preferences.edit().remove("language").commit())
        }
    }
}
