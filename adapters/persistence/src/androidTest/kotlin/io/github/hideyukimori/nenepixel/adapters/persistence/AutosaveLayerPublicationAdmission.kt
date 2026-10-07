package io.github.hideyukimori.nenepixel.adapters.persistence

import android.content.Context
import android.os.Bundle
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatBytes
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatCodec
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatResult
import java.io.DataInputStream
import java.io.File
import java.security.MessageDigest

/** Own-APK checks supplement the host's complete app preservation and artifact admission. */
internal class AutosaveLayerPublicationAdmission private constructor(
    private val context: Context,
    private val identity: List<Pair<String, String>>,
    private val prefix: String,
) {
    fun reserveOutputs(): AutosavePublicationEvidenceOutputReservation {
        val line = "P4_LAYER_PUBLICATION_IDENTITY " + identity.joinToString(" ") { (key, value) -> "$key=$value" }
        val reservation = AutosaveLayerPublicationOutputs.reserve(File(context.filesDir, prefix), line)
        InstrumentationRegistry.getInstrumentation().sendStatus(
            3,
            Bundle().apply { putString("p4LayerPublicationIdentity", line) },
        )
        return reservation
    }

    fun createWorkDirectory(): File =
        File(context.noBackupFilesDir, prefix).also {
            check(it.mkdir()) { "Layer publication work directory already exists or cannot be created" }
        }

    fun maximumDocument(): DocumentState {
        val bytes =
            InstrumentationRegistry.getInstrumentation().context.assets.open("maximum-layered.nenepixel").use {
                val bytes = ByteArray(1_182_862)
                DataInputStream(it).readFully(bytes)
                check(it.read() == -1 && hash(bytes) == FIXTURE_HASH)
                bytes
            }
        val source = accepted(ProjectFormatBytes.create(bytes))
        val decoded = accepted(ProjectFormatCodec.decode(source))
        check(decoded is DocumentImportSource.Current)
        return decoded.document
    }

    companion object {
        const val SCHEMA: String = "nene-pixel-p4-layer-publication-device-v1"
        const val MAXIMUM_CANDIDATE_BYTES: Int = 1_182_885
        private const val FIXTURE_HASH: String = "165f62d180533849ce1a4ef1625cd3971e445f2dca60ef7b9b46fedaafa0b3ec"
        private const val SELF_PACKAGE: String = "io.github.hideyukimori.nenepixel.adapters.persistence.test"
        private val identifier = Regex("[a-z0-9][a-z0-9-]{2,63}")
        private val digest = Regex("[0-9a-f]{64}")
        private val commit = Regex("[0-9a-f]{40}")

        fun read(context: Context): AutosaveLayerPublicationAdmission {
            val arguments = InstrumentationRegistry.getArguments()
            check(arguments.getString("p4LayerCollect") == "publication-v1")
            check(arguments.getString("p4LayerPreserved") == "true")
            check(context.packageName == SELF_PACKAGE)
            check(InstrumentationRegistry.getInstrumentation().context.packageName == SELF_PACKAGE)
            check(Process.myUid() == context.applicationInfo.uid)
            val identity = commonIdentity(arguments)
            val apkHash = arguments.required("p4LayerPublicationApkSha256", digest)
            check(fileHash(File(context.applicationInfo.sourceDir)) == apkHash)
            val prefix = "p4-layer-publication-${identity.single { it.first == "preflight_sha256" }.second.take(12)}"
            return AutosaveLayerPublicationAdmission(context, identity + runtimeFacts(context, prefix, apkHash), prefix)
        }

        private fun runtimeFacts(
            context: Context,
            prefix: String,
            apkHash: String,
        ): List<Pair<String, String>> {
            val processId = Process.myPid()
            val processStart = Process.getStartElapsedRealtime()
            check(processId > 0 && processStart > 0)
            return listOf(
                "publication_apk_sha256" to apkHash,
                "process_id" to processId.toString(),
                "process_start_elapsed_realtime_ms" to processStart.toString(),
                "target_package" to context.packageName,
                "target_uid" to Process.myUid().toString(),
                "profile" to "NENE-P2-ALLDOCUBE-IPL80MP-A16-API36",
                "fixture_sha256" to FIXTURE_HASH,
                "maximum_candidate_bytes" to MAXIMUM_CANDIDATE_BYTES.toString(),
                "minimum_candidate_bytes" to "85",
                "warmup_count_per_group" to "5",
                "sample_count_per_group" to "20",
                "journal_rows" to "54",
                "worker_timeout_seconds" to "60",
                "native_timeout_seconds" to "300",
                "sample_anomaly_nanos" to "5000000000",
                "work_relative_path" to "no_backup/$prefix",
                "report_relative_path" to "files/$prefix",
            )
        }

        private fun commonIdentity(arguments: Bundle): List<Pair<String, String>> =
            listOf(
                "protocol_id" to
                    arguments.required("p4LayerProtocolId", Regex("nene-pixel-p4-layer-phase-verification-v1")),
                "experiment_id" to arguments.required("p4LayerExperimentId", identifier),
                "preflight_sha256" to arguments.required("p4LayerPreflightSha256", digest),
                "preservation_sha256" to arguments.required("p4LayerPreservationSha256", digest),
                "session" to arguments.required("p4LayerSession", identifier),
                "slot_id" to arguments.required("p4LayerSlotId", Regex("publication-layers16-candidate")),
                "artifact_role" to arguments.required("p4LayerArtifactRole", Regex("candidate")),
                "measurement_build_commit" to arguments.required("p4LayerBuildCommit", commit),
                "production_commit" to arguments.required("p4LayerProductionCommit", commit),
                "app_apk_sha256" to arguments.required("p4LayerAppApkSha256", digest),
                "test_apk_sha256" to arguments.required("p4LayerTestApkSha256", digest),
            )

        private fun Bundle.required(
            name: String,
            pattern: Regex,
        ): String =
            checkNotNull(getString(name)?.takeIf(pattern::matches)) { "Invalid layer publication argument $name" }

        private fun <T> accepted(result: ProjectFormatResult<T>): T =
            when (result) {
                is ProjectFormatResult.Accepted -> result.value
                is ProjectFormatResult.Rejected -> error("Layer fixture rejected: ${result.rejection}")
            }

        private fun fileHash(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var count = input.read(buffer)
                while (count != -1) {
                    digest.update(buffer, 0, count)
                    count = input.read(buffer)
                }
            }
            return hex(digest.digest())
        }

        private fun hash(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

        private fun hex(bytes: ByteArray): String =
            bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}
