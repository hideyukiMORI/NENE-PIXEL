package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.presentation.compose.R

/**
 * One layer notice (#144 U6): its [kind], a [serial] that tells two equal notices apart, and the document id and
 * revision it was raised against. [target] is the hidden layer a [Kind.HiddenTarget] notice offers to show. It holds
 * no `DocumentState`, so comparing two notices never compares pixels.
 */
internal data class LayerNotice(
    val kind: Kind,
    val serial: Int,
    val documentId: DocumentId,
    val revision: Revision,
    val target: LayerId?,
) {
    /**
     * Whether the notice still describes [document] with [activeLayerId] active: a deletion or a hidden target only
     * until the next revision (and a hidden target only while it stays active), a refusal until the document changes.
     */
    fun isValidFor(
        document: DocumentState,
        activeLayerId: LayerId,
    ): Boolean {
        val sameDocument = documentId == document.id
        val sameRevision = sameDocument && revision == document.revision
        return when (kind) {
            Kind.Deleted -> sameRevision
            Kind.HiddenTarget -> sameRevision && target == activeLayerId
            Kind.Busy, Kind.Failed -> sameDocument
        }
    }

    /** What the notice says, and the action it offers, if any (#144 UI spec "Content"). */
    enum class Kind(
        val message: Int,
        val action: Int?,
    ) {
        Deleted(R.string.layer_notice_deleted, R.string.layer_notice_undo),
        HiddenTarget(R.string.layer_notice_hidden, R.string.layer_notice_show),
        Busy(R.string.layer_notice_busy, null),
        Failed(R.string.layer_notice_failed, null),
    }
}
