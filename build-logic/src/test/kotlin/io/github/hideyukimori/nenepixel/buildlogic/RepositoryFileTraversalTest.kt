package io.github.hideyukimori.nenepixel.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class RepositoryFileTraversalTest {
    @TempDir
    private lateinit var temporaryDirectory: Path

    @Test
    fun `ignored name on repository root preserves the former empty input semantics`() {
        val ignoredRoot = temporaryDirectory.resolve("build")
        Files.createDirectories(ignoredRoot)
        Files.writeString(ignoredRoot.resolve("tracked.kt"), "val tracked = true")

        assertEquals(emptyList<Path>(), RepositoryFileTraversal.regularFiles(ignoredRoot))
    }

    @Test
    fun `hidden and untracked-style files outside ignored directories remain inputs`() {
        val hiddenFile = temporaryDirectory.resolve(".hidden/source.kt")
        val untrackedStyleFile = temporaryDirectory.resolve("scratch/new-source.kt")
        Files.createDirectories(hiddenFile.parent)
        Files.createDirectories(untrackedStyleFile.parent)
        Files.writeString(hiddenFile, "val hidden = true")
        Files.writeString(untrackedStyleFile, "val untracked = true")

        assertEquals(
            setOf(hiddenFile, untrackedStyleFile),
            RepositoryFileTraversal.regularFiles(temporaryDirectory).toSet(),
        )
    }
}
