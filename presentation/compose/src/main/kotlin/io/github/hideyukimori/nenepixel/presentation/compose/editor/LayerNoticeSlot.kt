package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId

/**
 * The current layer notice and its serial counter (#144 U6), owned by [EditorRuntimeAdapter]. The layer route and
 * the canvas route record their results here; the adapter reads [validFor] each time it builds a render state, which
 * drops a notice the document has moved past.
 */
internal class LayerNoticeSlot {
    private var current: LayerNotice? = null
    private var lastSerial: Int = 0

    /** A layer command ran on [document]: a deletion raises its notice, other successes clear, refusals raise. */
    fun recordCommand(
        result: CommandResult,
        deletion: Boolean,
        document: DocumentState,
    ) {
        if (result is CommandResult.Applied) {
            current = if (deletion) raised(LayerNotice.Kind.Deleted, document, null) else null
        } else {
            layerNoticeKindOf(result)?.let { kind -> current = raised(kind, document, null) }
        }
    }

    /** `SelectLayer` was reduced against [document]: a change clears the notice, a refusal raises one. */
    fun recordSelection(
        result: WorkspaceReductionResult,
        document: DocumentState,
    ) {
        current =
            when (result) {
                is WorkspaceReductionResult.Reduced -> null
                is WorkspaceReductionResult.Rejected -> raised(layerNoticeKindOf(result.rejection), document, null)
                is WorkspaceReductionResult.Unchanged, is WorkspaceReductionResult.CommitPrepared -> current
            }
    }

    /** A canvas action was refused on [document]; only a hidden active layer raises a notice. */
    fun recordCanvasRejection(
        rejection: WorkspaceActionRejection,
        document: DocumentState,
    ) {
        if (rejection is WorkspaceActionRejection.ActiveLayerHidden) {
            current = raised(LayerNotice.Kind.HiddenTarget, document, rejection.layerId)
        }
    }

    /** Clears the notice numbered [serial] once the UI has shown it; returns whether it was still the current one. */
    fun settle(serial: Int): Boolean {
        val matches = current?.serial == serial
        if (matches) {
            current = null
        }
        return matches
    }

    /** The current notice if it still holds for [document] with [activeLayerId] active; otherwise drops it. */
    fun validFor(
        document: DocumentState,
        activeLayerId: LayerId,
    ): LayerNotice? {
        if (current?.isValidFor(document, activeLayerId) == false) {
            current = null
        }
        return current
    }

    private fun raised(
        kind: LayerNotice.Kind,
        document: DocumentState,
        target: LayerId?,
    ): LayerNotice {
        lastSerial += 1
        return LayerNotice(kind, lastSerial, document.id, document.revision, target)
    }
}
