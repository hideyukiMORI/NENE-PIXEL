package io.github.hideyukimori.nenepixel.presentation.compose.input

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PointerArbitrationTest {
    @Test
    fun `one pointer draws and release ends draw`() {
        assertEquals(
            PointerDirective.Draw,
            drawingFacts(pressedIds = setOf(FIRST_POINTER)).classified(),
        )
        assertEquals(
            PointerDirective.EndDraw,
            drawingFacts(releasedIds = setOf(FIRST_POINTER)).classified(),
        )
    }

    @Test
    fun `second pointer atomically cancels draw and starts transform`() {
        assertEquals(
            PointerDirective.CancelDrawAndBeginTransform,
            drawingFacts(pressedIds = setOf(FIRST_POINTER, SECOND_POINTER)).classified(),
        )
    }

    @Test
    fun `transform applies only to stable membership and surface`() {
        assertEquals(
            PointerDirective.ApplyTransform,
            transformFacts(sameTransformMembership = true).classified(),
        )
        assertEquals(
            PointerDirective.RebaseTransform,
            transformFacts(sameTransformMembership = false).classified(),
        )
        assertEquals(
            PointerDirective.RebaseTransform,
            transformFacts(sameTransformMembership = true, surfaceChanged = true).classified(),
        )
    }

    @Test
    fun `third pointer suppresses until every pointer is up`() {
        assertEquals(
            PointerDirective.Suppress,
            transformFacts(pressedIds = setOf(FIRST_POINTER, SECOND_POINTER, THIRD_POINTER)).classified(),
        )
        assertEquals(
            PointerDirective.Wait,
            suppressedFacts(setOf(FIRST_POINTER)).classified(),
        )
        assertEquals(
            PointerDirective.Finish,
            suppressedFacts(emptySet()).classified(),
        )
    }

    @Test
    fun `transform never resumes drawing after one transform pointer lifts`() {
        assertEquals(
            PointerDirective.Wait,
            transformFacts(pressedIds = setOf(FIRST_POINTER)).classified(),
        )
        assertEquals(
            PointerDirective.Finish,
            transformFacts(pressedIds = emptySet()).classified(),
        )
    }

    private fun drawingFacts(
        pressedIds: Set<Long> = emptySet(),
        releasedIds: Set<Long> = emptySet(),
    ): ClassifiedFacts =
        ClassifiedFacts(
            PointerInteractionMode.Drawing,
            PointerFrameFacts(
                pressedIds = pressedIds,
                releasedIds = releasedIds,
                trackedDrawingId = FIRST_POINTER,
            ),
        )

    private fun transformFacts(
        pressedIds: Set<Long> = setOf(FIRST_POINTER, SECOND_POINTER),
        sameTransformMembership: Boolean = false,
        surfaceChanged: Boolean = false,
    ): ClassifiedFacts =
        ClassifiedFacts(
            PointerInteractionMode.Transforming,
            PointerFrameFacts(
                pressedIds = pressedIds,
                sameTransformMembership = sameTransformMembership,
                surfaceChanged = surfaceChanged,
            ),
        )

    private fun suppressedFacts(pressedIds: Set<Long>): ClassifiedFacts =
        ClassifiedFacts(
            PointerInteractionMode.Suppressed,
            PointerFrameFacts(pressedIds = pressedIds),
        )

    private fun ClassifiedFacts.classified(): PointerDirective = classifyPointerFrame(mode, facts)

    private data class ClassifiedFacts(
        val mode: PointerInteractionMode,
        val facts: PointerFrameFacts,
    )

    private companion object {
        const val FIRST_POINTER: Long = 1L
        const val SECOND_POINTER: Long = 2L
        const val THIRD_POINTER: Long = 3L
    }
}
