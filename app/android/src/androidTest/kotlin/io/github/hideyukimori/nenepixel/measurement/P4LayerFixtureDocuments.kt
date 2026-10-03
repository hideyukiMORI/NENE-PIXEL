package io.github.hideyukimori.nenepixel.measurement

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.DocumentsContract
import androidx.activity.result.ActivityResultLauncher
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.CreateProjectDocumentContract
import io.github.hideyukimori.nenepixel.MainActivity
import io.github.hideyukimori.nenepixel.acceptance.AcceptanceDocumentsProvider
import io.github.hideyukimori.nenepixel.acceptance.AcceptanceDocumentsUi
import io.github.hideyukimori.nenepixel.adapters.persistence.DocumentCreationRequest
import io.github.hideyukimori.nenepixel.adapters.persistence.DocumentOutputFormat
import io.github.hideyukimori.nenepixel.adapters.persistence.ProjectPickerResult
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

/** Real app grants and the existing provider; all setup is outside measured operations. */
internal class P4LayerFixtureDocuments(
    private val scenario: ActivityScenario<MainActivity>,
    private val awaitCondition: (() -> Boolean) -> Unit,
) {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val picker = AcceptanceDocumentsUi(awaitCondition)

    fun createEmpty(
        name: String,
        format: DocumentOutputFormat,
    ): P4GrantedDocument {
        require(name.matches(Regex("i89-145-[a-z0-9.-]{1,86}")))
        require(format == DocumentOutputFormat.PROJECT || format == DocumentOutputFormat.PNG)
        val result = AtomicReference<ProjectPickerResult>()
        var launcher: ActivityResultLauncher<DocumentCreationRequest>? = null
        try {
            scenario.onActivity { activity ->
                launcher =
                    activity.activityResultRegistry.register(
                        "p4-layer-$name",
                        CreateProjectDocumentContract(),
                        result::set,
                    )
                checkNotNull(launcher).launch(DocumentCreationRequest(name, format))
            }
            picker.create(name)
            awaitCondition { result.get() != null }
            val selected = result.get()
            check(selected is ProjectPickerResult.Selected) { "Fresh document creation failed: $selected" }
            return verifyFresh(selected.uri, name)
        } finally {
            scenario.onActivity { launcher?.unregister() }
        }
    }

    fun stage(
        name: String,
        fixture: P4LayerFixture,
    ): P4GrantedDocument {
        val bytes =
            instrumentation.context.assets
                .open(fixture.asset)
                .use { readExact(it, fixture.byteCount) }
        check(p4LayerSha256(bytes) == fixture.sha256) { "Packaged fixture hash differs" }
        val document = createEmpty(name, fixture.format)
        checkNotNull(context.contentResolver.openOutputStream(document.uri, "w")).use { it.write(bytes) }
        verifyBytes(document, fixture)
        return document
    }

    fun verifyBytes(
        document: P4GrantedDocument,
        fixture: P4LayerFixture,
    ) {
        val bytes =
            checkNotNull(context.contentResolver.openInputStream(document.uri)).use { readExact(it, fixture.byteCount) }
        check(p4LayerSha256(bytes) == fixture.sha256) { "Provider fixture hash differs" }
    }

    fun openInProductionPicker(name: String) = picker.open(name)

    private fun verifyFresh(
        uri: Uri,
        name: String,
    ): P4GrantedDocument {
        val authority = AcceptanceDocumentsProvider.AUTHORITY
        check(uri == DocumentsContract.buildDocumentUri(authority, name)) { "Unexpected fixture destination" }
        check(Process.myUid() == context.applicationInfo.uid) { "Grant check is not in the target app process" }
        for (flag in listOf(Intent.FLAG_GRANT_READ_URI_PERMISSION, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) {
            check(
                context.checkUriPermission(uri, Process.myPid(), Process.myUid(), flag) ==
                    PackageManager.PERMISSION_GRANTED,
            )
        }
        if (Build.VERSION.SDK_INT <
            Build.VERSION_CODES.TIRAMISU
        ) {
            error("Layer phase requires the physical API-36 profile")
        }
        val provider =
            checkNotNull(
                context.packageManager.resolveContentProvider(authority, PackageManager.ComponentInfoFlags.of(0)),
            )
        check(provider.packageName == instrumentation.context.packageName)
        check(provider.name == AcceptanceDocumentsProvider::class.java.name)
        check(provider.applicationInfo.uid != Process.myUid()) { "Fixture provider must be outside target-process PSS" }
        verifyEmptyMetadata(uri, name)
        checkNotNull(context.contentResolver.openInputStream(uri)).use { check(it.read() == -1) }
        return P4GrantedDocument(uri, name, Process.myUid(), provider.applicationInfo.uid)
    }

    private fun verifyEmptyMetadata(
        uri: Uri,
        name: String,
    ) {
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_SIZE)
        checkNotNull(context.contentResolver.query(uri, columns, null, null, null)).use { cursor ->
            check(cursor.count == 1 && cursor.moveToFirst())
            check(cursor.getString(0) == name && !cursor.isNull(1) && cursor.getLong(1) == 0L)
        }
    }

    private fun readExact(
        input: InputStream,
        byteCount: Int,
    ): ByteArray {
        val bytes = ByteArray(byteCount)
        var offset = 0
        while (offset < bytes.size) {
            val count = input.read(bytes, offset, bytes.size - offset)
            check(count > 0) { "Fixture is shorter than declared" }
            offset += count
        }
        check(input.read() == -1) { "Fixture is longer than declared" }
        return bytes
    }
}

internal data class P4GrantedDocument(
    val uri: Uri,
    val name: String,
    val granteeUid: Int,
    val providerUid: Int,
)

internal enum class P4LayerFixture(
    val asset: String,
    val byteCount: Int,
    val sha256: String,
    val format: DocumentOutputFormat,
) {
    MAXIMUM(
        "maximum-layered.nenepixel",
        1_182_862,
        "165f62d180533849ce1a4ef1625cd3971e445f2dca60ef7b9b46fedaafa0b3ec",
        DocumentOutputFormat.PROJECT,
    ),
    UNDERLAY(
        "underlay-grid.png",
        184_323,
        "05efb3fc8edf43f45dc5a8b7cae3be148680c5694b47c47d1cf4eb7cbe26c6fb",
        DocumentOutputFormat.PNG,
    ),
}

internal fun p4LayerSha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
        (it.toInt() and 0xFF).toString(16).padStart(2, '0')
    }
