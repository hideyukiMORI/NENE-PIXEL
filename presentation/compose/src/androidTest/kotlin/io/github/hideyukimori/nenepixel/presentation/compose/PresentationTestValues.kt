package io.github.hideyukimori.nenepixel.presentation.compose

import io.github.hideyukimori.nenepixel.core.application.editor.DocumentIdSource
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReducer
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceState
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.editor.EditorController

internal object PresentationTestValues {
    val red: PixelColor = color(255, 0, 0)
    val green: PixelColor = color(0, 255, 0)
    val transparent: PixelColor = PixelColor.blank

    fun canvas(
        width: Int,
        height: Int,
    ): CanvasSize =
        CanvasSize.create(CanvasWidth.create(width).requiredValue(), CanvasHeight.create(height).requiredValue())

    fun fixture(
        canvas: CanvasSize = canvas(4, 4),
        paletteColors: List<PixelColor> = defaultPaletteColors(),
    ): EditorFixture {
        val palette = Palette.create(paletteColors).requiredValue()
        val definition = PaletteDefinition.create(palette, paletteIndex(defaultIndex(paletteColors))).requiredValue()
        val runtime = EditorRuntime.create(canvas, definition, TestDocumentIdSource())
        val state = runtime.state
        return EditorFixture(
            initialDocument = state.documentState,
            runtime = runtime,
            reducer = WorkspaceReducer.create(),
            initialWorkspace = state.workspaceState,
            controller = EditorController.create(runtime),
        )
    }

    private fun defaultPaletteColors(): List<PixelColor> = listOf(red, green, red, red, red, red, red, red, transparent)

    private fun defaultIndex(colors: List<PixelColor>): Int = if (colors.size > 8) 8 else 0

    private fun paletteIndex(value: Int): PaletteIndex = PaletteIndex.create(value).requiredValue()

    private fun color(
        red: Int,
        green: Int,
        blue: Int,
    ): PixelColor =
        PixelColor.create(
            ColorChannel.create(red).requiredValue(),
            ColorChannel.create(green).requiredValue(),
            ColorChannel.create(blue).requiredValue(),
            ColorChannel.create(255).requiredValue(),
        )
}

internal data class EditorFixture(
    val initialDocument: DocumentState,
    val runtime: EditorRuntime,
    val reducer: WorkspaceReducer,
    val initialWorkspace: WorkspaceState,
    val controller: EditorController,
)

private fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("Invalid Android test fixture: $rejection")
    }

private class TestDocumentIdSource : DocumentIdSource {
    private var nextValue: Int = 1

    override fun nextDocumentId(): DocumentId {
        val value = nextValue.coerceAtMost(9).toString().repeat(DOCUMENT_ID_LENGTH)
        nextValue += 1
        return DocumentId.create(value).requiredValue()
    }

    private companion object {
        const val DOCUMENT_ID_LENGTH: Int = 32
    }
}
