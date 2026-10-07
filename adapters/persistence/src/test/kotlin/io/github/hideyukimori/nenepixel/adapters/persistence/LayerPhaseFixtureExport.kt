package io.github.hideyukimori.nenepixel.adapters.persistence

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.HexFormat

/** Run only through the explicitly applied #145 measurement init script. */
internal object LayerPhaseFixtureExport {
    @JvmStatic
    fun main(arguments: Array<String>) {
        require(arguments.size == 1 && arguments.single().isNotBlank()) { "A sole output path is required" }
        val output = Path.of(arguments.single()).toAbsolutePath().normalize()
        require(Files.isDirectory(output.parent)) { "Output parent must already exist" }
        require(!Files.exists(output)) { "Output already exists" }
        val bytes = LayerPhaseFixture.verifiedProjectBytes()
        LayerPhaseFixture.verifyCandidate()
        Files.newOutputStream(output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { it.write(bytes) }
        check(Files.readAllBytes(output).contentEquals(bytes))
        val digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
        println("bytes=${bytes.size} sha256=$digest")
    }
}
