package io.github.hideyukimori.nenepixel.presentation.compose.editor

/** Three forms of one immutable import choice; its source/target stays with the pending operation. */
internal data class PngImportFormChoices(
    val append: PngImportFormModel,
    val convert: PngImportFormModel,
    val newWork: PngImportFormModel,
)
