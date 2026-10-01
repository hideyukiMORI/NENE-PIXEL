package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class FakePngImportPortTest {
    @Test
    fun `default handler cancels and counts the call`() {
        val port = FakePngImportPort()

        val outcome = runBlocking { port.pick() }

        assertSame(PngImportOutcome.Cancelled, outcome)
        assertEquals(1, port.calls)
    }

    @Test
    fun `handler decides the picked raster`() {
        val raster = raster()
        val port = FakePngImportPort()
        port.handler = { PngImportOutcome.Picked(raster) }

        val outcome = runBlocking { port.pick() }

        assertEquals(PngImportOutcome.Picked(raster), outcome)
        assertSame(raster, (outcome as PngImportOutcome.Picked).raster)
    }

    @Test
    fun `handler decides rejection and every pick is counted`() {
        val port = FakePngImportPort()
        port.handler = { PngImportOutcome.Rejected(PngImportSourceRejection.TooManyPixels) }

        val outcomes = runBlocking { listOf(port.pick(), port.pick()) }

        val expected = PngImportOutcome.Rejected(PngImportSourceRejection.TooManyPixels)
        assertEquals(listOf(expected, expected), outcomes)
        assertEquals(2, port.calls)
    }

    private fun raster(): ImportRaster {
        val result = ImportRaster.create(2, 2, IntArray(4))
        check(result is DomainValueResult.Created) { "expected Created, got $result" }
        return result.value
    }
}
