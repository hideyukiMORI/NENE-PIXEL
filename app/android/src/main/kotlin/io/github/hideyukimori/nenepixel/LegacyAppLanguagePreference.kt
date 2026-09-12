package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguage

/** Called inside the Android adapter's I/O lane; callbacks address the same private preference. */
internal class LegacyAppLanguagePreference(
    private val readTag: () -> String?,
    private val commitTag: (String?) -> Boolean,
) {
    fun read(): AppLanguageRead = readAppLanguageTag(readTag().orEmpty())

    fun write(language: AppLanguage): AppLanguageWrite {
        val previous = readTag()
        return if (commitTag(language.languageTag)) {
            AppLanguageWrite.Saved
        } else {
            // SharedPreferences changes its memory before disk commit, even when commit returns false.
            // Restore that value and attempt durable rollback; neither rollback outcome means success.
            commitTag(previous)
            AppLanguageWrite.Failed
        }
    }
}
