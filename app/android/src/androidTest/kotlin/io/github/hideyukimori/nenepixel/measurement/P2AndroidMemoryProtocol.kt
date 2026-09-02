package io.github.hideyukimori.nenepixel.measurement

import android.app.ActivityManager
import android.os.Build
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID

internal data class P2AndroidMemoryRunIdentity(
    val run: P2AndroidRunIdentity,
    val batchId: String,
    val processId: Int,
    val processStartElapsedRealtimeMillis: Long,
)

internal object P2AndroidMemoryProtocol {
    fun runIdentity(): P2AndroidMemoryRunIdentity {
        val identity = P2AndroidRunIdentity.fromRunnerArguments()
        val batchId = requiredCanonicalBatchId()
        validateCommon(identity)
        check(identity.runIndex in RUN_INDEX_RANGE) {
            "Retained-memory run index must be between 1 and $RUN_COUNT."
        }
        return P2AndroidMemoryRunIdentity(
            run = identity,
            batchId = batchId,
            processId = Process.myPid(),
            processStartElapsedRealtimeMillis = Process.getStartElapsedRealtime(),
        )
    }

    fun aggregateIdentity(): P2AndroidMemoryRunIdentity {
        val identity = P2AndroidRunIdentity.fromRunnerArguments()
        val batchId = requiredCanonicalBatchId()
        validateCommon(identity)
        check(identity.runIndex == RUN_COUNT) {
            "Aggregate-only invocation uses collection run index $RUN_COUNT."
        }
        return P2AndroidMemoryRunIdentity(
            run = identity,
            batchId = batchId,
            processId = Process.myPid(),
            processStartElapsedRealtimeMillis = Process.getStartElapsedRealtime(),
        )
    }

    fun validateEnvironment(environment: P2AndroidMeasurementEnvironment) {
        check(!environment.emulatorDetection.isEmulator) {
            "Retained-memory evidence requires the physical profile."
        }
        check(environment.profileId == PHYSICAL_PROFILE_ID) {
            "Retained-memory evidence requires physical profile '$PHYSICAL_PROFILE_ID'."
        }
        check(Build.FINGERPRINT == BUILD_FINGERPRINT) {
            "Retained-memory evidence requires the pre-fixed Android build fingerprint."
        }
        check(Build.VERSION.SECURITY_PATCH == SECURITY_PATCH) {
            "Retained-memory evidence requires security patch '$SECURITY_PATCH'."
        }
        check(Build.MANUFACTURER == MANUFACTURER && Build.MODEL == MODEL && Build.PRODUCT == PRODUCT) {
            "Retained-memory evidence requires the pre-fixed manufacturer/model/product."
        }
        check(Build.HARDWARE == HARDWARE && Build.VERSION.SDK_INT == API_LEVEL) {
            "Retained-memory evidence requires the pre-fixed hardware and API level."
        }
        check(Build.SUPPORTED_ABIS.joinToString("|") == SUPPORTED_ABIS) {
            "Retained-memory evidence requires the pre-fixed ABI set."
        }
        val activityManager = environment.targetContext.getSystemService(ActivityManager::class.java)
        check(Runtime.getRuntime().maxMemory() == RUNTIME_MAX_MEMORY_BYTES) {
            "Retained-memory evidence requires the pre-fixed Runtime.maxMemory."
        }
        check(activityManager.memoryClass == MEMORY_CLASS_MIB) {
            "Retained-memory evidence requires the pre-fixed memoryClass."
        }
    }

    private fun validateCommon(identity: P2AndroidRunIdentity) {
        check(identity.candidateId == CANDIDATE_ID) {
            "Retained-memory candidate ID must be '$CANDIDATE_ID'."
        }
    }

    private fun requiredCanonicalBatchId(): String {
        val arguments = InstrumentationRegistry.getArguments()
        val raw =
            requireNotNull(arguments.getString(BATCH_ID_ARGUMENT)?.trim()?.takeIf(String::isNotEmpty)) {
                "Runner argument '$BATCH_ID_ARGUMENT' is required for retained-memory evidence."
            }
        val parsed = runCatching { UUID.fromString(raw) }.getOrNull()
        return requireNotNull(parsed?.toString()?.takeIf { canonical -> canonical == raw }) {
            "Runner argument '$BATCH_ID_ARGUMENT' must be a canonical lowercase UUID."
        }
    }

