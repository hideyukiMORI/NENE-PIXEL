package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportGesture
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface

/** The viewport transform gesture route, reached through [EditorCallbacks.viewport]. */
internal class EditorViewportCallbacks(
    private val started: (ViewportSurface) -> PointerInputAcknowledgement,
    private val transformed: (ViewportSurface, ViewportGesture) -> PointerInputAcknowledgement,
) {
    fun onViewportStarted(surface: ViewportSurface): PointerInputAcknowledgement = started(surface)

    fun onViewportTransformed(
        surface: ViewportSurface,
        gesture: ViewportGesture,
    ): PointerInputAcknowledgement = transformed(surface, gesture)
}
