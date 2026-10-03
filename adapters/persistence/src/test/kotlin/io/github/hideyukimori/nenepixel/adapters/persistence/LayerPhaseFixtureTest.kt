package io.github.hideyukimori.nenepixel.adapters.persistence

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

internal class LayerPhaseFixtureTest {
    @Test
    fun `checked maximum project is exactly the canonical drawable fixture`() {
        val expected = LayerPhaseFixture.verifiedProjectBytes()
        val checked = Files.readAllBytes(checkedAsset())

        assertEquals(1_182_862, checked.size)
        assertArrayEquals(expected, checked)
        LayerPhaseFixture.verifyDecoded(checked)
        LayerPhaseFixture.verifyCandidate()
    }

    private fun checkedAsset(): Path {
        val relative = Path.of("docs/quality/fixtures/p4-layer-phase/maximum-layered.nenepixel")
        return generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .map { it.resolve(relative) }
            .firstOrNull(Files::isRegularFile)
            ?: error("Checked #145 layer fixture is missing")
    }
}
