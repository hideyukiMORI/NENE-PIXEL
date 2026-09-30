package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandFailure
import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class LayerNoticeKindsTest {
    @Test
    fun `saving or loading maps to busy on both the command and the action side`() {
        assertEquals(LayerNotice.Kind.Busy, layerNoticeKindOf(CommandFailure.PersistenceBusy))
        assertEquals(LayerNotice.Kind.Busy, layerNoticeKindOf(WorkspaceActionRejection.PersistenceBusy))
    }

    @Test
    fun `a hidden active layer maps to the hidden target notice`() {
        val fixture = fixture()
        fixture.controller.callbacks.layers
            .onSetVisibility(LayerId.first(), LayerVisibility.Hidden)
        val size = fixture.runtime.state.documentState.size

        val refused = fixture.runtime.reduce(WorkspaceAction.BeginGesturePreview(size, position(0, 0)))

        val rejection = assertInstanceOf(WorkspaceReductionResult.Rejected::class.java, refused).rejection
        assertInstanceOf(WorkspaceActionRejection.ActiveLayerHidden::class.java, rejection)
        assertEquals(LayerNotice.Kind.HiddenTarget, layerNoticeKindOf(rejection))
    }

    @Test
    fun `no effective change raises no notice and every other refusal is a failure`() {
        assertNull(layerNoticeKindOf(RejectionReason.NoEffectiveChange))
        assertEquals(LayerNotice.Kind.Failed, layerNoticeKindOf(RejectionReason.LastLayerNotDeletable))
        assertEquals(LayerNotice.Kind.Failed, layerNoticeKindOf(RejectionReason.LayerLimitReached))
        assertEquals(LayerNotice.Kind.Failed, layerNoticeKindOf(CommandFailure.PaletteSessionActive))
        assertEquals(LayerNotice.Kind.Failed, layerNoticeKindOf(WorkspaceActionRejection.PaletteSessionActive))
    }
}
