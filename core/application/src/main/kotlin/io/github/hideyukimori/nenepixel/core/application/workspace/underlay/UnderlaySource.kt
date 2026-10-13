package io.github.hideyukimori.nenepixel.core.application.workspace.underlay

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize

/** The immutable image and canvas against which underlay placement is defined. */
internal class UnderlaySource(
    val image: ReferenceImage,
    val canvas: CanvasSize,
)
