package io.github.hideyukimori.nenepixel.measurement

import androidx.test.platform.app.InstrumentationRegistry

internal data class P2AndroidRunIdentity(
    val candidateId: String,
    val runIndex: Int,
    val sourceCommit: String,
) {
    companion object {
        fun fromRunnerArguments(): P2AndroidRunIdentity {
            val arguments = InstrumentationRegistry.getArguments()
            val candidateId = arguments.requiredIdentityString(CANDIDATE_ID_ARGUMENT)
            val runIndex =
                requireNotNull(arguments.getString(RUN_INDEX_ARGUMENT)?.toIntOrNull()?.takeIf { it > 0 }) {
                    "Runner argument '$RUN_INDEX_ARGUMENT' must be a positive integer."
                }
            val sourceCommit =
                arguments
                    .requiredIdentityString(SOURCE_COMMIT_ARGUMENT)
                    .lowercase()
                    .takeIf(SOURCE_COMMIT_PATTERN::matches)
            return P2AndroidRunIdentity(
                candidateId = candidateId,
                runIndex = runIndex,
                sourceCommit =
                    requireNotNull(sourceCommit) {
                        "Runner argument '$SOURCE_COMMIT_ARGUMENT' must be a full 40-character Git commit."
                    },
            )
        }

        const val CANDIDATE_ID_ARGUMENT: String = "nene.p2.candidateId"
        const val RUN_INDEX_ARGUMENT: String = "nene.p2.runIndex"
        const val SOURCE_COMMIT_ARGUMENT: String = "nene.p2.sourceCommit"
        private val SOURCE_COMMIT_PATTERN: Regex = Regex("[0-9a-f]{40}")
    }
}

private fun android.os.Bundle.requiredIdentityString(name: String): String =
    requireNotNull(getString(name)?.trim()?.takeIf(String::isNotEmpty)) {
        "Runner argument '$name' is required for physical measurements."
    }
