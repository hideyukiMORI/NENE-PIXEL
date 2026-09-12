package io.github.hideyukimori.nenepixel.acceptance

import android.os.Process
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.CRC32

internal class AcceptanceFixture {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    val id: String = arguments.getString("m3EvidenceId").orEmpty()

    fun requireIsolation() {
        assumeTrue("Run only with the documented isolated device protocol", arguments.getString("m3Isolated") == "true")
        require(id.matches(Regex("[a-z0-9-]{1,35}")))
        check(File(context.noBackupFilesDir, "issue-89-user-recovery-20260912").isDirectory)
    }

    fun recordProcess(
        stage: String,
        previous: String? = null,
    ) {
        if (previous != null) assertNotEquals(local("$previous.pid").readText().toInt(), Process.myPid())
        writeNew("$stage.pid", Process.myPid().toString().toByteArray())
        println("M3_PROCESS $stage ${Process.myPid()}")
    }

    fun name(suffix: String): String = "i89-$id-$suffix"

    fun readDocument(suffix: String): ByteArray {
        val uri = DocumentsContract.buildDocumentUri(AcceptanceDocumentsProvider.AUTHORITY, name(suffix))
        return checkNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
    }

    fun writeNew(
        suffix: String,
        bytes: ByteArray,
    ) {
        val file = local(suffix)
        check(file.createNewFile()) { "Refusing to overwrite evidence: $file" }
        file.writeBytes(bytes)
    }

    fun local(suffix: String): File = File(context.filesDir, name(suffix))

    fun savedId(): String =
        local("expected.nenepixel").readBytes().copyOfRange(14, 30).joinToString("") {
            "%02x".format(it)
        }

    fun verifyProject(
        bytes: ByteArray,
        documentId: String,
        revision: Long,
        recovered: Boolean = false,
    ) {
        assertEquals(234, bytes.size)
        assertArrayEquals(byteArrayOf(78, 69, 78, 69, 80, 73, 88, 0), bytes.copyOfRange(0, 8))
        val buffer = ByteBuffer.wrap(bytes)
        assertEquals(1, buffer.getShort(8).toInt())
        assertEquals(8, buffer.getShort(10).toInt())
        assertEquals(6, buffer.getShort(12).toInt())
        assertEquals(documentId, bytes.copyOfRange(14, 30).joinToString("") { "%02x".format(it) })
        assertEquals(revision, buffer.getLong(30))
        assertArrayEquals(expectedPixels(recovered), IntArray(48) { buffer.getInt(38 + it * 4) })
        val crc = CRC32().apply { update(bytes, 0, 230) }.value
        assertEquals(crc, buffer.getInt(230).toLong() and 0xffffffffL)
    }

    fun expectedPixels(recovered: Boolean = false): IntArray =
        IntArray(48).apply {
            this[9] = 0xff0000ff.toInt()
            this[11] = 0xff0000ff.toInt()
            this[29] = 0x0000ffff
            if (recovered) this[38] = 0x00ff00ff
        }
}
