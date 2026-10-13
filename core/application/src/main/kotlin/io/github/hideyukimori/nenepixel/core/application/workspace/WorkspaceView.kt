package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState

/** View projection settings and the optional reference picture; no document truth is stored here. */
internal data class WorkspaceView(
    val viewport: ViewportState,
    val appearance: EditorAppearance,
    val actualSizeWindow: ActualSizeWindow,
    val underlay: ReferenceUnderlay?,
)
