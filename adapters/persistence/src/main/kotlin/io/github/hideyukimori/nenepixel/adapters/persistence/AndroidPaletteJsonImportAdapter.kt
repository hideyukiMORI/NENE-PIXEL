package io.github.hideyukimori.nenepixel.adapters.persistence

import android.content.ContentResolver
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportPort
import io.github.hideyukimori.nenepixel.core.projectformat.palette.PaletteJsonBytes
import io.github.hideyukimori.nenepixel.core.projectformat.palette.PaletteJsonCodec
import io.github.hideyukimori.nenepixel.core.projectformat.palette.PaletteJsonResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

public class AndroidPaletteJsonImportAdapter private constructor(
    private val reader: ProjectDocumentReader,
    private val picker: ProjectPickerAccess,
    private val ioDispatcher: CoroutineDispatcher,
) : PaletteJsonImportPort {
    override suspend fun import(): PaletteJsonImportOutcome =
        when (val result = picker.openDocument(DocumentOpenRequest(DocumentOutputFormat.PALETTE_JSON))) {
            is InternalPickerResult.Selected -> importSelected(result.location)
            InternalPickerResult.Cancelled -> PaletteJsonImportOutcome.Cancelled
            is InternalPickerResult.Failed -> PaletteJsonImportOutcome.Failed(result.failure)
        }

    private suspend fun importSelected(location: ProjectLocation): PaletteJsonImportOutcome =
        withContext(ioDispatcher) {
            when (val read = reader.read(location, ProjectReadPurpose.SOURCE)) {
                is ProjectReadResult.Bytes -> decodeImported(read.value)
                is ProjectReadResult.Failed -> PaletteJsonImportOutcome.Failed(read.failure)
            }
        }

    private fun decodeImported(bytes: ByteArray): PaletteJsonImportOutcome =
        when (val carrier = PaletteJsonBytes.create(bytes)) {
            is PaletteJsonResult.Accepted -> {
                when (val decoded = PaletteJsonCodec.decode(carrier.value)) {
                    is PaletteJsonResult.Accepted -> {
                        PaletteJsonImportOutcome.Imported(decoded.value)
                    }

                    is PaletteJsonResult.Rejected -> {
                        PaletteJsonImportOutcome.Failed(PaletteJsonRejectionMapper.map(decoded.rejection))
                    }
                }
            }

            is PaletteJsonResult.Rejected -> {
                PaletteJsonImportOutcome.Failed(PaletteJsonRejectionMapper.map(carrier.rejection))
            }
        }

    public companion object {
        public fun create(
            contentResolver: ContentResolver,
            picker: ProjectDocumentPicker,
            ioDispatcher: CoroutineDispatcher,
        ): PaletteJsonImportPort =
            create(
                ContentResolverProjectContentAccess(contentResolver),
                AndroidProjectPickerAccess(picker),
                ioDispatcher,
            )

        internal fun create(
            content: ProjectContentAccess,
            picker: ProjectPickerAccess,
            ioDispatcher: CoroutineDispatcher,
        ): PaletteJsonImportPort =
            AndroidPaletteJsonImportAdapter(
                ProjectDocumentReader(content, PaletteJsonBytes.MAX_PROBE_BYTE_COUNT),
                picker,
                ioDispatcher,
            )
    }
}
