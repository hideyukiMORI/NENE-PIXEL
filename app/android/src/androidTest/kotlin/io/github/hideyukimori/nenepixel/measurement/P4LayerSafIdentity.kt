package io.github.hideyukimori.nenepixel.measurement

import android.os.Process

internal object P4LayerSafIdentity {
    fun line(
        admission: P4LayerRunAdmission,
        fixture: P4GrantedDocument,
        profile: String,
    ): String {
        val prefix = "p4-layer-saf-${admission.preflightSha256.take(12)}"
        check(Process.myPid() > 0 && Process.getStartElapsedRealtime() > 0)
        return "P4_LAYER_SAF_IDENTITY " +
            (
                admission.reportFields() +
                    facts(
                        fixture,
                        profile,
                        prefix,
                    )
            ).joinToString(" ") { (key, value) -> "$key=$value" }
    }

    private fun facts(
        fixture: P4GrantedDocument,
        profile: String,
        prefix: String,
    ): List<Pair<String, String>> =
        listOf(
            "process_id" to Process.myPid().toString(),
            "process_start_elapsed_realtime_ms" to Process.getStartElapsedRealtime().toString(),
            "profile" to profile,
            "fixture_uri" to fixture.uri.toString(),
            "fixture_sha256" to P4LayerFixture.MAXIMUM.sha256,
            "fixture_bytes" to P4LayerFixture.MAXIMUM.byteCount.toString(),
            "grantee_uid" to fixture.granteeUid.toString(),
            "provider_uid" to fixture.providerUid.toString(),
            "destination_count" to "25",
            "warmup_count" to "5",
            "sample_count" to "20",
            "journal_rows" to "27",
            "setup_timeout_seconds" to "300",
            "worker_timeout_seconds" to "60",
            "native_timeout_seconds" to "420",
            "sample_anomaly_nanos" to "5000000000",
            "report_relative_path" to "files/$prefix",
        )
}
