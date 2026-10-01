package io.github.hideyukimori.nenepixel.adapters.persistence

/**
 * The chunks of a PNG file that the import reads (ADR 0033), before anything is inflated.
 *
 * It is an adapter-internal value like [ReferenceImageDecodeResult.Decoded]: the arrays are owned by
 * this value, are never modified and never leave `:adapters:persistence`.
 *
 * @property palette the data of `PLTE`, three bytes per entry, for colour type 3 only.
 * @property transparency the data of `tRNS`, when present.
 * @property compressed the data of every `IDAT` chunk in order, joined into one zlib stream.
 */
internal class PngImportStructure(
    val header: PngImportHeader,
    val palette: ByteArray?,
    val transparency: ByteArray?,
    val compressed: ByteArray,
)
