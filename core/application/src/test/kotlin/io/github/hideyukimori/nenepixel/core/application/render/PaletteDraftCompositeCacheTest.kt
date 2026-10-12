package io.github.hideyukimori.nenepixel.core.application.render

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.definition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.paletteIndex
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.begin
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.drawnRuntime
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.edit
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.green
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.opaqueBlue
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.reduce
import io.github.hideyukimori.nenepixel.core.application.render.PaletteDraftCompositeFixture.sourceDefinition
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftOperation
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class PaletteDraftCompositeCacheTest {
    @Test
    fun `ordinary committed rendering never prepares palette dependencies`() {
        val cache = PaletteDraftCompositeCache()
        val runtime = drawnRuntime()
        val first = render(cache, runtime)
        assertSame(first, render(cache, runtime))
        val next = drawnRuntime()
        assertNotSame(first, render(cache, next))
        assertEquals(0, cache.preparationCount)
        assertEquals(0, cache.tableDerivationCount)
    }

    @Test
    fun `begin reorder and finish with the same effective table reuse image`() {
        val runtime = drawnRuntime()
        val cache = PaletteDraftCompositeCache()
        val original = cache.render(runtime.state.documentState, sourceDefinition, null)
        begin(runtime)
        assertSame(original, render(cache, runtime))
        assertEquals(1, cache.preparationCount)
        assertEquals(1, cache.tableDerivationCount)
        assertSame(original, render(cache, runtime))
        assertEquals(1, cache.tableDerivationCount, "An unchanged draw derives no remap or colour table")
        reduce(
            runtime,
            edit(
                PaletteDraftOperation.Reorder(
                    listOf(paletteIndex(2), paletteIndex(1), paletteIndex(0), paletteIndex(3)),
                ),
            ),
        )
        assertSame(original, render(cache, runtime), "Remapped reorder leaves the effective table identical")
        reduce(runtime, WorkspaceAction.BeginPaletteColorEdit(paletteIndex(0)))
        assertSame(original, render(cache, runtime))
        reduce(runtime, WorkspaceAction.UpdatePaletteColorEdit(opaqueBlue))
        val changed = render(cache, runtime)
        assertNotSame(original, changed)
        assertCanonical(cache, runtime)
        reduce(runtime, WorkspaceAction.FinishPaletteColorEdit)
        assertSame(changed, render(cache, runtime), "Finishing changes history metadata only")
        assertEquals(1, cache.preparationCount)
    }

    @Test
    fun `removal recolour and Undo Redo preserve canonical applied-picture mapping`() {
        val runtime = drawnRuntime()
        val cache = PaletteDraftCompositeCache()
        begin(runtime)
        reduce(runtime, edit(PaletteDraftOperation.SetSlotColor(paletteIndex(1), green)))
        assertCanonical(cache, runtime)
        reduce(runtime, edit(PaletteDraftOperation.RemoveSlot(paletteIndex(1), paletteIndex(0))))
        assertCanonical(cache, runtime)
        reduce(runtime, WorkspaceAction.UndoPaletteDraft)
        assertCanonical(cache, runtime)
        reduce(runtime, WorkspaceAction.RedoPaletteDraft)
        assertCanonical(cache, runtime)
    }

    @Test
    fun `mismatched session uses committed table and changed document invalidates image`() {
        val runtime = drawnRuntime()
        begin(runtime)
        val cache = PaletteDraftCompositeCache()
        val session = requireNotNull(runtime.state.workspaceState.paletteEditSession)
        val otherDefinition = definition(paletteIndex(0), green, opaqueBlue, green, opaqueBlue)
        val another = drawnRuntime(otherDefinition)
        val document = another.state.documentState
        val fallback = cache.render(document, document.definition, session)
        assertArrayEquals(DocumentComposite.render(document).copyPackedRgba8888(), fallback.copyPackedRgba8888())
        assertEquals(0, cache.preparationCount)
        val replaced = drawnRuntime(otherDefinition).state.documentState
        assertNotSame(fallback, cache.render(replaced, replaced.definition, session))
    }

    @Test
    fun `document replacement during a compatible session prepares new snapshot sources`() {
        val runtime = drawnRuntime()
        val cache = PaletteDraftCompositeCache()
        begin(runtime)
        val first = render(cache, runtime)
        val session = requireNotNull(runtime.state.workspaceState.paletteEditSession)
        val replacement = drawnRuntime().state.documentState
        val next = cache.render(replacement, replacement.definition, session)
        assertNotSame(first, next)
        assertEquals(2, cache.preparationCount)
        assertArrayEquals(DocumentComposite.render(replacement).copyPackedRgba8888(), next.copyPackedRgba8888())
    }

    @Test
    fun `closing session drops workspace and reopening prepares once without changing image`() {
        val runtime = drawnRuntime()
        val cache = PaletteDraftCompositeCache()
        val first = render(cache, runtime)
        begin(runtime)
        assertSame(first, render(cache, runtime))
        reduce(runtime, WorkspaceAction.CancelPaletteEdit)
        assertSame(first, render(cache, runtime))
        begin(runtime)
        assertSame(first, render(cache, runtime))
        assertEquals(2, cache.preparationCount)
    }

    private fun render(
        cache: PaletteDraftCompositeCache,
        runtime: EditorRuntime,
    ): DocumentCompositeImage =
        cache.render(
            runtime.state.documentState,
            runtime.state.documentState.definition,
            runtime.state.workspaceState.paletteEditSession,
        )

    private fun assertCanonical(
        cache: PaletteDraftCompositeCache,
        runtime: EditorRuntime,
    ) {
        assertArrayEquals(
            PaletteDraftCompositeFixture.draftPixels(runtime),
            render(cache, runtime).copyPackedRgba8888(),
        )
    }
}
