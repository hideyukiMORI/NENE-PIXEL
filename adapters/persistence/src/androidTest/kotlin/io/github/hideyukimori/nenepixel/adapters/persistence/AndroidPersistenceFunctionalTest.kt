package io.github.hideyukimori.nenepixel.adapters.persistence

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacySourceCopyOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectLoadOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectSaveOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspection
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryPublicationOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementOutcome
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.util.zip.CRC32

@RunWith(AndroidJUnit4::class)
public class AndroidPersistenceFunctionalTest {
    @Test
    public fun actualContentResolverWritesClosesReadsAndDecodes() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val directory = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "project-provider-").toFile()
            val projectFile = File(directory, "fresh.nenepixel")
            val provider = SingleProjectContentProvider(projectFile)
            provider.attachInfo(context, ProviderInfo().also { it.authority = AUTHORITY })
            val resolver = ContentResolver.wrap(provider)
            val uri = Uri.parse("content://$AUTHORITY/document")
            val picker = FixedAndroidPicker(uri)
            val adapter = AndroidProjectStorageAdapter.create(resolver, picker, Dispatchers.IO)
            val document = minimalDocument()
            try {
                assertEquals(ProjectSaveOutcome.Saved, adapter.save(document))
                assertArrayEquals(minimalV2Bytes(), projectFile.readBytes())
                assertEquals(ProjectLoadOutcome.Loaded(DocumentImportSource.Current(document)), adapter.load())
            } finally {
                projectFile.delete()
                directory.delete()
            }
        }

    @Test
    public fun actualContentResolverLoadsV1LegacyAndCopiesExactOriginalToFreshDestination() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val directory =
                Files.createTempDirectory(context.noBackupFilesDir.toPath(), "legacy-original-copy-").toFile()
            val sourceFile = File(directory, "legacy-source.nenepixel")
            val destinationFile = File(directory, "legacy-copy.nenepixel")
            val sourceUri = Uri.parse("content://$AUTHORITY/legacy-source")
            val destinationUri = Uri.parse("content://$AUTHORITY/legacy-copy")
            val source = legacySource()
            val originalBytes = legacyV1Bytes(source)
            sourceFile.writeBytes(originalBytes)
            val provider = SingleProjectContentProvider(sourceFile, mapOf("/legacy-copy" to destinationFile))
            provider.attachInfo(context, ProviderInfo().also { it.authority = AUTHORITY })
            val resolver = ContentResolver.wrap(provider)
            val adapter =
                AndroidProjectStorageAdapter.create(
                    resolver,
                    FixedAndroidPicker(sourceUri, destinationUri),
                    Dispatchers.IO,
                )
            val loaded =
                try {
                    val result = adapter.load()
                    val loadedSource =
                        when (val imported = (result as ProjectLoadOutcome.Loaded).source) {
                            is DocumentImportSource.Legacy -> imported.source
                            is DocumentImportSource.Current -> error("v1 fixture unexpectedly decoded as v2")
                        }
                    assertEquals(source.id, loadedSource.id)
                    assertEquals(source.revision, loadedSource.revision)
                    assertEquals(source.size, loadedSource.size)
                    assertArrayEquals(source.copyPackedRgba8888(), loadedSource.copyPackedRgba8888())
                    assertEquals(272, loadedSource.copyPackedRgba8888().toSet().size)
                    assertEquals(0x01020300, loadedSource.copyPackedRgba8888().first())
                    val beforeCopy = loadedSource.copyPackedRgba8888()

                    assertEquals(LegacySourceCopyOutcome.Copied, adapter.copyLegacySource(loadedSource))
                    assertArrayEquals(originalBytes, destinationFile.readBytes())
                    assertArrayEquals(beforeCopy, loadedSource.copyPackedRgba8888())
                    assertArrayEquals(originalBytes, sourceFile.readBytes())
                    loadedSource
                } finally {
                    destinationFile.delete()
                    sourceFile.delete()
                    directory.delete()
                }
            assertEquals(0x01020300, loaded.copyPackedRgba8888().first())
        }

    @Test
    public fun actualResolverExportsPngWithExactUnpremultipliedPixel() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val directory = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "png-export-").toFile()
            val output = File(directory, "drawing.png")
            val provider = SingleProjectContentProvider(output)
            provider.attachInfo(context, ProviderInfo().also { it.authority = AUTHORITY })
            val resolver = ContentResolver.wrap(provider)
            val adapter =
                AndroidPngExportAdapter.create(
                    resolver,
                    FixedAndroidPicker(Uri.parse("content://$AUTHORITY/png")),
                    Dispatchers.IO,
                )
            val source = minimalDocument()
            val color = PixelColor.fromPackedRgba8888(0x11223301)
            val palette = created(Palette.create(listOf(PixelColor.blank, color)))
            val definition = created(PaletteDefinition.create(palette, created(PaletteIndex.create(0))))
            val snapshot = created(PixelSnapshot.createPackedIndices(source.size, source.revision, byteArrayOf(1)))
            try {
                assertEquals(
                    PngExportOutcome.Exported,
                    adapter.export(created(DocumentState.create(source.id, definition, snapshot))),
                )
                val options = BitmapFactory.Options().apply { inPremultiplied = false }
                val decoded = checkNotNull(BitmapFactory.decodeFile(output.absolutePath, options))
                try {
                    assertEquals(1, decoded.width)
                    assertEquals(1, decoded.height)
                    assertEquals(0x01112233, decoded.getPixel(0, 0))
                } finally {
                    decoded.recycle()
                }
            } finally {
                output.delete()
                directory.delete()
            }
        }

    @Test
    public fun actualAtomicFilePublishesAndReadsBackRetiredRecord() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val directory = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "recovery-").toFile()
            val atomicFile = AtomicFile(File(directory, "nene-pixel-recovery-v1"))
            val adapter = AndroidRecoveryRecordAdapter.create(atomicFile, Dispatchers.IO)
            try {
                val retired = adapter.retire(ExpectedRecoveryLineage.Missing)
                assertTrue(retired is RecoveryRetirementOutcome.Retired)
                val generation = (retired as RecoveryRetirementOutcome.Retired).generation
                assertEquals(RecoveryInspection.Retired(generation), adapter.inspect())
            } finally {
                directory.listFiles()?.forEach { it.delete() }
                directory.delete()
            }
        }

    @Test
    public fun actualAtomicFilePublishesAndReadsBackCandidateRecord() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val directory = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "recovery-candidate-").toFile()
            val atomicFile = AtomicFile(File(directory, "nene-pixel-recovery-v1"))
            val adapter = AndroidRecoveryRecordAdapter.create(atomicFile, Dispatchers.IO)
            val document = minimalDocument()
            try {
                val published = adapter.publishCandidate(ExpectedRecoveryLineage.Missing, document)
                assertTrue(published is RecoveryPublicationOutcome.Published)
                val generation = (published as RecoveryPublicationOutcome.Published).generation
                assertEquals(
                    RecoveryInspection.Candidate(generation, DocumentImportSource.Current(document)),
                    adapter.inspect(),
                )
            } finally {
                directory.listFiles()?.forEach { it.delete() }
                directory.delete()
            }
        }

    @Test
    public fun actualAtomicFileReadsHistoricalV1LegacyCandidateExactly() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val directory = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "recovery-v1-legacy-").toFile()
            val recordFile = File(directory, "nene-pixel-recovery-v1")
            val source = legacySource()
            recordFile.writeBytes(legacyRecoveryV1CandidateBytes(generation = 7L, source = source))
            val adapter = AndroidRecoveryRecordAdapter.create(AtomicFile(recordFile), Dispatchers.IO)
            try {
                val inspection = adapter.inspect()
                val candidate = inspection as RecoveryInspection.Candidate
                assertEquals(generation(7L), candidate.generation)
                assertEquals(DocumentImportSource.Legacy(source), candidate.source)
                val recoveredSource = (candidate.source as DocumentImportSource.Legacy).source
                assertArrayEquals(source.copyPackedRgba8888(), recoveredSource.copyPackedRgba8888())
                assertEquals(0x01020300, recoveredSource.copyPackedRgba8888().first())
            } finally {
                directory.listFiles()?.forEach { it.delete() }
                directory.delete()
            }
        }

    private fun legacySource(): LegacyRgbaSource {
        val id = created(DocumentId.create("0123456789abcdef0123456789abcdef"))
        val size = CanvasSize.create(created(CanvasWidth.create(17)), created(CanvasHeight.create(16)))
        return created(
            LegacyRgbaSource.createPackedRgba8888(
                id,
                created(Revision.create(17L)),
                size,
                IntArray(size.pixelCount.toInt()) { index ->
                    if (index == 0) {
                        0x01020300
                    } else {
                        0xff000000.toInt() or index
                    }
                },
            ),
        )
    }

    private fun legacyV1Bytes(source: LegacyRgbaSource): ByteArray {
        val bytes = ByteArray(V1_PIXEL_OFFSET + source.size.pixelCount.toInt() * BYTES_PER_PIXEL + CRC_BYTES)
        MAGIC.copyInto(bytes)
        writeUnsignedShort(bytes, VERSION_OFFSET, 1)
        writeUnsignedShort(bytes, WIDTH_OFFSET, source.size.width.value)
        writeUnsignedShort(bytes, HEIGHT_OFFSET, source.size.height.value)
        source.id.value.chunked(2).forEachIndexed { index, pair ->
            bytes[DOCUMENT_ID_OFFSET + index] = pair.toInt(HEX_RADIX).toByte()
        }
        writeLong(bytes, REVISION_OFFSET, source.revision.value)
        source.copyPackedRgba8888().forEachIndexed { index, pixel ->
            writeInt(bytes, V1_PIXEL_OFFSET + index * BYTES_PER_PIXEL, pixel)
        }
        writeInt(bytes, bytes.size - CRC_BYTES, checksum(bytes, bytes.size - CRC_BYTES).toInt())
        return bytes
    }

    private fun legacyRecoveryV1CandidateBytes(
        generation: Long,
        source: LegacyRgbaSource,
    ): ByteArray {
        val payload = legacyV1Bytes(source)
        val bytes = ByteArray(RECOVERY_PAYLOAD_OFFSET + payload.size + CRC_BYTES)
        RECOVERY_MAGIC.copyInto(bytes)
        writeUnsignedShort(bytes, RECOVERY_VERSION_OFFSET, 1)
        bytes[RECOVERY_STATE_OFFSET] = RECOVERY_CANDIDATE_STATE.toByte()
        writeLong(bytes, RECOVERY_GENERATION_OFFSET, generation)
        payload.copyInto(bytes, RECOVERY_PAYLOAD_OFFSET)
        writeInt(bytes, bytes.size - CRC_BYTES, checksum(bytes, bytes.size - CRC_BYTES).toInt())
        return bytes
    }

    private fun minimalV2Bytes(): ByteArray {
        val bytes = ByteArray(54)
        MAGIC.copyInto(bytes)
        writeUnsignedShort(bytes, VERSION_OFFSET, 2)
        writeUnsignedShort(bytes, WIDTH_OFFSET, 1)
        writeUnsignedShort(bytes, HEIGHT_OFFSET, 1)
        (0 until 16).forEach { index -> bytes[DOCUMENT_ID_OFFSET + index] = index.toByte() }
        writeLong(bytes, REVISION_OFFSET, 0L)
        writeUnsignedShort(bytes, PALETTE_COUNT_OFFSET, 2)
        bytes[DEFAULT_INDEX_OFFSET] = 0
        writeInt(bytes, PALETTE_OFFSET, 0)
        writeInt(bytes, PALETTE_OFFSET + BYTES_PER_PIXEL, 0)
        bytes[PALETTE_OFFSET + 2 * BYTES_PER_PIXEL] = 0
        writeInt(bytes, bytes.size - CRC_BYTES, checksum(bytes, bytes.size - CRC_BYTES).toInt())
        return bytes
    }

    private fun checksum(
        bytes: ByteArray,
        endExclusive: Int,
    ): Long = CRC32().apply { update(bytes, 0, endExclusive) }.value

    private fun generation(value: Long): RecoveryGeneration =
        when (val result = RecoveryGeneration.create(value)) {
            is io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGenerationResult.Created -> {
                result.generation
            }

            io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGenerationResult.Rejected -> {
                error("Invalid recovery generation fixture: $value")
            }
        }

    private fun writeUnsignedShort(
        bytes: ByteArray,
        offset: Int,
        value: Int,
    ) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }

    private fun writeInt(
        bytes: ByteArray,
        offset: Int,
        value: Int,
    ) {
        repeat(BYTES_PER_PIXEL) { index -> bytes[offset + index] = (value ushr (24 - index * 8)).toByte() }
    }

    private fun writeLong(
        bytes: ByteArray,
        offset: Int,
        value: Long,
    ) {
        repeat(Long.SIZE_BYTES) { index -> bytes[offset + index] = (value ushr (56 - index * 8)).toByte() }
    }

    private fun minimalDocument(): DocumentState {
        val id = created(DocumentId.create("000102030405060708090a0b0c0d0e0f"))
        val size = CanvasSize.create(created(CanvasWidth.create(1)), created(CanvasHeight.create(1)))
        val palette = created(Palette.create(listOf(PixelColor.blank, PixelColor.blank)))
        val definition = created(PaletteDefinition.create(palette, created(PaletteIndex.create(0))))
        val snapshot = created(PixelSnapshot.createPackedIndices(size, created(Revision.create(0L)), byteArrayOf(0)))
        return created(DocumentState.create(id, definition, snapshot))
    }

    private fun <T> created(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid functional test value: ${result.rejection}")
        }

    private companion object {
        const val AUTHORITY: String = "io.github.hideyukimori.nenepixel.persistence.test"
        const val VERSION_OFFSET: Int = 8
        const val WIDTH_OFFSET: Int = 10
        const val HEIGHT_OFFSET: Int = 12
        const val DOCUMENT_ID_OFFSET: Int = 14
        const val REVISION_OFFSET: Int = 30
        const val PALETTE_COUNT_OFFSET: Int = 38
        const val DEFAULT_INDEX_OFFSET: Int = 40
        const val PALETTE_OFFSET: Int = 41
        const val V1_PIXEL_OFFSET: Int = 38
        const val RECOVERY_VERSION_OFFSET: Int = 8
        const val RECOVERY_STATE_OFFSET: Int = 10
        const val RECOVERY_GENERATION_OFFSET: Int = 11
        const val RECOVERY_PAYLOAD_OFFSET: Int = 19
        const val RECOVERY_CANDIDATE_STATE: Int = 1
        const val BYTES_PER_PIXEL: Int = 4
        const val CRC_BYTES: Int = 4
        const val HEX_RADIX: Int = 16
        val MAGIC: ByteArray = byteArrayOf(0x4e, 0x45, 0x4e, 0x45, 0x50, 0x49, 0x58, 0x00)
        val RECOVERY_MAGIC: ByteArray = byteArrayOf(0x4e, 0x45, 0x4e, 0x45, 0x52, 0x45, 0x43, 0x00)
    }
}

