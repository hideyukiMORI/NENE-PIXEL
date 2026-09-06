package io.github.hideyukimori.nenepixel.measurement

internal object P2AndroidFinalCommandProtocol {
    const val WARMUP_ITERATIONS: Int = 5
    const val SAMPLES_PER_WORKLOAD: Int = 200
    const val WORKLOAD_COUNT: Int = 5
    const val M2_WORKLOAD_COUNT: Int = 6
    const val RUN_INDEX: Int = 1
    const val PHYSICAL_PROFILE_ID: String = "NENE-P2-ALLDOCUBE-IPL80MP-A16-API36"
    const val CLEAN_LATENCY_SCHEMA: String = "nene-pixel-p2-android-clean-command-latency-v2"
    const val M2_LATENCY_SCHEMA: String = "nene-pixel-m2-android-command-latency-v2"

    fun resolve(identity: P2AndroidRunIdentity): P2AndroidFinalCommandPlan {
        val plan = PLANS_BY_CANDIDATE[identity.candidateId]
        requireNotNull(plan) {
            "Unknown final command candidate ID '${identity.candidateId}'."
        }
        check(identity.runIndex == plan.runIndex) {
            "Final command run index for '${plan.candidateId}' must be ${plan.runIndex}."
        }
        validatePlan(plan)
        return plan
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
        val m2Plan = plan.candidateId == M2_CANDIDATE_ID
        check(plan.schema == if (m2Plan) M2_LATENCY_SCHEMA else CLEAN_LATENCY_SCHEMA)
        check(plan.warmupIterations == WARMUP_ITERATIONS)
        check(plan.samplesPerWorkload == SAMPLES_PER_WORKLOAD)
        check(plan.specs.size == if (m2Plan) M2_WORKLOAD_COUNT else WORKLOAD_COUNT)
        check(
            plan.specs.map(P2CommandWorkloadSpec::kind) ==
                if (m2Plan) P2CommandWorkloadCatalog.m2Kinds else P2CommandWorkloadCatalog.legacyKinds,
        )
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
                    kinds = P2CommandWorkloadCatalog.legacyKinds,
                ),
            output =
                P2AndroidFinalCommandPlan.Output(
                    outputIdentity = "device-lane-separated-flat-packed-command-256-run-01",
                    relativePath = "p2-measurements/p2-android-lane-separated-flat-packed-command-256-run-01.csv",
                    publicationPolicy = P2AndroidFinalCommandPlan.PublicationPolicy.FailIfExists,
                ),
        )

    private val M2_PLAN: P2AndroidFinalCommandPlan =
        P2AndroidFinalCommandPlan(
            identity = P2AndroidFinalCommandPlan.Identity(M2_CANDIDATE_ID, RUN_INDEX),
            workload =
                P2AndroidFinalCommandPlan.Workload(
                    canvasWidth = 256,
                    canvasHeight = 256,
                    warmupIterations = WARMUP_ITERATIONS,
                    samplesPerWorkload = SAMPLES_PER_WORKLOAD,
                    schema = M2_LATENCY_SCHEMA,
                    kinds = P2CommandWorkloadCatalog.m2Kinds,
                ),
            output =
                P2AndroidFinalCommandPlan.Output(
                    outputIdentity = "m2-production-command-256-lane-separated-run-01",
                    relativePath = "m2-measurements/m2-production-command-256-lane-separated-run-01.csv",
                    publicationPolicy = P2AndroidFinalCommandPlan.PublicationPolicy.FailIfExists,
                ),
        )

    private val PLANS_BY_CANDIDATE: Map<String, P2AndroidFinalCommandPlan> =
        listOf(FINAL_PLAN, M2_PLAN).associateBy(P2AndroidFinalCommandPlan::candidateId)

    private const val M2_CANDIDATE_ID: String = "m2-production-command-256-lane-separated-v2"
}
