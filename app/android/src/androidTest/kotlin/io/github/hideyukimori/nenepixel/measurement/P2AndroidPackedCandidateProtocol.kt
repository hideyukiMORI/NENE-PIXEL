package io.github.hideyukimori.nenepixel.measurement

import java.io.File

internal object P2AndroidPackedCandidateProtocol {
    const val CANVAS_EDGE: Int = 256
    const val PIXEL_COUNT: Int = CANVAS_EDGE * CANVAS_EDGE
    const val INITIAL_REVISION: Long = 0L
    const val SOURCE_RGBA: Int = 0x000000ff
    const val TARGET_RGBA: Int = -0x00ffff01
    const val WARMUP_ITERATIONS: Int = 5
    const val SAMPLES_PER_WORKLOAD: Int = 100
    const val PHYSICAL_PROFILE_ID: String = "NENE-P2-ALLDOCUBE-IPL80MP-A16-API36"
    const val RUN_CANDIDATE_ID: String = "packed-storage-candidate-comparison-v1"
    const val RUN_INDEX: Int = 1
    const val SCHEMA: String = "nene-pixel-p2-android-packed-candidate-comparison-v1"

    val specs: List<P2PackedCandidateSpec> =
        P2PackedCandidateKind.entries.flatMap { candidate ->
            P2PackedCandidateWorkload.entries.map { workload -> P2PackedCandidateSpec(candidate, workload) }
        }

    fun validate(
        environment: P2AndroidMeasurementEnvironment,
        identity: P2AndroidRunIdentity,
    ) {
        check(!environment.emulatorDetection.isEmulator) { "Packed candidate evidence requires a physical device." }
        check(!environment.auxiliaryEmulatorArgumentPresent) {
            "Packed candidate evidence does not accept the auxiliary-emulator runner argument."
        }
        check(environment.profileId == PHYSICAL_PROFILE_ID) {
            "Packed candidate evidence requires physical profile '$PHYSICAL_PROFILE_ID'."
        }
        check(environment.warmupIterations == WARMUP_ITERATIONS) {
            "Packed candidate evidence requires exactly $WARMUP_ITERATIONS warmups."
        }
        check(environment.sampleCount == SAMPLES_PER_WORKLOAD) {
            "Packed candidate evidence requires exactly $SAMPLES_PER_WORKLOAD samples per workload."
        }
        check(identity.candidateId == RUN_CANDIDATE_ID) { "Packed candidate run identity changed." }
        check(identity.runIndex == RUN_INDEX) { "Packed candidate run index changed." }
        P2AndroidFinalCommandProfile.validateRuntime(environment.targetContext)
    }

    fun outputFile(environment: P2AndroidMeasurementEnvironment): File =
        File(
            environment.targetContext.filesDir,
            "p2-measurements/p2-android-packed-candidate-comparison-run-01.csv",
        )
}

internal data class P2PackedCandidateSpec(
    val candidate: P2PackedCandidateKind,
    val workload: P2PackedCandidateWorkload,
)
