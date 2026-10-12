package io.github.hideyukimori.nenepixel.core.application.workspace.importing

/** The three planned forms belong to one pending import and carry no duplicate live raster owner. */
internal data class RasterImportForms(
    val appending: RasterImportOption,
    val converting: RasterImportOption,
    val newWork: NewWorkImportOption,
)
