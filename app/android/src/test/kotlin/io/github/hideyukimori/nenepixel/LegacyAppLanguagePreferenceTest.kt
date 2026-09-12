package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class LegacyAppLanguagePreferenceTest {
    @Test
    fun failedCommitRestoresPreviousMemoryValueEvenIfRollbackAlsoFails() {
        listOf<String?>(null, "ja").forEach { previous ->
            var memory = previous
            val writes = mutableListOf<String?>()
            val preference =
                LegacyAppLanguagePreference(
                    readTag = { memory },
                    commitTag = { value ->
                        memory = value
                        writes.add(value)
                        false
                    },
                )
            assertEquals(AppLanguageWrite.Failed, preference.write(AppLanguage.English))
            assertEquals(listOf("en", previous), writes)
            assertEquals(previous, memory)
            assertEquals(readAppLanguageTag(previous.orEmpty()), preference.read())
        }
    }

    @Test
    fun successfulCommitUsesOneWriteAndBecomesTheOnlyReadValue() {
        var memory: String? = null
        var writes = 0
        val preference =
            LegacyAppLanguagePreference(
                readTag = { memory },
                commitTag = { value ->
                    memory = value
                    writes += 1
                    true
                },
            )
        assertEquals(AppLanguageWrite.Saved, preference.write(AppLanguage.Japanese))
        assertEquals(1, writes)
        assertEquals(AppLanguageRead.Loaded(AppLanguage.Japanese), preference.read())
    }

    @Test
    fun regionalOsTagsResolveToThePickerChoiceAndUnsupportedOverridesAreNotSystem() {
        val samples =
            mapOf(
                "" to AppLanguage.System,
                "ja-JP" to AppLanguage.Japanese,
                "en-US" to AppLanguage.English,
                "zh-Hans-CN" to AppLanguage.SimplifiedChinese,
                "zh-CN" to AppLanguage.SimplifiedChinese,
            )
        samples.forEach { (tag, language) -> assertEquals(AppLanguageRead.Loaded(language), readAppLanguageTag(tag)) }
        listOf("ar", "zh-Hant", "zh-TW", "unknown").forEach { tag ->
            assertEquals(AppLanguageRead.Failed, readAppLanguageTag(tag))
        }
    }
}
