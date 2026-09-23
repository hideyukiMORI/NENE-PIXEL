package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.editor.RuntimeSourceToken
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition

/** Runtime-only: `EditorRuntime.beginPaletteEdit` captures [base] and [definition] under the runtime lock. */
internal data class BeginPaletteEdit(
    val base: RuntimeSourceToken,
    val definition: PaletteDefinition,
) : WorkspaceAction
