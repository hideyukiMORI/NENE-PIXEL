package io.github.hideyukimori.nenepixel

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import io.github.hideyukimori.nenepixel.adapters.persistence.AndroidPngExportAdapter
import io.github.hideyukimori.nenepixel.adapters.persistence.ProjectPickerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class PngPickerCancellationTest {
    @Test
    fun cancelledClaimedPickerDeletesSelectedUriBeforeCompletion() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val provider = CleanupOnlyProvider()
            provider.attachInfo(context, ProviderInfo().also { it.authority = AUTHORITY })
            val broker = ProjectPickerBroker()
            val exporter =
                AndroidPngExportAdapter.create(
                    ContentResolver.wrap(provider),
                    broker,
                    Dispatchers.Unconfined,
                )
            val document = createEditorRuntime().state.documentState
            val job = async { exporter.export(document) }
            yield()
            assertTrue(broker.claim(checkNotNull(broker.pendingRequest.value)))
            job.cancel()
            yield()
            assertFalse(job.isCompleted)
            broker.completeCreate(ProjectPickerResult.Selected(Uri.parse("content://$AUTHORITY/fresh")))
            job.join()
            assertTrue(job.isCancelled)
            assertEquals(1, provider.deleted)
            assertEquals(0, provider.opened)
        }

    private companion object {
        const val AUTHORITY: String = "io.github.hideyukimori.nenepixel.png.cleanup.test"
    }
}

private class CleanupOnlyProvider : ContentProvider() {
    var deleted: Int = 0
        private set
    var opened: Int = 0
        private set

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor {
        opened += 1
        error("Cancelled export must not open output")
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/png"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = error("Cancelled export must not read")

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = error("Cancelled export must not insert")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = error("Cancelled export must not update")

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        deleted += 1
        return 1
    }
}
