package io.github.hideyukimori.nenepixel.adapters.persistence

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ProjectActivityResultContractsAndroidTest {
    @Test
    public fun createIntentHasCanonicalActionMimeCategoryAndExtension() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val intent = CreateProjectDocumentContract().createIntent(context, "drawing")

        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("application/octet-stream", intent.type)
        assertTrue(intent.categories?.contains(Intent.CATEGORY_OPENABLE) == true)
        assertEquals("drawing.nenepixel", intent.getStringExtra(Intent.EXTRA_TITLE))
    }

    @Test
    public fun openIntentHasCanonicalActionMimeAndCategory() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val intent = OpenProjectDocumentContract().createIntent(context, Unit)

        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertEquals("application/octet-stream", intent.type)
        assertTrue(intent.categories?.contains(Intent.CATEGORY_OPENABLE) == true)
    }

    @Test
    public fun parseDistinguishesCancelNullSuccessUnexpectedAndSelection() {
        val contract = OpenProjectDocumentContract()
        val selectedUri = Uri.parse("content://test/document")

        assertSame(ProjectPickerResult.Cancelled, contract.parseResult(Activity.RESULT_CANCELED, Intent()))
        assertEquals(
            ProjectPickerResult.Failed(ProjectStorageFailure.InvalidPickerResult),
            contract.parseResult(Activity.RESULT_OK, Intent()),
        )
        assertEquals(
            ProjectPickerResult.Failed(ProjectStorageFailure.UnexpectedPickerResultCode),
            contract.parseResult(7, Intent().setData(selectedUri)),
        )
        assertEquals(
            ProjectPickerResult.Selected(selectedUri),
            contract.parseResult(Activity.RESULT_OK, Intent().setData(selectedUri)),
        )
    }
}
