package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/** Runtime-internal workspace follow-ups to a document install or transition; never dispatched by presentation. */
internal sealed interface DocumentReconciliation : WorkspaceAction

/** Moves the active layer; the runtime chooses [layerId] (ADR 0030). */
internal data class ReconcileDocumentLayer(
    val layerId: LayerId,
) : DocumentReconciliation

internal fun reconcileDocument(
    state: WorkspaceState,
    action: DocumentReconciliation,
    document: DocumentState,
): WorkspaceReductionResult =
    when (action) {
        is ReconcileDocumentPalette -> reconcileDocumentPalette(state, action, document.definition.palette)
        is ReconcileDocumentLayer -> reconcileDocumentLayer(state, action, document)
    }

/**
 * Installs the reconciled recent slots, closes the quick-select menu and disarms the eyedropper (ADR 0029).
 */
private fun reconcileDocumentPalette(
    state: WorkspaceState,
    action: ReconcileDocumentPalette,
    palette: Palette,
): WorkspaceReductionResult =
    when (val entry = palette.entryAt(action.index)) {
        is DomainValueResult.Created -> {
            WorkspaceReductionResult.Reduced(
                state
                    .withEditTarget(state.editTarget.withPaletteIndex(action.index))
                    .withPreview(null)
                    .withQuickSelection(
                        state.quickSelection
                            .withRecent(action.recent)
                            .closed()
                            .idle(),
                    ),
            )
        }

        is DomainValueResult.Rejected -> {
            error("Document transition produced an invalid selection: ${entry.rejection}")
        }
    }

private fun reconcileDocumentLayer(
    state: WorkspaceState,
    action: ReconcileDocumentLayer,
    document: DocumentState,
): WorkspaceReductionResult =
    if (document.layers.none { it.id == action.layerId }) {
        error("Document transition produced an invalid active layer: ${action.layerId}")
    } else {
        WorkspaceReductionResult.Reduced(state.withEditTarget(state.editTarget.withLayer(action.layerId)))
    }
