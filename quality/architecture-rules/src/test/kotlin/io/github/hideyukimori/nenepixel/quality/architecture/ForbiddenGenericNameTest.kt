package io.github.hideyukimori.nenepixel.quality.architecture

import dev.detekt.api.Config
import dev.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ForbiddenGenericNameTest {
    private val subject = ForbiddenGenericName(Config.empty)

    @Test
    fun `domain-specific names are accepted`() {
        assertTrue(subject.lint(fixture("compliant-name.fixture")).isEmpty())
    }

    @Test
    fun `generic type suffix is rejected`() {
        val findings = subject.lint(fixture("forbidden-type-name.fixture"))

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("LayerManager"))
    }

    @Test
    fun `generic package segment is rejected`() {
        val findings = subject.lint(fixture("forbidden-package-name.fixture"))

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("helpers"))
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/architecture-fixtures/$name")) {
            "Missing architecture fixture: $name"
        }.readText()
}
