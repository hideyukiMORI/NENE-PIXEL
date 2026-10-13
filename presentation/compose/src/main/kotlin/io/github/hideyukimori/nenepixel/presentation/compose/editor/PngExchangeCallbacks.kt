package io.github.hideyukimori.nenepixel.presentation.compose.editor

/** The three existing PNG file operations; picker and workflow ownership remain in the app. */
public class PngExchangeCallbacks(
    internal val exportPng: () -> Unit,
    internal val importPng: () -> Unit,
    internal val openAsNewWork: () -> Unit,
)
