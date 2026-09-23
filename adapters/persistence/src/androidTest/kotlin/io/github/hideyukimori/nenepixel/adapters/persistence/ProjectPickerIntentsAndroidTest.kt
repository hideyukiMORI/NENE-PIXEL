package io.github.hideyukimori.nenepixel.adapters.persistence

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ProjectPickerIntentsAndroidTest {
    @Test
    public fun createIntentHasCanonicalActionMimeCategoryAndExtension() {
        val intent =
            ProjectPickerIntents.createDocument(
                DocumentCreationRequest("drawing", DocumentOutputFormat.PROJECT),
            )

        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("application/octet-stream", intent.type)
        assertTrue(intent.categories?.contains(Intent.CATEGORY_OPENABLE) == true)
        assertEquals("drawing.nenepixel", intent.getStringExtra(Intent.EXTRA_TITLE))
    }

    @Test
    public fun pngIntentHasExactMimeAndDoesNotDuplicateExtension() {
        val intent =
            ProjectPickerIntents.createDocument(
                DocumentCreationRequest("drawing.PNG", DocumentOutputFormat.PNG),
            )
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("image/png", intent.type)
        assertEquals("drawing.PNG", intent.getStringExtra(Intent.EXTRA_TITLE))
        assertTrue(intent.categories?.contains(Intent.CATEGORY_OPENABLE) == true)
    }

    @Test
    public fun paletteJsonIntentHasJsonMimeAndDoesNotDuplicateCompoundExtension() {
        val named =
            ProjectPickerIntents.createDocument(
                DocumentCreationRequest("nene-pixel.nenepalette.json", DocumentOutputFormat.PALETTE_JSON),
            )
        val bare =
            ProjectPickerIntents.createDocument(
                DocumentCreationRequest("drawing", DocumentOutputFormat.PALETTE_JSON),
            )

        assertEquals(Intent.ACTION_CREATE_DOCUMENT, named.action)
        assertEquals("application/json", named.type)
        assertEquals("nene-pixel.nenepalette.json", named.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals("drawing.nenepalette.json", bare.getStringExtra(Intent.EXTRA_TITLE))
        assertTrue(named.categories?.contains(Intent.CATEGORY_OPENABLE) == true)
    }

    @Test
    public fun openIntentHasCanonicalActionMimeAndCategory() {
        val intent = ProjectPickerIntents.openDocument(DocumentOpenRequest(DocumentOutputFormat.PROJECT))

        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertEquals("application/octet-stream", intent.type)
        assertTrue(intent.categories?.contains(Intent.CATEGORY_OPENABLE) == true)
    }

    @Test
    public fun paletteJsonOpenIntentHasJsonMime() {
        val intent = ProjectPickerIntents.openDocument(DocumentOpenRequest(DocumentOutputFormat.PALETTE_JSON))

        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertEquals("application/json", intent.type)
        assertTrue(intent.categories?.contains(Intent.CATEGORY_OPENABLE) == true)
    }

    @Test
    public fun parseDistinguishesCancelNullSuccessUnexpectedAndSelection() {
        val selectedUri = Uri.parse("content://test/document")

        assertSame(
            ProjectPickerResult.Cancelled,
            ProjectPickerIntents.parseResult(Activity.RESULT_CANCELED, Intent()),
        )
        assertEquals(
            ProjectPickerResult.Failed(ProjectStorageFailure.InvalidPickerResult),
            ProjectPickerIntents.parseResult(Activity.RESULT_OK, Intent()),
        )
        assertEquals(
            ProjectPickerResult.Failed(ProjectStorageFailure.UnexpectedPickerResultCode),
            ProjectPickerIntents.parseResult(7, Intent().setData(selectedUri)),
        )
        assertEquals(
            ProjectPickerResult.Selected(selectedUri),
            ProjectPickerIntents.parseResult(Activity.RESULT_OK, Intent().setData(selectedUri)),
        )
    }
}
