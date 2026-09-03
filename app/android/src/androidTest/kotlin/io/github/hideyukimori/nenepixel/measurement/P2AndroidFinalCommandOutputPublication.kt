package io.github.hideyukimori.nenepixel.measurement

import java.io.File

internal object P2AndroidFinalCommandOutputPublication {
    fun publish(
        output: File,
        policy: P2AndroidFinalCommandPlan.PublicationPolicy,
        writeRows: (File) -> Unit,
        cleanup: (File) -> Unit = ::deleteIncompleteOutput,
    ): File {
        val immutable = policy == P2AndroidFinalCommandPlan.PublicationPolicy.FailIfExists
        if (immutable) {
            check(output.createNewFile()) {
                "Final command measurement output already exists: ${output.absolutePath}"
            }
        }
        try {
            writeRows(output)
        } catch (originalFailure: Throwable) {
            if (immutable) cleanUp(output, originalFailure, cleanup)
            throw originalFailure
        }
        return output
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
}
