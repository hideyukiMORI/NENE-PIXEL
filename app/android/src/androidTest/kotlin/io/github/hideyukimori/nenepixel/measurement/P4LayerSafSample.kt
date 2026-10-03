package io.github.hideyukimori.nenepixel.measurement

import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectSaveOutcome

internal data class P4LayerSafSample(
    val ordinal: Int,
    val elapsedNanos: Long,
    val destination: P4LayerSafDestination,
    val outcome: P4LayerSafOutcome,
) {
    val kind: String get() = if (ordinal < 5) "warmup" else "sample"
    private val index: Int get() = if (ordinal < 5) ordinal else ordinal - 5

    fun accepted(): Boolean =
        outcome.result == ProjectSaveOutcome.Saved && outcome.pickerConsumed && elapsedNanos in 1L..5_000_000_000L

    fun row(kind: String = this.kind): String {
        val result = outcome.result
        val status =
            when (result) {
                ProjectSaveOutcome.Saved -> "saved"
                ProjectSaveOutcome.Cancelled -> "cancelled"
                is ProjectSaveOutcome.Failed -> "failed"
            }
        val bytes = if (result == ProjectSaveOutcome.Saved) "1182862" else "not_verified"
        val cleanup = if (result is ProjectSaveOutcome.Failed) result.cleanup.name.lowercase() else "not_needed"
        return "$SCHEMA,candidate,$index,$kind,$elapsedNanos,${destination.uri}," +
            "${destination.granteeUid},${destination.providerUid},0,$bytes,${outcome.pickerConsumed},$status,$cleanup"
    }

    companion object {
        const val SCHEMA: String = "nene-pixel-p4-layer-saf-save-device-v1"
        const val HEADER: String =
            "schema,role,index,kind,elapsed_ns,destination_uri,grantee_uid,provider_uid," +
                "initial_byte_count,accepted_byte_count,picker_consumed,outcome,cleanup"
    }
}

internal data class P4LayerSafDestination(
    val uri: String,
    val granteeUid: Int,
    val providerUid: Int,
)

internal data class P4LayerSafOutcome(
    val result: ProjectSaveOutcome,
    val pickerConsumed: Boolean,
)
