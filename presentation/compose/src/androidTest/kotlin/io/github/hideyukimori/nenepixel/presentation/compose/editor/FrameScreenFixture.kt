package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.presentation.compose.EditorFixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues

/** A library-only editor; never reads the installed app's document or storage. */
internal object FrameScreenFixture {
    fun twoPaintedFrames(): EditorFixture {
        val fixture = PresentationTestValues.fixture()
        paint(fixture, 0)
        fixture.controller.callbacks.frames
            .onAdd(fixture.controller.renderState.activeFrameId)
        paint(fixture, 1)
        return fixture
    }

    fun show(
        rule: ComposeContentTestRule,
        controller: EditorController,
        height: Dp = 600.dp,
    ) {
        rule.setContent {
            Box(Modifier.requiredSize(600.dp, height).consumeWindowInsets(WindowInsets.safeDrawing)) {
                TestNenePixelEditor(controller, Modifier.requiredSize(600.dp, height))
            }
        }
        rule.waitForIdle()
    }

    fun click(
        rule: ComposeContentTestRule,
        tag: String,
    ) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun paint(
        fixture: EditorFixture,
        index: Int,
    ) {
        val state = fixture.runtime.state
        val stroke =
            Stroke
                .create(
                    state.documentState.size,
                    listOf(FrameTestValues.position(0, 0)),
                    StrokeEffect.Paint(PaletteIndex.create(index).frameValue()),
                ).frameValue()
        fixture.runtime.execute(
            ApplyStrokeCommand.create(
                fixture.runtime.captureSource(),
                state.workspaceState.activeLayerId,
                stroke,
            ),
        )
        fixture.controller.synchronizeWithRuntime()
    }
}
