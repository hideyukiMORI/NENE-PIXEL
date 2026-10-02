package io.github.hideyukimori.nenepixel.adapters.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

/** The real [AndroidUnderlayRecordFiles] on a temporary directory of the device (ADR 0034). */
@RunWith(AndroidJUnit4::class)
public class AndroidUnderlayRecordFilesTest {
    private lateinit var parent: File
    private lateinit var directory: File
    private lateinit var files: AndroidUnderlayRecordFiles

    @Before
    public fun createDirectory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        parent = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "underlay-files-").toFile()
        directory = File(parent, UnderlayMemoryLayout.DIRECTORY_NAME)
        files = AndroidUnderlayRecordFiles(directory)
    }

    @After
    public fun deleteDirectory() {
        parent.deleteRecursively()
    }

    @Test
    public fun writeCreatesTheDirectoryAndReadsBackTheBytes() {
        val bytes = ByteArray(BYTE_COUNT) { index -> index.toByte() }

        files.write(NAME, bytes)

        assertTrue(directory.isDirectory)
        assertArrayEquals(bytes, files.read(NAME, BYTE_COUNT))
        assertEquals(BYTE_COUNT.toLong(), files.length(NAME))
    }

    @Test
    public fun aMissingFileReadsNullAndAMissingDirectoryHasNoNames() {
        assertNull(files.read(NAME, BYTE_COUNT))
        assertEquals(emptyList<String>(), files.names())
    }

    @Test
    public fun aLongerFileReadsExactlyMaxLengthPlusOneBytes() {
        val bytes = ByteArray(BYTE_COUNT) { index -> index.toByte() }
        files.write(NAME, bytes)

        val read = files.read(NAME, SHORT_MAX_LENGTH)

        assertArrayEquals(bytes.copyOf(SHORT_MAX_LENGTH + 1), read)
    }

    @Test
    public fun deleteRemovesTheFileAndAMissingFileIsNotAnError() {
        files.write(NAME, ByteArray(BYTE_COUNT))

        files.delete(NAME)
        files.delete(NAME)

        assertNull(files.read(NAME, BYTE_COUNT))
        assertFalse(File(directory, NAME).exists())
    }

    @Test
    public fun namesAnswersEveryFileIncludingWriteLeftovers() {
        files.write(NAME, ByteArray(BYTE_COUNT))
        File(directory, "$NAME.new").writeBytes(ByteArray(1))
        File(directory, "$OTHER_NAME.bak").writeBytes(ByteArray(1))

        val names = files.names().toSet()

        assertEquals(setOf(NAME, "$NAME.new", "$OTHER_NAME.bak"), names)
    }

    @Test
    public fun markUsedMovesUsedAtForward() {
        files.write(NAME, ByteArray(BYTE_COUNT))
        assertTrue(File(directory, NAME).setLastModified(OLD_TIME_MILLIS))
        val before = files.usedAt(NAME)

        files.markUsed(NAME)

        assertTrue("usedAt $before -> ${files.usedAt(NAME)}", files.usedAt(NAME) > before)
    }

    private companion object {
        const val NAME: String = "0123456789abcdef0123456789abcdef.state"
        const val OTHER_NAME: String = "fedcba9876543210fedcba9876543210.image"
        const val BYTE_COUNT: Int = 64
        const val SHORT_MAX_LENGTH: Int = 10
        const val OLD_TIME_MILLIS: Long = 1_000_000_000_000L
    }
}
