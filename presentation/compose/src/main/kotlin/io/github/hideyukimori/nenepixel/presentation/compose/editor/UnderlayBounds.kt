package io.github.hideyukimori.nenepixel.presentation.compose.editor

/**
 * A rectangle on the drawing surface in surface pixels (ADR 0032, Issue #170): the document
 * rectangle going into [underlayBounds] and the shown underlay coming out. A plain value, so the
 * placement formula is checked on the JVM without `RectF`.
 */
internal data class UnderlayBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)
