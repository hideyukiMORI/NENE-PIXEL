package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRequestResult

public class ProjectFileCallbacks(
    internal val exportPng: () -> Unit,
    internal val saveAs: () -> Unit,
    internal val load: () -> Unit,
    internal val createNewDocument: (NewDocumentRequestResult) -> Unit,
)
