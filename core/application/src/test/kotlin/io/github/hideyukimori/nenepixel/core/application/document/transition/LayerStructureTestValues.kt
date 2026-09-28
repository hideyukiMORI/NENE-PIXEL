package io.github.hideyukimori.nenepixel.core.application.document.transition

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.blackIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDocumentId
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.greenIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.revision
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.snapshot
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransitionAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.fail

internal object LayerStructureTestValues {
    val smallCanvas: CanvasSize = canvas(2, 1)

    fun layerId(value: Int): LayerId = LayerId.create(value).value()

    fun layerName(value: String): LayerName = LayerName.create(value).value()

    fun layer(
        id: Int,
        name: String,
        snapshot: PixelSnapshot = PixelSnapshot.createEmpty(smallCanvas),
        visibility: LayerVisibility = LayerVisibility.Visible,
    ): Layer = Layer.create(layerId(id), layerName(name), visibility, snapshot)

    /** Bottom `bottom` (black), middle `middle` (red), top `top` (green) on a 2x1 canvas. */
    fun threeLayerState(): DocumentState =
        layeredState(
            listOf(
                layer(1, "bottom", snapshot(smallCanvas, List(2) { blackIndex })),
                layer(2, "middle", snapshot(smallCanvas, List(2) { redIndex })),
                layer(3, "top", snapshot(smallCanvas, List(2) { greenIndex })),
            ),
        )

    fun layeredState(
        layers: List<Layer>,
        revision: Revision = Revision.initial(),
    ): DocumentState = DocumentState.createLayered(defaultDocumentId, revision, defaultDefinition, layers).value()

    fun structural(
        source: DocumentState,
        structure: LayerStructureTransition,
    ): ChangeSet = ChangeSet.createStructural(source, revision(source.revision.value + 1L), structure)

    /** Applies [changeSet] and its inverse, asserts the inverse restores [original], and returns the forward result. */
    fun roundTrip(
        original: DocumentState,
        changeSet: ChangeSet,
    ): DocumentState {
        val forward = created(DocumentTransition.create(original, changeSet))
        val backward = created(DocumentTransition.create(forward.nextState, changeSet.inverse()))
        assertEquals(original, backward.nextState)
        return forward.nextState
    }

    fun assertFullCanvasInvalidation(changeSet: ChangeSet) {
        assertEquals(0, changeSet.renderInvalidation.origin.x.value)
        assertEquals(0, changeSet.renderInvalidation.origin.y.value)
        assertEquals(changeSet.canvas, changeSet.renderInvalidation.size)
    }

    fun <T> DomainValueResult<T>.value(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> fail("Test value was rejected: $rejection")
        }
}
