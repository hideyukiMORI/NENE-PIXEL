package io.github.hideyukimori.nenepixel.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.extension

internal class BaselineProfileArtifactValidator(
    private val repositoryDirectory: Path,
) {
    fun validate(): List<String> =
        buildList {
            validateManualProfile(this)
            validateGeneratedProfile(this)
        }

    private fun validateManualProfile(violations: MutableList<String>) {
        val manualProfile = repositoryDirectory.resolve(MANUAL_PROFILE)
        if (Files.exists(manualProfile)) {
            violations.add("QLT-004 requires the hand-written Baseline Profile to be absent: $MANUAL_PROFILE")
        }
    }

    private fun validateGeneratedProfile(violations: MutableList<String>) {
        val generatedDirectory = repositoryDirectory.resolve(GENERATED_DIRECTORY)
        val profiles =
            if (Files.isDirectory(generatedDirectory)) {
                Files.walk(generatedDirectory).use { paths ->
                    paths
                        .filter(Files::isRegularFile)
                        .filter { path -> path.extension.equals("txt", ignoreCase = true) }
                        .sorted()
                        .toList()
                }
            } else {
                emptyList()
            }
        if (profiles.size != EXPECTED_PROFILE_COUNT) {
            violations.add(
                "QLT-004 requires exactly one generated Baseline Profile under $GENERATED_DIRECTORY; " +
                    "found ${profiles.size}.",
            )
        } else {
            validateProfile(profiles.single(), violations)
        }
    }

    private fun validateProfile(
        profile: Path,
        violations: MutableList<String>,
    ) {
        val bytes = Files.readAllBytes(profile)
        if (bytes.isEmpty() || bytes.all(::isAsciiWhitespace)) {
            violations.add("QLT-004 requires the generated Baseline Profile to be non-empty.")
        }

        val hashPath = repositoryDirectory.resolve(HASH_FILE)
        if (!Files.isRegularFile(hashPath)) {
            violations.add("QLT-004 requires the generated Baseline Profile hash: $HASH_FILE")
        } else {
            validateHash(bytes, hashPath, violations)
        }
    }

    private fun validateHash(
        bytes: ByteArray,
        hashPath: Path,
        violations: MutableList<String>,
    ) {
        val recordedHash = Files.readString(hashPath).trim()
        val actualHash = bytes.sha256()
        when {
            !HASH_PATTERN.matches(recordedHash) -> {
                violations.add("QLT-004 requires $HASH_FILE to contain one lowercase SHA-256 value.")
            }

            recordedHash != actualHash -> {
                violations.add(
                    "QLT-004 detected generated Baseline Profile drift: " +
                        "expected $recordedHash, found $actualHash.",
                )
            }
        }
    }

    private fun isAsciiWhitespace(value: Byte): Boolean =
        value == SPACE || value == TAB || value == LINE_FEED || value == CARRIAGE_RETURN

    private fun ByteArray.sha256(): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(this)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        const val MANUAL_PROFILE = "app/android/src/main/baseline-prof.txt"
        const val GENERATED_DIRECTORY = "app/android/src/main/generated/baselineProfiles"
        const val HASH_FILE = "app/android/src/main/generated/baselineProfiles.sha256"
        const val EXPECTED_PROFILE_COUNT = 1
        val HASH_PATTERN = Regex("[0-9a-f]{64}")
        const val SPACE: Byte = 32
        const val TAB: Byte = 9
        const val LINE_FEED: Byte = 10
        const val CARRIAGE_RETURN: Byte = 13
    }
}
