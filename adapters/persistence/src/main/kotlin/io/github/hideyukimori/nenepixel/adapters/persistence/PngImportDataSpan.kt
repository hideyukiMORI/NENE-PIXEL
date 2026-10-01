package io.github.hideyukimori.nenepixel.adapters.persistence

/**
 * The bytes of a PNG file to import that hold its consecutive `IDAT` chunks (ADR 0033): from the
 * length field of the first `IDAT` chunk ([start]) to just after the CRC of the last one ([end]).
 * Only `IDAT` chunks lie in between; their lengths and CRCs were checked when the file was walked.
 * [PngImportChunks.forEachData] visits the data of each chunk without copying it.
 */
internal class PngImportDataSpan(
    val start: Int,
    val end: Int,
)
