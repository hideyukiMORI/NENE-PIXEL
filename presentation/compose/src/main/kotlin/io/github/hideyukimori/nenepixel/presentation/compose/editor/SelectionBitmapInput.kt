package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.Bitmap
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransfer

/** Inputs are allocated only after a changed transfer misses the shared-cache identity check. */
internal class SelectionBitmapInput(
    val document: DocumentState,
    val layerId: LayerId,
    val transfer: PixelTransfer,
    val committed: CommittedBitmapCache,
) {
    val base: Bitmap = committed.render(document, document.definition, null)
}
