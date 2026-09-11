package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportOutcome

internal class RuntimePngExportOperations(
    private val runtime: EditorRuntime,
) {
    fun begin(): DocumentOutputStart =
        runtime.transact { transaction ->
            PngExportTransitions.begin(transaction.coordination, transaction.documentState())
        }

    fun complete(
        handle: PersistenceOperationHandle,
        outcome: PngExportOutcome,
    ): PersistenceRequestResult =
        runtime.transact { transaction -> PngExportTransitions.complete(transaction.coordination, handle, outcome) }

    fun cancel(handle: PersistenceOperationHandle): PersistenceRequestResult =
        runtime.transact { transaction ->
            CancellationTransitions.completeCancellation(transaction.coordination, handle)
        }
}
