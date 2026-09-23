package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRequestResult

public class ProjectFileCallbacks(
    internal val exchange: FileExchangeCallbacks,
    internal val saveAs: () -> Unit,
    internal val load: () -> Unit,
    internal val createNewDocument: (NewDocumentRequestResult) -> Unit,
)

/** Files exchanged beside the project file: the PNG export and the palette JSON export / import (ADR 0022). */
public class FileExchangeCallbacks(
    internal val exportPng: () -> Unit,
    internal val exportPaletteJson: () -> Unit,
    internal val importPaletteJson: () -> Unit,
)
