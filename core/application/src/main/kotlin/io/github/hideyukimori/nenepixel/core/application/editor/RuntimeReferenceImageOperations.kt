package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImageOutcome
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay

/** Runs the reference-image pick on the shared document-output lease (ADR 0032). */
internal class RuntimeReferenceImageOperations(
    private val runtime: EditorRuntime,
) {
    fun beginPick(): ReferenceImagePickStart =
        runtime.transact { transaction ->
            val lease = DocumentOutputTransitions.begin(transaction.coordination)
            val start =
                when (val result = lease.result) {
                    is DocumentOutputLease.Started -> {
                        ReferenceImagePickStart.Started(result.handle)
                    }

                    DocumentOutputLease.Busy -> {
                        ReferenceImagePickStart.Busy
                    }

                    DocumentOutputLease.RecoveryUnavailable -> {
                        ReferenceImagePickStart.RecoveryUnavailable
                    }

                    DocumentOutputLease.IdentityExhausted -> {
                        ReferenceImagePickStart.IdentityExhausted
                    }
                }
            PersistenceTransition(lease.next, start, lease.effect)
        }

    /**
     * Completes the pick lease. An accepted `Picked` outcome replaces the underlay with the image fitted
     * to the current document, inside the same transaction; a stale or cancelling completion leaves the
     * workspace untouched.
     */
    fun completePick(
        handle: PersistenceOperationHandle,
        outcome: ReferenceImageOutcome,
    ): PersistenceRequestResult =
        runtime.transact { transaction ->
            val completion =
                DocumentOutputTransitions.complete(transaction.coordination, handle, outcome.toLastOutcome())
            val accepted = (completion.result as? PersistenceRequestResult.Completed)?.outcome
            val picked = outcome as? ReferenceImageOutcome.Picked
            if (picked != null && accepted == PersistenceLastOutcome.ReferenceImagePicked) {
                val underlay = ReferenceUnderlay.placed(picked.image, transaction.documentState().size)
                transaction.reduceWorkspace(WorkspaceAction.SetReferenceUnderlay(underlay))
            }
            completion
        }

    fun cancelPick(handle: PersistenceOperationHandle): PersistenceRequestResult =
        runtime.transact { transaction ->
            CancellationTransitions.completeCancellation(transaction.coordination, handle)
        }

    private fun ReferenceImageOutcome.toLastOutcome(): PersistenceLastOutcome =
        when (this) {
            is ReferenceImageOutcome.Picked -> {
                PersistenceLastOutcome.ReferenceImagePicked
            }

            ReferenceImageOutcome.Cancelled -> {
                PersistenceLastOutcome.Cancelled
            }

            is ReferenceImageOutcome.Rejected -> {
                PersistenceLastOutcome.Failed(PersistenceFailure.ReferenceImageRejected(reason))
            }

            is ReferenceImageOutcome.Failed -> {
                PersistenceLastOutcome.Failed(PersistenceFailure.ReferenceImagePick(failure))
            }
        }
}