    const val INVOCATION_SCHEMA: String = "nene-pixel-p2-android-memory-invocation-v1"
    const val AGGREGATE_SCHEMA: String = "nene-pixel-p2-android-memory-aggregate-v1"
    const val CANDIDATE_ID: String = "current-canonical-object-materialized-v1"
    const val WORKLOAD_ID: String = "retained_projection_256_square_h64_t8n"
    const val PHYSICAL_PROFILE_ID: String = "NENE-P2-ALLDOCUBE-IPL80MP-A16-API36"
    const val BUILD_FINGERPRINT: String =
        "ALLDOCUBE/iPlay80miniPro/T830:16/BP2A.250605.031.A3/94110:user/release-keys"
    const val SECURITY_PATCH: String = "2026-06-05"
    const val MANUFACTURER: String = "ALLDOCUBE"
    const val MODEL: String = "iPlay80miniPro"
    const val PRODUCT: String = "iPlay80miniPro"
    const val HARDWARE: String = "ums9360_1h10"
    const val API_LEVEL: Int = 36
    const val SUPPORTED_ABIS: String = "arm64-v8a"
    const val RUNTIME_MAX_MEMORY_BYTES: Long = 268_435_456L
    const val MEMORY_CLASS_MIB: Int = 256
    const val BATCH_ID_ARGUMENT: String = "nene.p2.memoryBatchId"
    const val RUN_COUNT: Int = 5
    const val CANVAS_EDGE: Int = 256
    const val PIXEL_COUNT: Int = 65_536
    const val HISTORY_ENTRIES: Int = 64
    const val CHANGE_COUNT_PER_ENTRY: Int = 8_192
    const val TOTAL_RETAINED_CHANGES: Long = 524_288L
    const val BLOCK_COUNT: Int = 8
    const val BLOCK_HEIGHT: Int = 32
    const val FINAL_REVISION: Long = 64L
    const val OPAQUE_WHITE_ARGB: Int = -0x1
    const val OPAQUE_RED_ARGB: Int = -0x10000
    const val EXPECTED_PROJECTION_DIGEST: String =
        "F1100EF5EA1ACC83FB6A15F08ECBDE5277293C83707B2C13B5F8FC963E7A0F74"
    const val EXPECTED_ENTRY_DESCRIPTOR_DIGEST: String =
        "F59A52FA7C8A66D9C7676615F090280B4E32725C23E8A0B9C28E551C6FF8BFDD"
    const val CANVAS_DESCRIPTOR: String = "256x256"
    const val RETAINED_WORKLOAD_DESCRIPTOR: String = "N=65536;H=64;C=8192;T=524288"
    const val ENTRY_SEQUENCE_DESCRIPTOR: String =
        "block=i_mod_8;target=red_when_i_div_8_even_else_white"
    const val GC_PROTOCOL_DESCRIPTOR: String =
        "two Runtime.gc/System.runFinalization passes per memory checkpoint"
    const val CAPTURE_SEQUENCE_DESCRIPTOR: String =
        "validate;before_baseline;preload;baseline_post_gc;fixture_verify_projection;" +
            "retained_post_gc;after_retained;validate;atomic_publish"
    const val RETAINED_OWNER_DESCRIPTOR: String =
        "final DocumentState, 64 opaque public ChangeSets, and canonical materialized projection only"
    const val PROJECTION_BOUNDARY_DESCRIPTOR: String =
        "release-absent debug bridge delegates to PixelSnapshot.toRenderedPixels; storage remains opaque"
    const val PRIVATE_PATCH_BOUNDARY_DESCRIPTOR: String =
        "patch counts, ordering, inverse replay, and logical storage remain isolated host evidence"
    const val CHURN_STATUS: String = "not_evaluated_cap_policy_unselected"
    const val BASELINE_BOUNDARY: String =
        "two-pass post-GC process baseline after fixed preload and before retained fixture creation"
    const val RETAINED_BOUNDARY: String =
        "two-pass post-GC process memory while retaining final state, 64 ChangeSets, and canonical projection"
    const val SUMMARY_BOUNDARY: String =
        "public retained fixture and opaque projection correctness verified outside both memory checkpoints"
    val RUN_INDEX_RANGE: IntRange = 1..RUN_COUNT
}
