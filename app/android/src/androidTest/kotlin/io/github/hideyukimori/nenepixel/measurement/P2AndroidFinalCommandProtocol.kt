package io.github.hideyukimori.nenepixel.measurement

internal object P2AndroidFinalCommandProtocol {
    const val WARMUP_ITERATIONS: Int = 5
    const val SAMPLES_PER_WORKLOAD: Int = 200
    const val WORKLOAD_COUNT: Int = 5
    const val RUN_INDEX: Int = 1
    const val PHYSICAL_PROFILE_ID: String = "NENE-P2-ALLDOCUBE-IPL80MP-A16-API36"
    const val SCHEMA: String = "nene-pixel-p2-android-final-command-measurement-v1"

    fun resolve(identity: P2AndroidRunIdentity): P2AndroidFinalCommandPlan {
        val plan =
            requireNotNull(PLANS_BY_CANDIDATE_ID[identity.candidateId]) {
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
        check(plan.schema == SCHEMA)
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

    private val PLANS_BY_CANDIDATE_ID: Map<String, P2AndroidFinalCommandPlan> =
        listOf(
            P2AndroidFinalCommandPlan(
                identity =
                    P2AndroidFinalCommandPlan.Identity(
                        candidateId = "current-canonical-command-256-square",
                        runIndex = RUN_INDEX,
                    ),
                workload =
                    P2AndroidFinalCommandPlan.Workload(
                        canvasWidth = 256,
                        canvasHeight = 256,
                        warmupIterations = WARMUP_ITERATIONS,
                        samplesPerWorkload = SAMPLES_PER_WORKLOAD,
                        schema = SCHEMA,
                    ),
                output =
                    P2AndroidFinalCommandPlan.Output(
                        outputIdentity = "device-core",
                        relativePath = "p2-measurements/p2-android-final-command-measurement.csv",
                        publicationPolicy = P2AndroidFinalCommandPlan.PublicationPolicy.OverwriteExisting,
                    ),
            ),
            P2AndroidFinalCommandPlan(
                identity =
                    P2AndroidFinalCommandPlan.Identity(
                        candidateId = "current-canonical-command-64-square",
                        runIndex = RUN_INDEX,
                    ),
                workload =
                    P2AndroidFinalCommandPlan.Workload(
                        canvasWidth = 64,
                        canvasHeight = 64,
                        warmupIterations = WARMUP_ITERATIONS,
                        samplesPerWorkload = SAMPLES_PER_WORKLOAD,
                        schema = SCHEMA,
                    ),
                output =
                    P2AndroidFinalCommandPlan.Output(
                        outputIdentity = "device-core-current-64-square",
                        relativePath = "p2-measurements/p2-android-final-command-64-square.csv",
                        publicationPolicy = P2AndroidFinalCommandPlan.PublicationPolicy.FailIfExists,
                    ),
            ),
            P2AndroidFinalCommandPlan(
                identity =
                    P2AndroidFinalCommandPlan.Identity(
                        candidateId = "current-canonical-command-128-square",
                        runIndex = RUN_INDEX,
                    ),
                workload =
                    P2AndroidFinalCommandPlan.Workload(
                        canvasWidth = 128,
                        canvasHeight = 128,
                        warmupIterations = WARMUP_ITERATIONS,
                        samplesPerWorkload = SAMPLES_PER_WORKLOAD,
                        schema = SCHEMA,
                    ),
                output =
                    P2AndroidFinalCommandPlan.Output(
                        outputIdentity = "device-core-current-128-square",
                        relativePath = "p2-measurements/p2-android-final-command-128-square.csv",
                        publicationPolicy = P2AndroidFinalCommandPlan.PublicationPolicy.FailIfExists,
                    ),
            ),
            P2AndroidFinalCommandPlan(
                identity =
                    P2AndroidFinalCommandPlan.Identity(
                        candidateId = "current-canonical-command-16x256-rectangle",
                        runIndex = RUN_INDEX,
                    ),
                workload =
                    P2AndroidFinalCommandPlan.Workload(
                        canvasWidth = 16,
                        canvasHeight = 256,
                        warmupIterations = WARMUP_ITERATIONS,
                        samplesPerWorkload = SAMPLES_PER_WORKLOAD,
                        schema = SCHEMA,
                    ),
                output =
                    P2AndroidFinalCommandPlan.Output(
                        outputIdentity = "device-core-current-16x256-rectangle",
                        relativePath = "p2-measurements/p2-android-final-command-16x256-rectangle.csv",
                        publicationPolicy = P2AndroidFinalCommandPlan.PublicationPolicy.FailIfExists,
                    ),
            ),
            P2AndroidFinalCommandPlan(
                identity =
                    P2AndroidFinalCommandPlan.Identity(
                        candidateId = "current-canonical-command-256x16-rectangle",
                        runIndex = RUN_INDEX,
                    ),
                workload =
                    P2AndroidFinalCommandPlan.Workload(
                        canvasWidth = 256,
                        canvasHeight = 16,
                        warmupIterations = WARMUP_ITERATIONS,
                        samplesPerWorkload = SAMPLES_PER_WORKLOAD,
                        schema = SCHEMA,
                    ),
                output =
                    P2AndroidFinalCommandPlan.Output(
                        outputIdentity = "device-core-current-256x16-rectangle",
                        relativePath = "p2-measurements/p2-android-final-command-256x16-rectangle.csv",
                        publicationPolicy = P2AndroidFinalCommandPlan.PublicationPolicy.FailIfExists,
                    ),
            ),
            P2AndroidFinalCommandPlan(
                identity =
                    P2AndroidFinalCommandPlan.Identity(
                        candidateId = "current-canonical-command-64x256-rectangle",
                        runIndex = RUN_INDEX,
                    ),
                workload =
                    P2AndroidFinalCommandPlan.Workload(
                        canvasWidth = 64,
                        canvasHeight = 256,
                        warmupIterations = WARMUP_ITERATIONS,
                        samplesPerWorkload = SAMPLES_PER_WORKLOAD,
                        schema = SCHEMA,
                    ),
                output =
                    P2AndroidFinalCommandPlan.Output(
                        outputIdentity = "device-core-current-64x256-rectangle",
                        relativePath = "p2-measurements/p2-android-final-command-64x256-rectangle.csv",
                        publicationPolicy = P2AndroidFinalCommandPlan.PublicationPolicy.FailIfExists,
                    ),
            ),
            P2AndroidFinalCommandPlan(
                identity =
                    P2AndroidFinalCommandPlan.Identity(
                        candidateId = "current-canonical-command-256x64-rectangle",
                        runIndex = RUN_INDEX,
                    ),
                workload =
                    P2AndroidFinalCommandPlan.Workload(
                        canvasWidth = 256,
                        canvasHeight = 64,
                        warmupIterations = WARMUP_ITERATIONS,
                        samplesPerWorkload = SAMPLES_PER_WORKLOAD,
                        schema = SCHEMA,
                    ),
                output =
                    P2AndroidFinalCommandPlan.Output(
                        outputIdentity = "device-core-current-256x64-rectangle",
                        relativePath = "p2-measurements/p2-android-final-command-256x64-rectangle.csv",
                        publicationPolicy = P2AndroidFinalCommandPlan.PublicationPolicy.FailIfExists,
                    ),
            ),
        ).associateBy(P2AndroidFinalCommandPlan::candidateId)
}
