package io.github.hideyukimori.nenepixel.presentation.compose.editor

/** The settings sheet renders app-wide preferences and app identity, never document state. */
public class EditorSettingsInputs(
    internal val language: AppLanguageControls,
    internal val version: AppVersionDisplay,
)
