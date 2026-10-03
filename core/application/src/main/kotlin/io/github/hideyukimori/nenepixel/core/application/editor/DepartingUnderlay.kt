package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId

/**
 * The one departing capture (ADR 0034): the unpublished underlay of the work an installation
 * replaced. A null [underlay] means the work's record is to be forgotten. A completion clears the
 * capture only when it wrote this same instance.
 */
internal data class DepartingUnderlay(
    val document: DocumentId,
    val underlay: RememberedUnderlay?,
) {
    fun write(): UnderlayMemoryWrite = UnderlayMemoryWrite.of(document, underlay)
}
