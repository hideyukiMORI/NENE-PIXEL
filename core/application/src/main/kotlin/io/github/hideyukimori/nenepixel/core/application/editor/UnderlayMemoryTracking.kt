package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayStoreKnowledge.Known
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayStoreKnowledge.Unknown
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayStoreKnowledge.UnknownTouched
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryToken
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayRecollection
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayInteraction
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize

/**
 * Pure bookkeeping of the device memory of underlays (ADR 0034, Coordination): which installation
 * is current, what the store is known to hold for it, and at most one departing capture. Every
 * function returns a new value; the runtime holds it under its lock and performs no I/O with it.
 */
internal data class UnderlayMemoryTracking(
    val installation: Long,
    val store: UnderlayStoreKnowledge,
    val departing: DepartingUnderlay?,
) {
    /**
     * Captures the departing work's unpublished underlay, adjusting or not, replacing an older
     * capture; an unknown, untouched store captures nothing. Then the store becomes unknown.
     */
    fun installed(
        departingDocument: DocumentId,
        departingUnderlay: ReferenceUnderlay?,
    ): UnderlayMemoryTracking {
        val departingValue = remembered(departingUnderlay)
        val captures =
            when (store) {
                Unknown -> false
                UnknownTouched -> true
                is Known -> departingValue != store.remembered
            }
        val capture = if (captures) DepartingUnderlay(departingDocument, departingValue) else departing
        return UnderlayMemoryTracking(installation + 1, Unknown, capture)
    }

    /** Records that an underlay action was reduced while the store is unknown. */
    fun underlayReduced(): UnderlayMemoryTracking = if (store == Unknown) copy(store = UnknownTouched) else this

    fun projection(workspace: ReferenceUnderlay?): UnderlayMemoryProjection =
        when (store) {
            Unknown, UnknownTouched -> UnderlayMemoryProjection.RecallPending(UnderlayMemoryToken.recall(installation))
            is Known -> knownProjection(store, workspace)
        }

    /**
     * Applies a recall for [installation]. A stale or repeated completion is dropped. A remembered
     * value of an untouched installation is restored against [canvas]; the store then holds the
     * restored value, so a clamp does not publish it again. Otherwise the workspace is kept.
     */
    fun recallCompleted(
        installation: Long,
        recollection: UnderlayRecollection,
        canvas: CanvasSize,
    ): UnderlayRecallStep {
        val remembered = recalled(recollection)
        val stale = installation != this.installation || store is Known
        return when {
            stale -> UnderlayRecallStep(this, UnderlayRecallResolution.Dropped)
            store == Unknown && remembered != null -> restored(remembered.toUnderlay(canvas))
            else -> UnderlayRecallStep(copy(store = Known(remembered)), UnderlayRecallResolution.Keep)
        }
    }

    /**
     * What to write now for [document], the installed work: the departing capture first, then the
     * current value when it differs from the store and is resting or [mode] is a flush. Nothing is
     * written while the store is unknown.
     */
    fun publication(
        document: DocumentId,
        workspace: ReferenceUnderlay?,
        mode: UnderlayPublicationMode,
    ): UnderlayPublication =
        when (store) {
            Unknown, UnknownTouched -> UnderlayPublication(installation, null, null)
            is Known -> UnderlayPublication(installation, departing, currentWrite(document, store, workspace, mode))
        }

    /**
     * Applies a finished [publication], stored or failed alike. The departing capture is cleared when
     * the publication wrote this same instance. A written current value becomes the known one while
     * its installation is current, so a failed value is not requested again until it changes.
     */
    fun publicationCompleted(publication: UnderlayPublication): UnderlayMemoryTracking {
        val written = publication.departing
        val remainingDeparting = if (written != null && written === departing) null else departing
        val current = publication.current
        val currentInstallation = publication.installation == installation && store is Known
        val knownStore = if (current != null && currentInstallation) Known(current.remembered) else store
        return UnderlayMemoryTracking(installation, knownStore, remainingDeparting)
    }

    private fun knownProjection(
        known: Known,
        workspace: ReferenceUnderlay?,
    ): UnderlayMemoryProjection {
        val current = remembered(workspace)
        val resting = workspace?.interaction != UnderlayInteraction.Adjusting
        val token =
            if (departing != null) {
                UnderlayMemoryToken.departingPublication(installation, current)
            } else {
                UnderlayMemoryToken.publication(installation, current)
            }
        val pending = departing != null || (current != known.remembered && resting)
        return if (pending) UnderlayMemoryProjection.PublishPending(token) else UnderlayMemoryProjection.Settled
    }

    private fun currentWrite(
        document: DocumentId,
        known: Known,
        workspace: ReferenceUnderlay?,
        mode: UnderlayPublicationMode,
    ): UnderlayMemoryWrite? {
        val current = remembered(workspace)
        val writable =
            when (mode) {
                UnderlayPublicationMode.Publish -> workspace?.interaction != UnderlayInteraction.Adjusting
                UnderlayPublicationMode.Flush -> true
            }
        return if (current != known.remembered && writable) UnderlayMemoryWrite.of(document, current) else null
    }

    private fun restored(underlay: ReferenceUnderlay): UnderlayRecallStep =
        UnderlayRecallStep(
            copy(store = Known(RememberedUnderlay.of(underlay))),
            UnderlayRecallResolution.Restore(underlay),
        )

    private fun recalled(recollection: UnderlayRecollection): RememberedUnderlay? =
        when (recollection) {
            is UnderlayRecollection.Remembered -> recollection.underlay
            UnderlayRecollection.Absent -> null
        }

    private fun remembered(underlay: ReferenceUnderlay?): RememberedUnderlay? = underlay?.let(RememberedUnderlay::of)

    companion object {
        /** The blank document of a fresh process: the store is known to hold nothing; no recall. */
        fun initial(): UnderlayMemoryTracking = UnderlayMemoryTracking(0, Known(null), null)
    }
}
