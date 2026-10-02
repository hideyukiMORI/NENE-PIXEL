package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayRecollection
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.fail

internal object UnderlayMemoryFixtures {
    val canvas: CanvasSize = ApplicationTestValues.canvas(64, 64)
    val workA: DocumentId = ApplicationTestValues.defaultDocumentId
    val workB: DocumentId = ApplicationTestValues.otherDocumentId
    val workC: DocumentId = documentId("c".repeat(32))

    fun image(): ReferenceImage {
        val result = ReferenceImage.create(32, 16, IntArray(32 * 16))
        check(result is ReferenceImageResult.Created) { "expected Created, got $result" }
        return result.image
    }

    fun underlay(): ReferenceUnderlay = ReferenceUnderlay.placed(image(), canvas)

    /** A tracking whose installation recalled [remembered] for an untouched work and restored nothing. */
    fun known(remembered: RememberedUnderlay?): UnderlayMemoryTracking =
        UnderlayMemoryTracking(1, UnderlayStoreKnowledge.Known(remembered), null)

    fun recollection(underlay: ReferenceUnderlay?): UnderlayRecollection =
        underlay?.let { value -> UnderlayRecollection.Remembered(RememberedUnderlay.of(value)) }
            ?: UnderlayRecollection.Absent

    private fun documentId(value: String): DocumentId =
        when (val result = DocumentId.create(value)) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Test value was rejected: ${result.rejection}")
        }
}
