package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguage

internal interface AppLanguageStorage {
    suspend fun read(): AppLanguageRead

    suspend fun write(language: AppLanguage): AppLanguageWrite
}

internal sealed interface AppLanguageRead {
    data class Loaded(
        val language: AppLanguage,
    ) : AppLanguageRead

    data object Failed : AppLanguageRead
}

internal enum class AppLanguageWrite { Saved, Failed }
