package io.github.hideyukimori.nenepixel.presentation.compose.editor

public data class AppLanguageSettings(
    public val selection: AppLanguage,
    public val status: AppLanguageStatus,
)

public enum class AppLanguageStatus { Loading, Ready, Applying, ReadFailed, WriteFailed }
