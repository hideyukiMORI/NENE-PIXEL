package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

internal object P2CandidateCanvasMatrix {
    val shapes: List<P2CanvasShape> =
        listOf(
            P2CanvasShape(64, 64),
            P2CanvasShape(16, 256),
            P2CanvasShape(256, 16),
            P2CanvasShape(128, 128),
            P2CanvasShape(64, 256),
            P2CanvasShape(256, 64),
            P2CanvasShape(256, 256),
        )

    val sparseShapes: List<P2CanvasShape> = shapes.dropLast(1)
    val denseAnchor: P2CanvasShape = shapes.last()
}
