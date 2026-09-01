package io.github.hideyukimori.nenepixel.presentation.compose.input

internal enum class PointerInteractionMode {
    Drawing,
    Transforming,
    Suppressed,
}

internal enum class PointerDirective {
    Draw,
    EndDraw,
    CancelDrawAndBeginTransform,
    ApplyTransform,
    RebaseTransform,
    Wait,
    Suppress,
    Finish,
}

internal data class PointerFrameFacts(
    val pressedIds: Set<Long>,
    val releasedIds: Set<Long> = emptySet(),
    val trackedDrawingId: Long? = null,
    val surfaceChanged: Boolean = false,
    val sameTransformMembership: Boolean = false,
)

internal fun classifyPointerFrame(
    mode: PointerInteractionMode,
    facts: PointerFrameFacts,
): PointerDirective =
    when (mode) {
        PointerInteractionMode.Drawing -> classifyDrawing(facts)
        PointerInteractionMode.Transforming -> classifyTransforming(facts)
        PointerInteractionMode.Suppressed -> classifySuppressed(facts)
    }

private fun classifyDrawing(facts: PointerFrameFacts): PointerDirective =
    when {
        facts.pressedIds.size > POINTER_TRANSFORM_COUNT -> PointerDirective.Suppress
        facts.pressedIds.size == POINTER_TRANSFORM_COUNT -> PointerDirective.CancelDrawAndBeginTransform
        facts.surfaceChanged -> PointerDirective.Suppress
        facts.trackedDrawingId in facts.releasedIds -> PointerDirective.EndDraw
        facts.trackedDrawingId in facts.pressedIds -> PointerDirective.Draw
        else -> PointerDirective.Suppress
    }

private fun classifyTransforming(facts: PointerFrameFacts): PointerDirective =
    when {
        facts.pressedIds.isEmpty() -> PointerDirective.Finish
        facts.pressedIds.size > POINTER_TRANSFORM_COUNT -> PointerDirective.Suppress
        facts.pressedIds.size < POINTER_TRANSFORM_COUNT -> PointerDirective.Wait
        facts.sameTransformMembership && !facts.surfaceChanged -> PointerDirective.ApplyTransform
        else -> PointerDirective.RebaseTransform
    }

private fun classifySuppressed(facts: PointerFrameFacts): PointerDirective =
    if (facts.pressedIds.isEmpty()) PointerDirective.Finish else PointerDirective.Wait

private const val POINTER_TRANSFORM_COUNT: Int = 2
