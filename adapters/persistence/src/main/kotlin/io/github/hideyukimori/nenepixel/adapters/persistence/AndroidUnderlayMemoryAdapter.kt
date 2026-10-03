package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryPort
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayRecollection
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Remembers the reference underlay of each work in the files of one directory (ADR 0034).
 *
 * Every call runs on the injected dispatcher and holds one mutex, so the store never sees two calls at once.
 * File and permission failures are answered here once: [recall] as [UnderlayRecollection.Absent], [remember]
 * and [forget] as [UnderlayMemoryOutcome.Failed]. Running out of memory is answered the same way: while [recall]
 * reads and decodes a record, as [UnderlayRecollection.Absent] with the record kept; while [remember] encodes
 * one, as [UnderlayMemoryOutcome.Failed].
 */
public class AndroidUnderlayMemoryAdapter internal constructor(
    private val store: UnderlayMemoryStore,
    private val ioDispatcher: CoroutineDispatcher,
) : UnderlayMemoryPort {
    private val mutex: Mutex = Mutex()

    override suspend fun recall(document: DocumentId): UnderlayRecollection =
        serialized {
            try {
                store.recall(document)
            } catch (_: IOException) {
                UnderlayRecollection.Absent
            } catch (_: SecurityException) {
                UnderlayRecollection.Absent
            } catch (_: OutOfMemoryError) {
                // The read buffer and the decoded pixels are allocated here; the record is kept for a later recall.
                UnderlayRecollection.Absent
            }
        }

    override suspend fun remember(
        document: DocumentId,
        underlay: RememberedUnderlay,
    ): UnderlayMemoryOutcome =
        serialized {
            try {
                stored { store.remember(document, underlay) }
            } catch (_: OutOfMemoryError) {
                // The image record is encoded here; the decoders map the same failure to a typed result.
                UnderlayMemoryOutcome.Failed
            }
        }

    override suspend fun forget(document: DocumentId): UnderlayMemoryOutcome =
        serialized { stored { store.forget(document) } }

    private suspend fun <T> serialized(operation: () -> T): T =
        withContext(ioDispatcher) {
            mutex.withLock { operation() }
        }

    private fun stored(operation: () -> Unit): UnderlayMemoryOutcome =
        try {
            operation()
            UnderlayMemoryOutcome.Stored
        } catch (_: IOException) {
            UnderlayMemoryOutcome.Failed
        } catch (_: SecurityException) {
            UnderlayMemoryOutcome.Failed
        }

    public companion object {
        /**
         * Creates the adapter under [noBackupDirectory], the application's no-backup files directory. The records
         * live only in its `reference-underlays` child, which the adapter names and creates on the first write
         * (ADR 0034). Blocking file work runs on [ioDispatcher].
         */
        public fun create(
            noBackupDirectory: File,
            ioDispatcher: CoroutineDispatcher,
        ): UnderlayMemoryPort {
            val files = AndroidUnderlayRecordFiles(File(noBackupDirectory, UnderlayMemoryLayout.DIRECTORY_NAME))
            return AndroidUnderlayMemoryAdapter(UnderlayMemoryStore(files), ioDispatcher)
        }
    }
}
