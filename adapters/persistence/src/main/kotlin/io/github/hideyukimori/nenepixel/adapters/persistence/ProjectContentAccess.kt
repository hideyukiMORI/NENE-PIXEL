package io.github.hideyukimori.nenepixel.adapters.persistence

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.InputStream
import java.io.OutputStream

internal interface ProjectLocation

internal data class UriProjectLocation(
    val uri: Uri,
) : ProjectLocation

internal interface ProjectContentAccess {
    fun knownByteCount(location: ProjectLocation): Long?

    fun openInput(location: ProjectLocation): InputStream?

    fun openOutput(location: ProjectLocation): OutputStream?

    fun delete(location: ProjectLocation): Int
}

internal class ContentResolverProjectContentAccess(
    private val resolver: ContentResolver,
) : ProjectContentAccess {
    override fun knownByteCount(location: ProjectLocation): Long? =
        resolver.query(location.uri(), SIZE_PROJECTION, null, null, null)?.use { cursor ->
            val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (!cursor.moveToFirst() || sizeColumn < 0 || cursor.isNull(sizeColumn)) {
                null
            } else {
                cursor.getLong(sizeColumn).takeIf { it >= 0L }
            }
        }

    override fun openInput(location: ProjectLocation): InputStream? = resolver.openInputStream(location.uri())

    override fun openOutput(location: ProjectLocation): OutputStream? =
        resolver.openOutputStream(location.uri(), WRITE_MODE)

    override fun delete(location: ProjectLocation): Int = resolver.delete(location.uri(), null, null)

    private companion object {
        const val WRITE_MODE: String = "w"
        val SIZE_PROJECTION: Array<String> = arrayOf(OpenableColumns.SIZE)
    }

    private fun ProjectLocation.uri(): Uri = (this as UriProjectLocation).uri
}
