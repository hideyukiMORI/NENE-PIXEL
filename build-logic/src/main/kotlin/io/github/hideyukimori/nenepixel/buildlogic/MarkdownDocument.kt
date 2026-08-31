package io.github.hideyukimori.nenepixel.buildlogic

import java.nio.file.Path

internal data class MarkdownDocument(
    val path: Path,
    val relativePath: String,
    val content: String,
)
