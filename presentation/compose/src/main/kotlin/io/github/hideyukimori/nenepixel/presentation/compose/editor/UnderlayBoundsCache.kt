package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayPlacement
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize

/**
 * The last inputs of [underlayBounds] and its result (QLT-019, Issue #170). A frame whose document
 * rectangle, canvas size, image identity and placement match the last call gets the stored result
 * back without computing or allocating; any change computes once. Plain Kotlin, so the reuse is
 * checked on the JVM.
 */
internal class UnderlayBoundsCache {
    /** How many times [underlayBounds] has run; read by tests. */
    var computations: Int = 0
        private set

    private val document = FloatArray(EDGE_COUNT)
    private var canvas: CanvasSize? = null
    private var image: ReferenceImage? = null
    private var placement: UnderlayPlacement? = null
    private var shown: UnderlayBounds? = null

    /**
     * Where [image] at [placement] lands inside the document rectangle [edges] (indexed by [LEFT],
     * [TOP], [RIGHT] and [BOTTOM]) of [canvas].
     */
    fun resolve(
        edges: FloatArray,
        canvas: CanvasSize,
        image: ReferenceImage,
        placement: UnderlayPlacement,
    ): UnderlayBounds {
        val stored = shown
        val unchanged =
            image === this.image &&
                canvas == this.canvas &&
                placement == this.placement &&
                edges.contentEquals(document)
        return if (unchanged && stored != null) {
            stored
        } else {
            edges.copyInto(document)
            this.canvas = canvas
            this.image = image
            this.placement = placement
            computations++
            val bounds = UnderlayBounds(edges[LEFT], edges[TOP], edges[RIGHT], edges[BOTTOM])
            underlayBounds(bounds, canvas, image, placement).also { shown = it }
        }
    }

    /** Drops the stored inputs and result, so the image is no longer held and the next call computes. */
    fun forget() {
        canvas = null
        image = null
        placement = null
        shown = null
    }

    companion object {
        const val LEFT: Int = 0
        const val TOP: Int = 1
        const val RIGHT: Int = 2
        const val BOTTOM: Int = 3
        const val EDGE_COUNT: Int = 4
    }
}