private class FixedAndroidPicker(
    private val openUri: Uri,
    private val createUri: Uri = openUri,
) : ProjectDocumentPicker {
    override suspend fun createDocument(request: DocumentCreationRequest): ProjectPickerResult =
        ProjectPickerResult.Selected(createUri)

    override suspend fun openDocument(): ProjectPickerResult = ProjectPickerResult.Selected(openUri)
}

private class SingleProjectContentProvider(
    private val projectFile: File,
    private val filesByPath: Map<String, File> = emptyMap(),
) : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "application/octet-stream"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor = sizeCursor(file(uri))

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        queryArgs: Bundle?,
        cancellationSignal: android.os.CancellationSignal?,
    ): Cursor = sizeCursor(file(uri))

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor {
        val flags =
            if (mode == "r") {
                ParcelFileDescriptor.MODE_READ_ONLY
            } else {
                ParcelFileDescriptor.MODE_READ_WRITE or
                    ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE
            }
        return ParcelFileDescriptor.open(file(uri), flags)
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = if (file(uri).delete()) 1 else 0

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun file(uri: Uri): File = filesByPath[uri.path] ?: projectFile

    private fun sizeCursor(file: File): Cursor =
        MatrixCursor(arrayOf(OpenableColumns.SIZE)).apply {
            addRow(arrayOf(file.length()))
        }
}
