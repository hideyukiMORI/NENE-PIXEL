package io.github.hideyukimori.nenepixel.measurement

internal object P2AndroidFinalCommandProtocol {
    const val CANVAS_EDGE: Int = 256
    const val WARMUP_ITERATIONS: Int = 5
    const val SAMPLES_PER_WORKLOAD: Int = 200
    const val WORKLOAD_COUNT: Int = 5
    const val TOTAL_SAMPLE_COUNT: Int = WORKLOAD_COUNT * SAMPLES_PER_WORKLOAD
    const val CHECKPOINT_COUNT: Int = 42
    const val RUN_INDEX: Int = 1
    const val CANDIDATE_ID: String = "current-canonical-command-256-square"
    const val PHYSICAL_PROFILE_ID: String = "NENE-P2-ALLDOCUBE-IPL80MP-A16-API36"

    val specs: List<P2CommandWorkloadSpec>
        get() = P2CommandWorkloadCatalog.finalCurrentSpecs

    val workloadNames: List<String>
        get() = specs.map { spec -> spec.kind.metricName }

    fun validate(
        environment: P2AndroidMeasurementEnvironment,
        identity: P2AndroidRunIdentity,
    ) {
        check(!environment.emulatorDetection.isEmulator) {
            "Final command evidence requires the physical profile."
        }
        check(environment.profileId == PHYSICAL_PROFILE_ID) {
            "Final command evidence requires physical profile '$PHYSICAL_PROFILE_ID'."
        }
        check(environment.warmupIterations == WARMUP_ITERATIONS) {
            "Final command evidence requires exactly $WARMUP_ITERATIONS warmups."
        }
        check(environment.sampleCount == SAMPLES_PER_WORKLOAD) {
            "Final command evidence requires exactly $SAMPLES_PER_WORKLOAD samples per workload."
        }
        check(identity.candidateId == CANDIDATE_ID) {
            "Final command candidate ID must be '$CANDIDATE_ID'."
        }
        check(identity.runIndex == RUN_INDEX) { "Final command run index must be $RUN_INDEX." }
        check(specs.size == WORKLOAD_COUNT)
        check(specs.map(P2CommandWorkloadSpec::kind) == P2CommandWorkloadKind.entries)
        check(specs.all { spec -> spec.canvasEdge == CANVAS_EDGE })
    }
}
