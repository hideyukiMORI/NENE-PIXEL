package io.github.hideyukimori.nenepixel.presentation.compose.editor

/** The existing palette JSON file operations at the composition root. */
public class PaletteExchangeCallbacks(
    internal val exportJson: () -> Unit,
    internal val importJson: () -> Unit,
)
