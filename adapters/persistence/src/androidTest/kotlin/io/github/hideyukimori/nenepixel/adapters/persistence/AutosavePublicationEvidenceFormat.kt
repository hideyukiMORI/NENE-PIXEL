package io.github.hideyukimori.nenepixel.adapters.persistence

internal enum class AutosavePublicationEvidenceFormat(
    private val schema: String,
) {
    INDEXED(AutosavePublicationEvidenceReport.SCHEMA),
    LAYER(AutosaveLayerPublicationAdmission.SCHEMA),
    ;

    fun sampleRow(
        group: String,
        index: Int,
        kind: String,
        sample: PublicationSample,
    ): String {
        val result = sample.result
        val accepted = result is RecordWriteResult.Written && result.generation.value == sample.generation
        val outcome = if (this == LAYER && !accepted) "failed" else "written"
        return "$schema,candidate,$group,$index,$kind,${sample.elapsedNanos},${sample.generation},$outcome"
    }
}
