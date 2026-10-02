package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRequestResult

public class ProjectFileCallbacks(
    internal val exchange: FileExchangeCallbacks,
    internal val saveAs: () -> Unit,
    internal val load: () -> Unit,
    internal val createNewDocument: (NewDocumentRequestResult) -> Unit,
)

/**
 * Files exchanged beside the project file: the PNG export and the palette JSON export / import (ADR 0022),
 * the reference image picked to show under the drawing (ADR 0032), the PNG picked to import, and the pending PNG
 * opened as a new work (ADR 0033).
 */
public class FileExchangeCallbacks(
    internal val exportPng: () -> Unit,
    internal val exportPaletteJson: () -> Unit,
    internal val importPaletteJson: () -> Unit,
    internal val pickReferenceImage: () -> Unit,
    internal val importPng: () -> Unit,
    internal val openPngAsNewWork: () -> Unit,
)
