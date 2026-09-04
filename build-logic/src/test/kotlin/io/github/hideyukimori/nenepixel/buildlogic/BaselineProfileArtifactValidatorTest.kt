package io.github.hideyukimori.nenepixel.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

internal class BaselineProfileArtifactValidatorTest {
    @TempDir
    lateinit var repositoryDirectory: Path

    @Test
    fun `one nonempty generated profile with matching hash is accepted`() {
        val profile = writeProfile(PROFILE_CONTENT)
        writeHash(Files.readAllBytes(profile).sha256())

        assertTrue(validate().isEmpty())
    }

    @Test
    fun `manual profile and generated profile drift are rejected`() {
        val profile = writeProfile(PROFILE_CONTENT)
        writeHash(Files.readAllBytes(profile).sha256())
        writeFile(MANUAL_PROFILE, PROFILE_CONTENT)
        Files.writeString(profile, "$PROFILE_CONTENT# drift\n")

        val violations = validate()

        assertContains(violations, "hand-written Baseline Profile")
        assertContains(violations, "generated Baseline Profile drift")
    }

    @Test
    fun `missing generated profile and hash are rejected`() {
        val violations = validate()

        assertEquals(1, violations.size)
        assertContains(violations, "exactly one generated Baseline Profile")
    }

    @Test
    fun `multiple generated profiles are rejected`() {
        writeProfile(PROFILE_CONTENT)
        writeFile("$GENERATED_DIRECTORY/secondary.txt", PROFILE_CONTENT)

        assertContains(validate(), "found 2")
    }

    @Test
    fun `empty profile and malformed hash are rejected`() {
        writeProfile(" \r\n\t")
        writeHash("not-a-sha256")

        val violations = validate()

        assertContains(violations, "to be non-empty")
        assertContains(violations, "one lowercase SHA-256 value")
    }

    private fun validate(): List<String> = BaselineProfileArtifactValidator(repositoryDirectory).validate()

    private fun writeProfile(content: String): Path = writeFile("$GENERATED_DIRECTORY/baseline-prof.txt", content)

    private fun writeHash(content: String) {
        writeFile(HASH_FILE, "$content\n")
    }

    private fun writeFile(
        relativePath: String,
        content: String,
    ): Path {
        val file = repositoryDirectory.resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
        return file
    }

    private fun ByteArray.sha256(): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(this)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun assertContains(
        violations: List<String>,
        message: String,
    ) {
        assertTrue(violations.any { message in it }, violations.joinToString(separator = "\n"))
    }

    private companion object {
        const val GENERATED_DIRECTORY = "app/android/src/main/generated/baselineProfiles"
        const val MANUAL_PROFILE = "app/android/src/main/baseline-prof.txt"
        const val HASH_FILE = "app/android/src/main/generated/baselineProfiles.sha256"
        const val PROFILE_CONTENT =
            "HSPLio/github/hideyukimori/nenepixel/MainActivity;" +
                "->onCreate(Landroid/os/Bundle;)V\n"
    }
}
