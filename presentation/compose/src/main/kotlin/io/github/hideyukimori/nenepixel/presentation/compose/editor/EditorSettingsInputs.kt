package io.github.hideyukimori.nenepixel.presentation.compose.editor

/** The settings sheet renders app-wide preferences and app identity, never document state. */
internal data class EditorSettingsInputs(
    val language: AppLanguageControls,
    val version: AppVersionDisplay,
)
