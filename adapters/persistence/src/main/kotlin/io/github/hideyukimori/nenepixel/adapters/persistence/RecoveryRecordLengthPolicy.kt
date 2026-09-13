package io.github.hideyukimori.nenepixel.adapters.persistence

internal object RecoveryRecordLengthPolicy {
    fun validate(
        version: Int,
        state: Int,
        byteCount: Int,
    ): RecoveryRejection? =
        when (state) {
            RecoveryRecordLayout.CANDIDATE_STATE -> validateCandidate(version, byteCount)
            RecoveryRecordLayout.RETIRED_STATE -> validateRetired(byteCount)
            else -> RecoveryRejection.CORRUPT
        }

    private fun validateCandidate(
        version: Int,
        byteCount: Int,
    ): RecoveryRejection? {
        val range =
            if (version == RecoveryRecordLayout.V1_VERSION) {
                RecoveryRecordLayout.V1_MIN_CANDIDATE_BYTE_COUNT..RecoveryRecordLayout.MAX_RECORD_BYTE_COUNT
            } else {
                RecoveryRecordLayout.V2_MIN_CANDIDATE_BYTE_COUNT..RecoveryRecordLayout.V2_MAX_CANDIDATE_BYTE_COUNT
            }
        return if (byteCount in range) null else RecoveryRejection.CORRUPT
    }

    private fun validateRetired(byteCount: Int): RecoveryRejection? =
        if (byteCount == RecoveryRecordLayout.RETIRED_BYTE_COUNT) null else RecoveryRejection.CORRUPT
}
