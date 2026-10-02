package io.github.hideyukimori.nenepixel.core.application.workspace.importing

/** What a picked PNG is, independent of how it is imported: its size and number of colours (ADR 0033). */
public data class RasterImportFacts internal constructor(
    public val width: Int,
    public val height: Int,
    public val colorCount: Int,
)
