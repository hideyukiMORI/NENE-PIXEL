package io.github.hideyukimori.nenepixel.measurement

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest

/** Local fail-closed checks supplement the host's verified manifest and preservation record. */
internal data class P4LayerRunAdmission(
    val protocol: P4LayerProtocolEvidence,
    val preservation: P4LayerPreservationEvidence,
    val slot: P4LayerRunSlot,
    val artifact: P4LayerArtifactIdentity,
) {
    val protocolId: String get() = protocol.protocolId
    val experimentId: String get() = protocol.experimentId
    val preflightSha256: String get() = preservation.preflightSha256
    val preservationSha256: String get() = preservation.preservationSha256
    val session: String get() = preservation.session
    val slotId: String get() = slot.slotId
    val artifactRole: String get() = slot.artifactRole
    val buildCommit: String get() = artifact.buildCommit
    val productionCommit: String get() = artifact.productionCommit
    val appApkSha256: String get() = artifact.appApkSha256
    val testApkSha256: String get() = artifact.testApkSha256

    fun reportFields(): List<Pair<String, String>> =
        listOf(
            "protocol_id" to protocolId,
            "experiment_id" to experimentId,
            "preflight_sha256" to preflightSha256,
            "preservation_sha256" to preservationSha256,
            "session" to session,
            "slot_id" to slotId,
            "artifact_role" to artifactRole,
            "measurement_build_commit" to buildCommit,
            "production_commit" to productionCommit,
            "app_apk_sha256" to appApkSha256,
            "test_apk_sha256" to testApkSha256,
        )

    companion object {
        const val PROTOCOL: String = "nene-pixel-p4-layer-phase-verification-v1"

        fun read(): P4LayerRunAdmission {
            val arguments = InstrumentationRegistry.getArguments()
            check(arguments.getString("p4LayerPreserved") == "true")
            val admission =
                P4LayerRunAdmission(
                    protocol = arguments.protocolEvidence(),
                    preservation = arguments.preservationEvidence(),
                    slot = arguments.runSlot(),
                    artifact = arguments.artifactIdentity(),
                )
            admission.verifyInstalledIdentity()
            return admission
        }

        private fun Bundle.protocolEvidence(): P4LayerProtocolEvidence =
            P4LayerProtocolEvidence(
                protocolId = required("p4LayerProtocolId", Regex(PROTOCOL)),
                experimentId = required("p4LayerExperimentId", identifier),
            )

        private fun Bundle.preservationEvidence(): P4LayerPreservationEvidence =
            P4LayerPreservationEvidence(
                preflightSha256 = required("p4LayerPreflightSha256", hash),
                preservationSha256 = required("p4LayerPreservationSha256", hash),
                session = required("p4LayerSession", identifier),
            )

        private fun Bundle.runSlot(): P4LayerRunSlot =
            P4LayerRunSlot(
                slotId = required("p4LayerSlotId", identifier),
                artifactRole =
                    required(
                        "p4LayerArtifactRole",
                        Regex("baseline_single|baseline_layers16|baseline_underlay|candidate"),
                    ),
            )

        private fun Bundle.artifactIdentity(): P4LayerArtifactIdentity =
            P4LayerArtifactIdentity(
                buildCommit = required("p4LayerBuildCommit", commit),
                productionCommit = required("p4LayerProductionCommit", commit),
                appApkSha256 = required("p4LayerAppApkSha256", hash),
                testApkSha256 = required("p4LayerTestApkSha256", hash),
            )

        private val identifier = Regex("[a-z0-9][a-z0-9-]{2,63}")
        private val hash = Regex("[0-9a-f]{64}")
        private val commit = Regex("[0-9a-f]{40}")

        private fun Bundle.required(
            name: String,
            pattern: Regex,
        ): String = checkNotNull(getString(name)?.takeIf(pattern::matches)) { "Invalid runner argument $name" }
    }

    private fun verifyInstalledIdentity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        check(target.packageName == "io.github.hideyukimori.nenepixel")
        check(File(target.noBackupFilesDir, "p4-user-preservation/$session/original").isDirectory)
        check(fileSha256(File(target.applicationInfo.sourceDir)) == appApkSha256) { "Installed app APK differs" }
        check(fileSha256(File(instrumentation.context.applicationInfo.sourceDir)) == testApkSha256) {
            "Installed test/provider APK differs"
        }
    }

    private fun fileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                check(count > 0)
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}

internal data class P4LayerProtocolEvidence(
    val protocolId: String,
    val experimentId: String,
)

internal data class P4LayerPreservationEvidence(
    val preflightSha256: String,
    val preservationSha256: String,
    val session: String,
)

internal data class P4LayerRunSlot(
    val slotId: String,
    val artifactRole: String,
)

internal data class P4LayerArtifactIdentity(
    val buildCommit: String,
    val productionCommit: String,
    val appApkSha256: String,
    val testApkSha256: String,
)
