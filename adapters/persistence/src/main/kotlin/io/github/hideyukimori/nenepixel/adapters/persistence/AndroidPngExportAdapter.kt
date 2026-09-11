package io.github.hideyukimori.nenepixel.adapters.persistence

import android.content.ContentResolver
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportPort
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

public class AndroidPngExportAdapter private constructor(
    private val output: FreshDocumentOutput,
    private val ioDispatcher: CoroutineDispatcher,
) : PngExportPort {
    override suspend fun export(document: DocumentState): PngExportOutcome {
        val bytes = withContext(ioDispatcher) { PngEncoder.encode(document.snapshot).copyBytes() }
        return when (
            val result =
                output.write(
                    DocumentCreationRequest("nene-pixel.png", DocumentOutputFormat.PNG),
                    bytes,
                )
        ) {
            FreshOutputResult.Written -> PngExportOutcome.Exported
            FreshOutputResult.Cancelled -> PngExportOutcome.Cancelled
            is FreshOutputResult.Failed -> PngExportOutcome.Failed(result.failure, result.cleanup)
        }
    }

    public companion object {
        public fun create(
            contentResolver: ContentResolver,
            picker: ProjectDocumentPicker,
            ioDispatcher: CoroutineDispatcher,
        ): PngExportPort =
            create(
                ContentResolverProjectContentAccess(contentResolver),
                AndroidProjectPickerAccess(picker),
                ioDispatcher,
            )

        internal fun create(
            content: ProjectContentAccess,
            picker: ProjectPickerAccess,
            ioDispatcher: CoroutineDispatcher,
        ): PngExportPort {
            val reader = ProjectDocumentReader(content, PngBytes.MAX_BYTE_COUNT)
            val output = FreshDocumentOutput(FreshDocumentWriter(content, reader), picker, ioDispatcher)
            return AndroidPngExportAdapter(output, ioDispatcher)
        }
    }
}
