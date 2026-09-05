package io.github.hideyukimori.nenepixel.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class NoBaselineValidatorTest {
    @TempDir
    private lateinit var root: Path

    @Test
    fun `relevant baseline files including untracked-style files are detected`() {
        write("config/lint-baseline.xml")
        write("reports/baseline.sarif")
        write("config/baseline.txt")

        assertEquals(
            listOf(
                Path.of("config", "lint-baseline.xml").toString(),
                Path.of("reports", "baseline.sarif").toString(),
            ),
            NoBaselineValidator(root).findBaselines().map(Path::toString),
        )
    }

    @Test
    fun `baseline files below ignored directories are not validation input`() {
        ignoredDirectoryPaths.forEach { directory -> write("$directory/lint-baseline.xml") }

        assertEquals(emptyList<Path>(), NoBaselineValidator(root).findBaselines())
    }

    private fun write(relativePath: String) {
        val target = root.resolve(relativePath)
        Files.createDirectories(target.parent)
        Files.writeString(target, "baseline")
    }

    private companion object {
        val ignoredDirectoryPaths: List<String> =
            listOf(".git", ".gradle", ".idea", ".kotlin", "build", "module/build")
    }
}
