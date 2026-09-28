package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId

/** Chooses the workspace's active layer when the runtime installs or transitions a document (ADR 0030). */
internal object LayerSelectionPolicy {
    /** A newly installed document (new, load, recovery adoption) starts on its top layer. */
    fun onInstall(document: DocumentState): LayerId = document.layers.last().id

    /** Keeps [current] while it exists; otherwise falls back to the top layer. `null` means unchanged. */
    fun afterApplied(
        current: LayerId,
        document: DocumentState,
    ): LayerId? =
        if (document.layers.any { it.id == current }) {
            null
        } else {
            onInstall(document)
        }
}
