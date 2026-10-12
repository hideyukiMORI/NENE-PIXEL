package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.Bitmap
import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/** Rendering-only device boundary: library fixture, never the installed app's document or storage. */
internal class CommittedPaletteBitmapCacheTest {
    @Test
    fun colourGestureMetadataReusesBitmapAndChangedOutputPublishesOnce() {
        val runtime = paintedRuntime()
        val cache = CommittedBitmapCache()
        val first = render(cache, runtime)
        runtime.paletteOperations.beginPaletteEdit()
        assertSame(first, render(cache, runtime))
        runtime.reduce(WorkspaceAction.BeginPaletteColorEdit(PaletteIndex.first))
        assertSame(first, render(cache, runtime))
        runtime.reduce(WorkspaceAction.UpdatePaletteColorEdit(PresentationTestValues.green))
        val changed = render(cache, runtime)
        assertNotSame(first, changed)
        assertEquals(OPAQUE_GREEN, changed.getPixel(0, 0))
        runtime.reduce(WorkspaceAction.FinishPaletteColorEdit)
        assertSame(changed, render(cache, runtime))
        runtime.reduce(WorkspaceAction.BeginPaletteColorEdit(PaletteIndex.first))
        assertSame(changed, render(cache, runtime))
        runtime.reduce(WorkspaceAction.CancelPaletteColorEdit)
        assertSame(changed, render(cache, runtime))
    }

    private fun paintedRuntime(): EditorRuntime {
        val runtime = PresentationTestValues.fixture().runtime
        val position = PixelPosition.create(PixelX.create(0).value(), PixelY.create(0).value())
        val stroke =
            Stroke
                .create(
                    runtime.state.documentState.size,
                    listOf(position),
                    StrokeEffect.Paint(PaletteIndex.first),
                ).value()
        runtime.execute(ApplyStrokeCommand.create(runtime.captureSource(), LayerId.first(), stroke))
        return runtime
    }

    private fun render(
        cache: CommittedBitmapCache,
        runtime: EditorRuntime,
    ): Bitmap =
        cache.render(
            runtime.state.documentState,
            runtime.state.workspaceState.activeFrameId,
            runtime.state.documentState.definition,
            runtime.state.workspaceState.paletteEditSession,
        )

    private fun <T> DomainValueResult<T>.value(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> error("Invalid bitmap fixture: $rejection")
        }

    private companion object {
        const val OPAQUE_GREEN: Int = 0xFF00FF00.toInt()
    }
}
