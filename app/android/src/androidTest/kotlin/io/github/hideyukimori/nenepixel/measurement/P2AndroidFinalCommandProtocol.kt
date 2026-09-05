package io.github.hideyukimori.nenepixel.measurement

internal object P2AndroidFinalCommandProtocol {
    const val WARMUP_ITERATIONS: Int = 5
    const val SAMPLES_PER_WORKLOAD: Int = 200
    const val WORKLOAD_COUNT: Int = 5
    const val RUN_INDEX: Int = 1
    const val PHYSICAL_PROFILE_ID: String = "NENE-P2-ALLDOCUBE-IPL80MP-A16-API36"
    const val CLEAN_LATENCY_SCHEMA: String = "nene-pixel-p2-android-clean-command-latency-v2"

    fun resolve(identity: P2AndroidRunIdentity): P2AndroidFinalCommandPlan {
        require(identity.candidateId == FINAL_PLAN.candidateId) {
            "Unknown final command candidate ID '${identity.candidateId}'."
        }
        check(identity.runIndex == FINAL_PLAN.runIndex) {
            "Final command run index for '${FINAL_PLAN.candidateId}' must be ${FINAL_PLAN.runIndex}."
        }
        validatePlan(FINAL_PLAN)
        return FINAL_PLAN
    }

    fun resolve(
        environment: P2AndroidMeasurementEnvironment,
        identity: P2AndroidRunIdentity,
    ): P2AndroidFinalCommandPlan = resolve(identity).also { plan -> validate(environment, identity, plan) }

    fun validate(
        environment: P2AndroidMeasurementEnvironment,
        identity: P2AndroidRunIdentity,
        plan: P2AndroidFinalCommandPlan,
    ) {
        check(resolve(identity) == plan) { "Final command plan does not match the run identity." }
        check(!environment.emulatorDetection.isEmulator) {
            "Final command evidence requires the physical profile."
        }
        check(!environment.auxiliaryEmulatorArgumentPresent) {
            "Final command evidence does not accept the auxiliary-emulator runner argument."
        }
        check(environment.profileId == PHYSICAL_PROFILE_ID) {
            "Final command evidence requires physical profile '$PHYSICAL_PROFILE_ID'."
        }
        check(environment.warmupIterations == plan.warmupIterations) {
            "Final command evidence requires exactly ${plan.warmupIterations} warmups."
        }
        check(environment.sampleCount == plan.samplesPerWorkload) {
            "Final command evidence requires exactly ${plan.samplesPerWorkload} samples per workload."
        }
        P2AndroidFinalCommandProfile.validateRuntime(environment.targetContext)
    }

    private fun validatePlan(plan: P2AndroidFinalCommandPlan) {
        check(plan.runIndex == RUN_INDEX)
        check(plan.schema == CLEAN_LATENCY_SCHEMA)
        check(plan.warmupIterations == WARMUP_ITERATIONS)
        check(plan.samplesPerWorkload == SAMPLES_PER_WORKLOAD)
        check(plan.specs.size == WORKLOAD_COUNT)
        check(plan.specs.map(P2CommandWorkloadSpec::kind) == P2CommandWorkloadKind.entries)
        check(
            plan.specs.all { spec ->
                spec.canvasWidth == plan.canvasWidth && spec.canvasHeight == plan.canvasHeight
            },
        )
        check(
            plan.totalSampleCount % P2AndroidPhysicalCheckpointPolicy.CHECKPOINT_INTERVAL == 0,
        ) { "Final command sample count must end on a physical checkpoint boundary." }
    }

    private val FINAL_PLAN: P2AndroidFinalCommandPlan =
        P2AndroidFinalCommandPlan(
            identity =
                P2AndroidFinalCommandPlan.Identity(
                    candidateId = "flat-packed-command-256-lane-separated-v1",
                    runIndex = RUN_INDEX,
                ),
            workload =
                P2AndroidFinalCommandPlan.Workload(
                    canvasWidth = 256,
                    canvasHeight = 256,
                    warmupIterations = WARMUP_ITERATIONS,
                    samplesPerWorkload = SAMPLES_PER_WORKLOAD,
                    schema = CLEAN_LATENCY_SCHEMA,
                ),
            output =
                P2AndroidFinalCommandPlan.Output(
                    outputIdentity = "device-lane-separated-flat-packed-command-256-run-01",
                    relativePath = "p2-measurements/p2-android-lane-separated-flat-packed-command-256-run-01.csv",
                    publicationPolicy = P2AndroidFinalCommandPlan.PublicationPolicy.FailIfExists,
                ),
        )
}
