package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayRecollection
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction

/**
 * The runtime-lock side of the device memory of underlays (ADR 0034). Nothing here performs I/O or
 * changes the persistence coordination: every transaction returns it unchanged and takes no lease.
 */
internal class RuntimeUnderlayMemoryOperations(
    private val runtime: EditorRuntime,
) {
    /** Starts a recall only while the projection is `RecallPending`. */
    fun beginRecall(): UnderlayRecallStart =
        runtime.read { transaction ->
            val tracking = transaction.underlayMemoryTracking
            val projection = tracking.projection(transaction.workspaceState().underlay)
            if (projection is UnderlayMemoryProjection.RecallPending) {
                UnderlayRecallStart.Start(tracking.installation, transaction.documentId())
            } else {
                UnderlayRecallStart.NotNeeded
            }
        }

    /**
     * Applies a finished recall. While a switch is installing another work nothing changes, and this
     * installation is not recalled again. A restored value is reduced into the workspace once.
     */
    fun completeRecall(
        start: UnderlayRecallStart.Start,
        recollection: UnderlayRecollection,
    ) {
        runtime.transact { transaction ->
            if (!transaction.coordination.activeOperation.isSwitching()) {
                val canvas = transaction.documentState().size
                val step = transaction.underlayMemoryTracking.recallCompleted(start.installation, recollection, canvas)
                transaction.underlayMemoryTracking = step.tracking
                val resolution = step.resolution
                if (resolution is UnderlayRecallResolution.Restore) {
                    transaction.reduceWorkspace(WorkspaceAction.SetReferenceUnderlay(resolution.underlay))
                }
            }
            PersistenceTransition(transaction.coordination, Unit)
        }
    }

    /** What to write now for the installed work; `NotNeeded` when there is nothing. */
    fun beginPublication(mode: UnderlayPublicationMode): UnderlayPublicationStart =
        runtime.read { transaction ->
            val publication =
                transaction.underlayMemoryTracking.publication(
                    transaction.documentId(),
                    transaction.workspaceState().underlay,
                    mode,
                )
            if (publication.writes.isEmpty()) {
                UnderlayPublicationStart.NotNeeded
            } else {
                UnderlayPublicationStart.Start(publication)
            }
        }

    /** Applies a finished [publication], stored or failed alike. */
    fun completePublication(publication: UnderlayPublication) {
        runtime.transact { transaction ->
            transaction.underlayMemoryTracking = transaction.underlayMemoryTracking.publicationCompleted(publication)
            PersistenceTransition(transaction.coordination, Unit)
        }
    }
}
