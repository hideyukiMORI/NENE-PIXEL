package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.Revision

/** Scalar freshness identity for a notice; it retains neither the document nor its pixels. */
internal data class LayerNoticeSource(
    val documentId: DocumentId,
    val revision: Revision,
)
