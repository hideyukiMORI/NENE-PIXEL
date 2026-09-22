package io.github.hideyukimori.nenepixel.measurement

import java.io.File

internal object P2AndroidFinalCommandOutputPublication {
    fun reserve(
        output: File,
        policy: P2AndroidFinalCommandPlan.PublicationPolicy,
    ): Reservation {
        val immutable = policy != P2AndroidFinalCommandPlan.PublicationPolicy.OverwriteExisting
        if (immutable) {
            check(output.createNewFile()) {
                "Final command measurement output already exists: ${output.absolutePath}"
            }
        }
        if (policy == P2AndroidFinalCommandPlan.PublicationPolicy.KeepPartial) {
            output.writeText("record_type,name,value\nmetadata,run_status,reserved\n")
        }
        return Reservation(output, policy)
    }

    fun publish(
        output: File,
        policy: P2AndroidFinalCommandPlan.PublicationPolicy,
        writeRows: (File) -> Unit,
        cleanup: (File) -> Unit = ::deleteIncompleteOutput,
    ): File = reserve(output, policy).complete(writeRows, cleanup)

    internal class Reservation(
        val output: File,
        private val policy: P2AndroidFinalCommandPlan.PublicationPolicy,
    ) {
        private var closed: Boolean = false

        fun bindIdentity(
            plan: P2AndroidFinalCommandPlan,
            identity: P2AndroidRunIdentity,
        ) {
            check(policy == P2AndroidFinalCommandPlan.PublicationPolicy.KeepPartial)
            check(!closed) { "Final command measurement output reservation is already closed." }
            output.appendText(
                buildString {
                    appendLine("metadata,schema,${plan.schema.csvCell()}")
                    appendLine("metadata,output_identity,${plan.outputIdentity.csvCell()}")
                    appendLine("metadata,candidate_id,${identity.candidateId.csvCell()}")
                    appendLine("metadata,run_index,${identity.runIndex}")
                    appendLine("metadata,measurement_build_commit,${identity.sourceCommit.csvCell()}")
                    appendLine("metadata,workload_order,${plan.workloadNames.joinToString("|").csvCell()}")
                    appendLine("metadata,warmups_per_workload,${plan.warmupIterations}")
                    appendLine("metadata,samples_per_workload,${plan.samplesPerWorkload}")
                    appendLine("metadata,sample_total,${plan.totalSampleCount}")
                },
            )
        }

        fun complete(
            writeRows: (File) -> Unit,
            cleanup: (File) -> Unit = ::deleteIncompleteOutput,
        ): File {
            check(!closed) { "Final command measurement output reservation is already closed." }
            try {
                writeRows(output)
                closed = true
            } catch (originalFailure: Throwable) {
                if (policy == P2AndroidFinalCommandPlan.PublicationPolicy.FailIfExists) {
                    cleanUp(output, originalFailure, cleanup)
                }
                throw originalFailure
            }
            return output
        }

        fun recordFailure(
            phase: String,
            completedCorrectness: Int,
            completedWarmups: Int,
            completedSamples: Int,
            samples: List<P2AndroidFinalCommandSample>,
            failure: Throwable,
        ) {
            check(policy == P2AndroidFinalCommandPlan.PublicationPolicy.KeepPartial)
            check(!closed) { "Final command measurement output reservation is already closed." }
            output.appendText(
                buildString {
                    appendLine("metadata,run_status,invalid")
                    appendLine("metadata,failure_phase,${phase.csvCell()}")
                    appendLine("metadata,completed_correctness,$completedCorrectness")
                    appendLine("metadata,completed_warmups,$completedWarmups")
                    appendLine("metadata,completed_samples,$completedSamples")
                    appendLine("metadata,failure_type,${failure.javaClass.name.csvCell()}")
                    appendLine(
                        "metadata,failure_message,${failure.message.orEmpty().take(MAX_FAILURE_MESSAGE).csvCell()}",
                    )
                    samples.forEach { sample ->
                        appendLine(
                            listOf(
                                "partial_sample",
                                sample.globalSampleIndex,
                                sample.spec.kind.metricName,
                                sample.localSampleIndex,
                                sample.latencyNanos,
                                sample.outcome.resultKind,
                                sample.runtimeDelta.allocatedBytesDelta,
                                sample.runtimeDelta.blockingGcCountDelta,
                                sample.runtimeDelta.blockingGcTimeMillisDelta,
                            ).joinToString(","),
                        )
                    }
                },
            )
            closed = true
        }
    }

    private fun cleanUp(
        output: File,
        originalFailure: Throwable,
        cleanup: (File) -> Unit,
    ) {
        val message = "Failed to clean up incomplete immutable final command output: ${output.absolutePath}"
        val cleanupFailure =
            try {
                cleanup(output)
                check(!output.exists()) { "Incomplete final command output still exists." }
                return
            } catch (failure: Throwable) {
                failure
            }
        throw IllegalStateException(message, originalFailure).also { failure ->
            failure.addSuppressed(cleanupFailure)
        }
    }

    private fun deleteIncompleteOutput(output: File) {
        check(output.delete()) { "Incomplete final command output could not be deleted." }
    }

    private fun String.csvCell(): String = "\"${replace("\"", "\"\"")}\""

    private const val MAX_FAILURE_MESSAGE: Int = 1_024
}
