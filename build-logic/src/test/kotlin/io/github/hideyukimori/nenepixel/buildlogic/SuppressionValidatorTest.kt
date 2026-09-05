package io.github.hideyukimori.nenepixel.buildlogic

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

internal class SuppressionValidatorTest {
    @TempDir
    private lateinit var root: Path

    @Test
    fun `source without suppression is accepted`() {
        write("src/main/kotlin/Layer.kt", fixture("compliant-suppression.fixture"))

        assertNoViolations()
    }

    @Test
    fun `suppression without waiver is rejected`() {
        write("src/main/kotlin/Layer.kt", fixture("unwaived-suppression.fixture"))

        assertViolationContains("requires an adjacent active waiver comment")
    }

    @Test
    fun `active exact-scope waiver is accepted`() {
        write("src/main/kotlin/Layer.kt", fixture("waived-suppression.fixture"))
        write("docs/waivers/WVR-0001-test.md", activeWaiver("src/main/kotlin/Layer.kt#Layer"))

        assertNoViolations()
    }

    @Test
    fun `file suppression is rejected even with active waiver`() {
        write("src/main/kotlin/Layer.kt", fixture("file-suppression.fixture"))
        write("docs/waivers/WVR-0001-test.md", activeWaiver("src/main/kotlin/Layer.kt#Layer"))

        assertViolationContains("prohibits file-level suppression")
    }

    @Test
    fun `waiver for another file is rejected`() {
        write("src/main/kotlin/Layer.kt", fixture("waived-suppression.fixture"))
        write("docs/waivers/WVR-0001-test.md", activeWaiver("src/main/kotlin/Other.kt#Other"))

        assertViolationContains("does not cover 'src/main/kotlin/Layer.kt'")
    }

    @Test
    fun `waiver for another declaration is rejected`() {
        write("src/main/kotlin/Layer.kt", fixture("waived-suppression.fixture"))
        write("docs/waivers/WVR-0001-test.md", activeWaiver("src/main/kotlin/Layer.kt#Other"))

        assertViolationContains("does not cover the suppressed declaration")
    }

    @Test
    fun `active exact-scope waiver covers XML element id`() {
        write("src/main/res/layout/editor.xml", fixture("waived-xml-suppression.fixture"))
        write("docs/waivers/WVR-0001-test.md", activeWaiver("src/main/res/layout/editor.xml#save_button"))

        assertNoViolations()
    }

    @Test
    fun `suppression below ignored directories is not validation input`() {
        ignoredDirectoryPaths.forEach { directory ->
            write("$directory/Layer.kt", fixture("unwaived-suppression.fixture"))
        }

        assertNoViolations()
    }

    private fun assertNoViolations() {
        val violations = validate()
        assertTrue(violations.isEmpty(), violations.joinToString(separator = "\n"))
    }

    private fun assertViolationContains(message: String) {
        val violations = validate()
        assertTrue(violations.any { message in it.message }, violations.joinToString(separator = "\n"))
    }

    private fun validate(): List<ArchitectureViolation> =
        SuppressionValidator(root, LocalDate.parse("2026-09-01")).validate()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/architecture-fixtures/$name")) {
            "Missing architecture fixture: $name"
        }.readText()

    private fun write(
        relativePath: String,
        content: String,
    ) {
        val target = root.resolve(relativePath)
        Files.createDirectories(target.parent)
        Files.writeString(target, content)
    }

    private fun activeWaiver(scope: String): String =
        """
        # WVR-0001: Test

        - Status: active
        - Rule: `KOT-013`
        - Issue: #9
        - Owner: test
        - Created: 2026-09-01
        - Expires: 2026-09-02
        - Scope: `$scope`
        """.trimIndent() + "\n"

    private companion object {
        val ignoredDirectoryPaths: List<String> =
            listOf(".git", ".gradle", ".idea", ".kotlin", "build", "module/build")
    }
}
