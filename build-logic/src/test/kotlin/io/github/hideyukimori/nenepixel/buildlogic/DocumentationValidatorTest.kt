package io.github.hideyukimori.nenepixel.buildlogic

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

internal class DocumentationValidatorTest {
    @TempDir
    private lateinit var root: Path

    @Test
    fun `valid documentation has no violations`() {
        write("README.md", "[Rules](docs/rules.md)")
        write("docs/rules.md", "### KOT-001 — Rule\n")
        write("docs/DEVELOPMENT_PLAN.md", "| P0-01 | Work | none | proof |\n\nUse `P0-01`.\n")
        write("docs/adr/README.md", "[0001](0001-test.md)\n")
        write("docs/adr/0001-test.md", validAdr())
        write("docs/waivers/README.md", "No active waivers.\n")

        assertNoViolations()
    }

    @Test
    fun `broken local link is rejected`() {
        write("README.md", "[Missing](docs/missing.md)\n")

        assertViolationContains("broken local link: docs/missing.md")
    }

    @Test
    fun `undefined and duplicate rule references are rejected`() {
        write("README.md", "`KOT-999`\n")
        write("docs/one.md", "### KOT-001 — One\n")
        write("docs/two.md", "### KOT-001 — Duplicate\n")

        assertViolationContains("undefined rule reference: KOT-999")
        assertViolationContains("duplicate rule definition: KOT-001")
    }

    @Test
    fun `undefined work package is rejected`() {
        write("docs/plan.md", "Execute P9-99.\n")

        assertViolationContains("undefined work package reference: P9-99")
    }

    @Test
    fun `malformed ADR is rejected`() {
        write("docs/adr/README.md", "[0001](0001-broken.md)\n")
        write("docs/adr/0001-broken.md", "# ADR 0001: Broken\n")

        assertViolationContains("missing or invalid ADR status")
        assertViolationContains("missing section: Context")
    }

    @Test
    fun `expired active waiver is rejected`() {
        write("docs/rules.md", "### KOT-001 — Rule\n")
        write("docs/waivers/README.md", "[WVR-0001](WVR-0001-test.md)\n")
        write("docs/waivers/WVR-0001-test.md", validExpiredWaiver())

        assertViolationContains("active waiver expired on 2026-08-31")
    }

    private fun assertNoViolations() {
        val violations = validate()
        assertTrue(violations.isEmpty(), violations.joinToString(separator = "\n"))
    }

    private fun assertViolationContains(message: String) {
        val violations = validate()
        assertTrue(violations.any { message in it.message }, violations.joinToString(separator = "\n"))
    }

    private fun validate(): List<DocumentationViolation> =
        DocumentationValidator(root, LocalDate.parse("2026-09-01")).validate()

    private fun write(
        relativePath: String,
        content: String,
    ) {
        val target = root.resolve(relativePath)
        Files.createDirectories(target.parent)
        Files.writeString(target, content)
    }

    private fun validAdr(): String =
        """
        # ADR 0001: Test

        - Status: accepted
        - Date: 2026-09-01
        - Issue: #1
        - Affected rules: `KOT-001`

        ## Context
        Context.
        ## Decision
        Decision.
        ## Rejected alternatives
        Rejected.
        ## Consequences
        Consequences.
        ## Enforcement impact
        Enforcement.
        ## Migration and rollback
        Migration.
        ## Related
        Related.
        """.trimIndent() + "\n"

    private fun validExpiredWaiver(): String =
        """
        # WVR-0001: Test

        - Status: active
        - Rule: `KOT-001`
        - Issue: #1
        - Owner: test owner
        - Created: 2026-08-01
        - Expires: 2026-08-31

        ## Exact scope
        Scope.
        ## Reason
        Reason.
        ## Risk and containment
        Risk.
        ## Removal condition
        Removal.
        ## Rejected alternatives
        Rejected.
        ## References
        References.
        """.trimIndent() + "\n"
}
