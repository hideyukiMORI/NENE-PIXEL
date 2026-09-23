package io.github.hideyukimori.nenepixel.adapters.persistence

import android.content.ContentResolver
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportPort
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.projectformat.palette.PaletteJsonBytes
import io.github.hideyukimori.nenepixel.core.projectformat.palette.PaletteJsonCodec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

public class AndroidPaletteJsonExportAdapter private constructor(
    private val output: FreshDocumentOutput,
    private val ioDispatcher: CoroutineDispatcher,
) : PaletteJsonExportPort {
    override suspend fun export(definition: PaletteDefinition): PaletteJsonExportOutcome {
        val bytes = withContext(ioDispatcher) { PaletteJsonCodec.encode(definition).copyBytes() }
        return when (
            val result =
                output.write(
                    DocumentCreationRequest("nene-pixel.nenepalette.json", DocumentOutputFormat.PALETTE_JSON),
                    bytes,
                )
        ) {
            FreshOutputResult.Written -> PaletteJsonExportOutcome.Exported
            FreshOutputResult.Cancelled -> PaletteJsonExportOutcome.Cancelled
            is FreshOutputResult.Failed -> PaletteJsonExportOutcome.Failed(result.failure, result.cleanup)
        }
    }

    public companion object {
        public fun create(
            contentResolver: ContentResolver,
            picker: ProjectDocumentPicker,
            ioDispatcher: CoroutineDispatcher,
        ): PaletteJsonExportPort =
            create(
                ContentResolverProjectContentAccess(contentResolver),
                AndroidProjectPickerAccess(picker),
                ioDispatcher,
            )

        internal fun create(
            content: ProjectContentAccess,
            picker: ProjectPickerAccess,
            ioDispatcher: CoroutineDispatcher,
        ): PaletteJsonExportPort {
            val reader = ProjectDocumentReader(content, PaletteJsonBytes.MAX_FILE_BYTE_COUNT)
            val output = FreshDocumentOutput(FreshDocumentWriter(content, reader), picker, ioDispatcher)
            return AndroidPaletteJsonExportAdapter(output, ioDispatcher)
        }
    }
}
