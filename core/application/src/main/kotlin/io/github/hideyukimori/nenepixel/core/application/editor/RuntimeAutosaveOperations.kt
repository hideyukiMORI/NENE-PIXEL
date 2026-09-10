package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryPublicationOutcome

internal class RuntimeAutosaveOperations(
    private val runtime: EditorRuntime,
) {
    fun beginPublication(): AutosaveStart =
        runtime.transact { transaction -> AutosaveTransitions.begin(transaction.coordination) }

    fun completePublication(
        handle: PersistenceOperationHandle,
        outcome: RecoveryPublicationOutcome,
    ): AutosaveRequestResult =
        runtime.transact { transaction ->
            AutosaveTransitions.complete(transaction.coordination, handle, outcome)
        }

    fun releasePublication(handle: PersistenceOperationHandle) {
        runtime.transact { transaction -> AutosaveTransitions.release(transaction.coordination, handle) }
    }

    fun activePublication(): PersistenceOperationHandle? =
        runtime.read { transaction ->
            (transaction.coordination.activeOperation as? ActivePersistenceOperation.Autosave)?.handle
        }
}
