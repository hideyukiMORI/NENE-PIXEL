package io.github.hideyukimori.nenepixel.adapters.persistence

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** Explicit test-asset export only; rejects an existing output rather than replacing evidence. */
internal object LayerPhaseUnderlayFixtureExport {
    @JvmStatic
    fun main(arguments: Array<String>) {
        require(arguments.size == 1 && arguments.single().isNotBlank()) { "A sole output path is required" }
        val output = Path.of(arguments.single()).toAbsolutePath().normalize()
        require(Files.isDirectory(output.parent)) { "Output parent must already exist" }
        require(!Files.exists(output)) { "Output already exists" }
        val bytes = LayerPhaseUnderlayFixture.pngBytes()
        Files.newOutputStream(output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { it.write(bytes) }
        check(Files.readAllBytes(output).contentEquals(bytes))
        println("bytes=${bytes.size} sha256=${LayerPhaseUnderlayFixture.sha256(bytes)}")
        println("rgba_sha256=${LayerPhaseUnderlayFixture.rgbaSha256(LayerPhaseUnderlayFixture.rgbaPixels())}")
    }
}
