package io.github.hideyukimori.nenepixel.adapters.persistence

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectLoadOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectSaveOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspection
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryPublicationOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementOutcome
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

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
                assertEquals(ProjectLoadOutcome.Loaded(document), adapter.load())
            } finally {
                projectFile.delete()
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
                assertEquals(RecoveryInspection.Candidate(generation, document), adapter.inspect())
            } finally {
                directory.listFiles()?.forEach { it.delete() }
                directory.delete()
            }
        }

    private fun minimalDocument(): DocumentState {
        val id = created(DocumentId.create("000102030405060708090a0b0c0d0e0f"))
        val size = CanvasSize.create(created(CanvasWidth.create(1)), created(CanvasHeight.create(1)))
        val snapshot = created(PixelSnapshot.createPackedRgba8888(size, created(Revision.create(0L)), intArrayOf(0)))
        return DocumentState.create(id, snapshot)
    }

    private fun <T> created(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid functional test value: ${result.rejection}")
        }

    private companion object {
        const val AUTHORITY: String = "io.github.hideyukimori.nenepixel.persistence.test"
    }
}

private class FixedAndroidPicker(
    private val uri: Uri,
) : ProjectDocumentPicker {
    override suspend fun createDocument(suggestedName: String): ProjectPickerResult = ProjectPickerResult.Selected(uri)

    override suspend fun openDocument(): ProjectPickerResult = ProjectPickerResult.Selected(uri)
}

private class SingleProjectContentProvider(
    private val projectFile: File,
) : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "application/octet-stream"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor = sizeCursor()

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        queryArgs: Bundle?,
        cancellationSignal: android.os.CancellationSignal?,
    ): Cursor = sizeCursor()

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
        return ParcelFileDescriptor.open(projectFile, flags)
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = if (projectFile.delete()) 1 else 0

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

    private fun sizeCursor(): Cursor =
        MatrixCursor(arrayOf(OpenableColumns.SIZE)).apply {
            addRow(arrayOf(projectFile.length()))
        }
}
