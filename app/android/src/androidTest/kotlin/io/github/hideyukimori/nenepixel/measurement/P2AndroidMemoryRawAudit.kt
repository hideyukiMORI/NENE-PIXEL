package io.github.hideyukimori.nenepixel.measurement

import java.io.File

internal data class P2AndroidMemoryRawArtifact(
    val fileName: String,
    val byteLength: Long,
    val sha256: String,
)

internal data class P2AndroidMemoryRawProcessIdentity(
    val runIndex: Int,
    val processId: Int,
    val processStartElapsedRealtimeMillis: Long,
)

internal data class P2AndroidMemoryRawRuntime(
    val runtimeMaxMemoryBytes: Long,
    val memoryClassMib: Int,
    val baseline: PostGcMemorySnapshot,
    val retained: PostGcMemorySnapshot,
)

internal data class P2AndroidMemoryRawContext(
    val process: P2AndroidMemoryRawProcessIdentity,
    val physicalCheckpoints: List<P2AndroidPhysicalCheckpoint>,
)

internal data class P2AndroidMemoryRawDigests(
    val finalPixels: String,
    val entryDescriptors: String,
    val projection: String,
)

internal data class P2AndroidMemoryAuditedRun(
    val artifact: P2AndroidMemoryRawArtifact,
    val context: P2AndroidMemoryRawContext,
    val runtime: P2AndroidMemoryRawRuntime,
    val digests: P2AndroidMemoryRawDigests,
) {
    val process: P2AndroidMemoryRawProcessIdentity
        get() = context.process

    val pairedPssDeltaKilobytes: Long
        get() = retainedPss - baselinePss

    private val baselinePss: Long
        get() = runtime.baseline.totalPssKilobytes.toLong()
    private val retainedPss: Long
        get() = runtime.retained.totalPssKilobytes.toLong()
}

internal object P2AndroidMemoryRawAudit {
    fun readAll(
        environment: P2AndroidMeasurementEnvironment,
        aggregateIdentity: P2AndroidMemoryRunIdentity,
    ): List<P2AndroidMemoryAuditedRun> {
        val runs =
            P2AndroidMemoryProtocol.RUN_INDEX_RANGE.map { runIndex ->
                readOne(environment.memoryRunOutputFile(runIndex), runIndex, aggregateIdentity, environment)
            }
        check(runs.map { run -> run.process.runIndex }.toSet().size == P2AndroidMemoryProtocol.RUN_COUNT)
        val processIdentities =
            runs.map { run ->
                run.process.processId to run.process.processStartElapsedRealtimeMillis
            }
        check(processIdentities.toSet().size == runs.size) {
            "Retained-memory raw artifacts did not come from five distinct process identities."
        }
        check(runs.map { run -> run.runtime.runtimeMaxMemoryBytes }.toSet().size == 1)
        check(runs.map { run -> run.runtime.memoryClassMib }.toSet().size == 1)
        check(runs.map { run -> run.digests }.toSet().size == 1) {
            "Retained-memory correctness digests changed across invocations."
        }
        check(
            runs
                .flatMap { run -> run.context.physicalCheckpoints }
                .map { checkpoint -> checkpoint.aggregateSignature() }
                .toSet()
                .size == 1,
        ) {
            "Retained-memory physical checkpoint signature changed across invocations."
        }
        return runs
    }

    private fun readOne(
        file: File,
        expectedRunIndex: Int,
        aggregateIdentity: P2AndroidMemoryRunIdentity,
        environment: P2AndroidMeasurementEnvironment,
    ): P2AndroidMemoryAuditedRun {
        val beforeArtifact = P2AndroidMemoryRawArtifact(file.name, file.length(), P2AndroidMemoryDigest.file(file))
        val rows = P2AndroidMemoryCsv.read(file)
        val afterArtifact = P2AndroidMemoryRawArtifact(file.name, file.length(), P2AndroidMemoryDigest.file(file))
        check(beforeArtifact == afterArtifact) { "Retained-memory raw artifact changed during checksum audit." }
        validateRowSequence(rows)
        validateEmptyColumns(rows)
        val process = validateIdentity(rows, expectedRunIndex, aggregateIdentity, environment)
        val metadata = validateMetadata(rows)
        val physical = validatePhysical(rows)
        validateEntries(rows)
        val memoryRows = rows.filterType("memory_checkpoint")
        validateMemoryRowSequence(
            memoryRows.map { row ->
                P2AndroidMemoryCheckpointRowIdentity(
                    name = row.required("name"),
                    index = row.requiredInt("memory_checkpoint_index"),
                )
            },
        )
        val baseline = memoryRows.single { row -> row.required("name") == "baseline_post_gc" }
        val retained = memoryRows.single { row -> row.required("name") == "retained_post_gc" }
        val runtime = runtime(baseline, retained, metadata)
        val digests = validateSummary(rows.filterType("retained_summary").single())
        return P2AndroidMemoryAuditedRun(
            artifact = afterArtifact,
            context = P2AndroidMemoryRawContext(process, physical),
            runtime = runtime,
            digests = digests,
        )
    }

    private fun validateRowSequence(rows: List<P2AndroidMemoryCsvRow>) {
        check(rows.size == RAW_ROW_COUNT) { "Retained-memory raw row count changed." }
        val expectedTypes =
            List(METADATA_NAMES.size) { "metadata" } +
                listOf("physical_checkpoint", "memory_checkpoint") +
                List(P2AndroidMemoryProtocol.HISTORY_ENTRIES) { "retained_entry" } +
                listOf("memory_checkpoint", "physical_checkpoint", "retained_summary")
        check(rows.map { row -> row.required("record_type") } == expectedTypes) {
            "Retained-memory raw row ordering changed."
        }
        val baselineRow = rows[METADATA_NAMES.size + 1]
        check(baselineRow.required("name") == "baseline_post_gc")
        check(baselineRow.requiredInt("memory_checkpoint_index") == 0)
        val retainedRow = rows[METADATA_NAMES.size + P2AndroidMemoryProtocol.HISTORY_ENTRIES + 2]
        check(retainedRow.required("name") == "retained_post_gc")
        check(retainedRow.requiredInt("memory_checkpoint_index") == 1)
    }

    private fun validateEmptyColumns(rows: List<P2AndroidMemoryCsvRow>) {
        rows.forEach { row ->
            val type = row.required("record_type")
            val allowed =
                IDENTITY_COLUMNS +
                    when (type) {
                        "metadata" -> METADATA_COLUMNS
                        "physical_checkpoint" -> PHYSICAL_COLUMNS
                        "memory_checkpoint" -> MEMORY_COLUMNS
                        "retained_entry" -> ENTRY_COLUMNS
                        "retained_summary" -> SUMMARY_COLUMNS
                        else -> error("Unknown retained-memory raw record type '$type'.")
                    }
            val unexpected = row.values.filterValues(String::isNotEmpty).keys - allowed
            check(unexpected.isEmpty()) {
                "Retained-memory raw '$type' row populated forbidden columns: ${unexpected.sorted()}."
            }
        }
    }

    private fun validateIdentity(
        rows: List<P2AndroidMemoryCsvRow>,
        expectedRunIndex: Int,
        aggregateIdentity: P2AndroidMemoryRunIdentity,
        environment: P2AndroidMeasurementEnvironment,
    ): P2AndroidMemoryRawProcessIdentity {
        val first = rows.first()
        val expected =
            mapOf(
                "schema" to P2AndroidMemoryProtocol.INVOCATION_SCHEMA,
                "evidence_class" to "physical_device",
                "physical_profile_id" to environment.profileId,
                "candidate_id" to aggregateIdentity.run.candidateId,
                "workload_id" to P2AndroidMemoryProtocol.WORKLOAD_ID,
                "batch_id" to aggregateIdentity.batchId,
                "run_index" to expectedRunIndex.toString(),
                "source_commit" to aggregateIdentity.run.sourceCommit,
            )
        expected.forEach { (name, value) ->
            check(rows.all { row -> row.required(name) == value }) {
                "Retained-memory raw identity '$name' changed within run $expectedRunIndex."
            }
        }
        val processId = first.requiredInt("process_id")
        val processStart = first.requiredLong("process_start_elapsed_realtime_ms")
        check(processId > 0 && processStart > 0L)
        check(rows.all { row -> row.requiredInt("process_id") == processId })
        check(rows.all { row -> row.requiredLong("process_start_elapsed_realtime_ms") == processStart })
        return P2AndroidMemoryRawProcessIdentity(expectedRunIndex, processId, processStart)
    }

    private fun validateMetadata(rows: List<P2AndroidMemoryCsvRow>): Map<String, String> {
        val metadataRows = rows.filterType("metadata")
        check(metadataRows.map { row -> row.required("name") } == METADATA_NAMES)
        val metadata = metadataRows.associate { row -> row.required("name") to row.required("value") }
        check(metadata.getValue("schema") == P2AndroidMemoryProtocol.INVOCATION_SCHEMA)
        check(metadata.getValue("output_identity") == "device-memory-run")
        check(metadata.getValue("run_status") == "valid")
        check(metadata.getValue("app_variant") == "debug")
        check(metadata.getValue("test_variant") == "debugAndroidTest")
        check(metadata.getValue("manufacturer") == P2AndroidMemoryProtocol.MANUFACTURER)
        check(metadata.getValue("model") == P2AndroidMemoryProtocol.MODEL)
        check(metadata.getValue("product") == P2AndroidMemoryProtocol.PRODUCT)
        check(metadata.getValue("hardware") == P2AndroidMemoryProtocol.HARDWARE)
        check(metadata.getValue("api_level") == P2AndroidMemoryProtocol.API_LEVEL.toString())
        check(metadata.getValue("build_fingerprint") == P2AndroidMemoryProtocol.BUILD_FINGERPRINT)
        check(metadata.getValue("security_patch") == P2AndroidMemoryProtocol.SECURITY_PATCH)
        check(metadata.getValue("supported_abis") == P2AndroidMemoryProtocol.SUPPORTED_ABIS)
        check(
            metadata.getValue("runtime_max_memory_bytes") ==
                P2AndroidMemoryProtocol.RUNTIME_MAX_MEMORY_BYTES.toString(),
        )
        check(metadata.getValue("memory_class_mib") == P2AndroidMemoryProtocol.MEMORY_CLASS_MIB.toString())
        check(metadata.getValue("canvas") == P2AndroidMemoryProtocol.CANVAS_DESCRIPTOR)
        check(
            metadata.getValue("retained_workload") ==
                P2AndroidMemoryProtocol.RETAINED_WORKLOAD_DESCRIPTOR,
        )
        validateFixedMetadata(metadata)
        check(metadata.getValue("post_gc_churn_status") == P2AndroidMemoryProtocol.CHURN_STATUS)
        UNEVALUATED_METADATA_NAMES.forEach { name -> check(metadata.getValue(name) == UNEVALUATED) }
        return metadata
    }

    private fun validatePhysical(rows: List<P2AndroidMemoryCsvRow>): List<P2AndroidPhysicalCheckpoint> {
        val physicalRows = rows.filterType("physical_checkpoint")
        check(physicalRows.map { row -> row.required("name") } == listOf("before_baseline", "after_retained"))
        val before = physicalRows[0].physicalCheckpoint(sampleIndex = 0)
        val after = physicalRows[1].physicalCheckpoint(sampleIndex = 1)
        before.assertInitialValidity()
        after.assertCompatibleWith(before)
        physicalRows.forEach { row -> check(row.required("correctness_status") == PASS) }
        check(before.aggregateSignature() == after.aggregateSignature()) {
            "Retained-memory physical checkpoint signature changed within an invocation."
        }
        return listOf(before, after)
    }

    private fun validateEntries(rows: List<P2AndroidMemoryCsvRow>) {
        val entries = rows.filterType("retained_entry")
        check(entries.size == P2AndroidMemoryProtocol.HISTORY_ENTRIES)
        entries.forEachIndexed { index, row ->
            val expectation = P2AndroidMemoryValues.entryExpectation(index)
            check(row.required("name") == "entry_${index.toString().padStart(2, '0')}")
            check(row.requiredInt("entry_index") == index)
            check(row.requiredInt("block_index") == expectation.blockIndex)
            check(row.required("target_argb_hex") == argbHex(expectation.targetArgb))
            check(row.requiredLong("before_revision") == index.toLong())
            check(row.requiredLong("after_revision") == index + 1L)
            check(row.requiredInt("invalidation_origin_x") == 0)
            check(
                row.requiredInt("invalidation_origin_y") ==
                    expectation.blockIndex * P2AndroidMemoryProtocol.BLOCK_HEIGHT,
            )
            check(row.requiredInt("invalidation_width") == P2AndroidMemoryProtocol.CANVAS_EDGE)
            check(row.requiredInt("invalidation_height") == P2AndroidMemoryProtocol.BLOCK_HEIGHT)
            validateWorkload(row)
            check(row.required("correctness_status") == PASS)
        }
    }

    private fun runtime(
        baselineRow: P2AndroidMemoryCsvRow,
        retainedRow: P2AndroidMemoryCsvRow,
        metadata: Map<String, String>,
    ): P2AndroidMemoryRawRuntime {
        check(baselineRow.requiredInt("memory_checkpoint_index") == 0)
        check(retainedRow.requiredInt("memory_checkpoint_index") == 1)
        validateWorkload(baselineRow)
        validateWorkload(retainedRow)
        val baseline = baselineRow.memorySnapshot()
        val retained = retainedRow.memorySnapshot()
        val maxMemory = baselineRow.requiredLong("runtime_max_memory_bytes")
        val memoryClass = baselineRow.requiredInt("memory_class_mib")
        check(memoryClass > 0)
        check(retainedRow.requiredLong("runtime_max_memory_bytes") == maxMemory)
        check(retainedRow.requiredInt("memory_class_mib") == memoryClass)
        check(metadata.getValue("runtime_max_memory_bytes") == maxMemory.toString())
        check(metadata.getValue("memory_class_mib") == memoryClass.toString())
        validateCheckpointMemory(baseline, maxMemory)
        validateCheckpointMemory(retained, maxMemory)
        check(baselineRow.required("correctness_status") == PASS)
        check(retainedRow.required("correctness_status") == PASS)
        check(baselineRow.required("boundary") == P2AndroidMemoryProtocol.BASELINE_BOUNDARY)
        check(retainedRow.required("boundary") == P2AndroidMemoryProtocol.RETAINED_BOUNDARY)
        return P2AndroidMemoryRawRuntime(maxMemory, memoryClass, baseline, retained)
    }

    private fun validateSummary(row: P2AndroidMemoryCsvRow): P2AndroidMemoryRawDigests {
        validateWorkload(row)
        check(row.required("name") == P2AndroidMemoryProtocol.WORKLOAD_ID)
        check(row.requiredLong("final_revision") == P2AndroidMemoryProtocol.FINAL_REVISION)
        check(row.required("history_after") == "undo_available")
        row.requiredInt("document_hash")
        row.requiredInt("snapshot_hash")
        val finalPixels = row.requiredDigest("final_pixel_digest_sha256")
        val entries = row.requiredDigest("entry_descriptor_digest_sha256")
        val projection = row.requiredDigest("projection_digest_sha256")
        check(finalPixels == P2AndroidMemoryProtocol.EXPECTED_PROJECTION_DIGEST)
        check(entries == P2AndroidMemoryProtocol.EXPECTED_ENTRY_DESCRIPTOR_DIGEST)
        check(projection == P2AndroidMemoryProtocol.EXPECTED_PROJECTION_DIGEST)
        check(row.requiredInt("projection_pixel_count") == P2AndroidMemoryProtocol.PIXEL_COUNT)
        check(row.requiredInt("projection_first_x") == 0 && row.requiredInt("projection_first_y") == 0)
        check(row.required("projection_first_argb_hex") == WHITE_ARGB_HEX)
        check(row.requiredInt("projection_last_x") == P2AndroidMemoryProtocol.CANVAS_EDGE - 1)
        check(row.requiredInt("projection_last_y") == P2AndroidMemoryProtocol.CANVAS_EDGE - 1)
        check(row.required("projection_last_argb_hex") == WHITE_ARGB_HEX)
        check(row.requiredInt("projection_mismatch_count") == 0)
        check(row.required("post_gc_churn_status") == P2AndroidMemoryProtocol.CHURN_STATUS)
        UNEVALUATED_SUMMARY_COLUMNS.forEach { column -> check(row.required(column) == UNEVALUATED) }
        check(row.required("correctness_status") == PASS)
        check(row.required("boundary") == P2AndroidMemoryProtocol.SUMMARY_BOUNDARY)
        return P2AndroidMemoryRawDigests(finalPixels, entries, projection)
    }

    private fun validateWorkload(row: P2AndroidMemoryCsvRow) {
        check(row.requiredInt("canvas_width") == P2AndroidMemoryProtocol.CANVAS_EDGE)
        check(row.requiredInt("canvas_height") == P2AndroidMemoryProtocol.CANVAS_EDGE)
        check(row.requiredInt("pixel_count") == P2AndroidMemoryProtocol.PIXEL_COUNT)
        check(row.requiredInt("history_entries") == P2AndroidMemoryProtocol.HISTORY_ENTRIES)
        check(row.requiredInt("change_count_per_entry") == P2AndroidMemoryProtocol.CHANGE_COUNT_PER_ENTRY)
        check(row.requiredLong("total_retained_changes") == P2AndroidMemoryProtocol.TOTAL_RETAINED_CHANGES)
    }

    private fun P2AndroidMemoryCsvRow.physicalCheckpoint(sampleIndex: Int): P2AndroidPhysicalCheckpoint =
        P2AndroidPhysicalCheckpoint(
            name = required("name"),
            sampleIndex = sampleIndex,
            displayModeId = requiredInt("display_mode_id"),
            physicalWidthPixels = requiredInt("display_width_pixels"),
            physicalHeightPixels = requiredInt("display_height_pixels"),
            refreshRateHertz = required("refresh_rate_hertz").toFloat(),
            thermalStatus = requiredInt("thermal_status"),
            powerSaveMode = requiredBoolean("power_save_mode"),
            interactive = requiredBoolean("interactive"),
            usbPowered = requiredBoolean("usb_powered"),
            batteryLevelPercent = requiredInt("battery_level_percent"),
        )

    private fun P2AndroidMemoryCsvRow.memorySnapshot(): PostGcMemorySnapshot {
        val memory =
            PostGcMemorySnapshot(
                javaHeapUsedBytes = requiredLong("post_gc_java_heap_used_bytes"),
                javaHeapCommittedBytes = requiredLong("post_gc_java_heap_committed_bytes"),
                totalPssKilobytes = requiredInt("total_pss_kb"),
                dalvikPssKilobytes = requiredInt("dalvik_pss_kb"),
                nativePssKilobytes = requiredInt("native_pss_kb"),
                otherPssKilobytes = requiredInt("other_pss_kb"),
                totalPrivateDirtyKilobytes = requiredInt("total_private_dirty_kb"),
                totalSharedDirtyKilobytes = requiredInt("total_shared_dirty_kb"),
            )
        return memory
    }

    internal fun validateMemoryRowSequence(rows: List<P2AndroidMemoryCheckpointRowIdentity>) {
        P2AndroidMemoryContractValidation.validateMemoryRowSequence(rows)
    }

    internal fun validateFixedMetadata(metadata: Map<String, String>) {
        P2AndroidMemoryContractValidation.validateFixedMetadata(metadata)
    }

    internal fun validateCheckpointMemory(
        memory: PostGcMemorySnapshot,
        runtimeMaxMemoryBytes: Long,
    ) {
        P2AndroidMemoryContractValidation.validateMemory(memory, runtimeMaxMemoryBytes)
    }

    private fun P2AndroidPhysicalCheckpoint.aggregateSignature(): String =
        listOf(
            displayModeId,
            physicalWidthPixels,
            physicalHeightPixels,
            refreshRateHertz,
            thermalStatus,
            powerSaveMode,
            interactive,
            usbPowered,
            batteryLevelPercent,
        ).joinToString("|")

    private fun List<P2AndroidMemoryCsvRow>.filterType(type: String): List<P2AndroidMemoryCsvRow> =
        filter { row -> row.values["record_type"] == type }

    private fun P2AndroidMemoryCsvRow.requiredInt(column: String): Int =
        requireNotNull(required(column).toIntOrNull()) { "Retained-memory '$column' must be an Int." }

    private fun P2AndroidMemoryCsvRow.requiredLong(column: String): Long =
        requireNotNull(required(column).toLongOrNull()) { "Retained-memory '$column' must be a Long." }

    private fun P2AndroidMemoryCsvRow.requiredBoolean(column: String): Boolean =
        requireNotNull(required(column).toBooleanStrictOrNull()) { "Retained-memory '$column' must be Boolean." }

    private fun P2AndroidMemoryCsvRow.requiredDigest(column: String): String {
        val value = required(column)
        check(DIGEST_PATTERN.matches(value)) { "Retained-memory '$column' must be uppercase SHA-256." }
        return value
    }

    private fun argbHex(argb: Int): String =
        argb
            .toUInt()
            .toString(HEX_RADIX)
            .uppercase()
            .padStart(ARGB_HEX_LENGTH, '0')

    private const val PASS: String = "pass"
    private const val UNEVALUATED: String = "not_evaluated"
    private const val HEX_RADIX: Int = 16
    private const val ARGB_HEX_LENGTH: Int = 8
    private const val WHITE_ARGB_HEX: String = "FFFFFFFF"
    private val DIGEST_PATTERN: Regex = Regex("[0-9A-F]{64}")
    private val UNEVALUATED_METADATA_NAMES: List<String> =
        listOf("peak_headroom_status", "candidate_retained_memory_status", "candidate_projection_status")
    private val UNEVALUATED_SUMMARY_COLUMNS: List<String> =
        listOf("peak_headroom_status", "candidate_retained_memory_status", "candidate_projection_status")
    private val METADATA_NAMES: List<String> =
        listOf(
            "schema",
            "output_identity",
            "run_status",
            "app_variant",
            "test_variant",
            "manufacturer",
            "model",
            "product",
            "hardware",
            "api_level",
            "build_fingerprint",
            "security_patch",
            "supported_abis",
            "runtime_max_memory_bytes",
            "memory_class_mib",
            "canvas",
            "retained_workload",
            "entry_sequence",
            "gc_protocol",
            "capture_sequence",
            "retained_boundary",
            "projection_boundary",
            "private_patch_boundary",
            "post_gc_churn_status",
            "peak_headroom_status",
            "candidate_retained_memory_status",
            "candidate_projection_status",
        )
    private val IDENTITY_COLUMNS: Set<String> =
        setOf(
            "record_type",
            "schema",
            "evidence_class",
            "physical_profile_id",
            "candidate_id",
            "workload_id",
            "batch_id",
            "run_index",
            "source_commit",
            "process_id",
            "process_start_elapsed_realtime_ms",
        )
    private val METADATA_COLUMNS: Set<String> = setOf("name", "value")
    private val WORKLOAD_COLUMNS: Set<String> =
        setOf(
            "canvas_width",
            "canvas_height",
            "pixel_count",
            "history_entries",
            "change_count_per_entry",
            "total_retained_changes",
        )
    private val PHYSICAL_COLUMNS: Set<String> =
        setOf(
            "name",
            "display_mode_id",
            "display_width_pixels",
            "display_height_pixels",
            "refresh_rate_hertz",
            "thermal_status",
            "power_save_mode",
            "interactive",
            "usb_powered",
            "battery_level_percent",
            "correctness_status",
        )
    private val MEMORY_COLUMNS: Set<String> =
        WORKLOAD_COLUMNS +
            setOf(
                "name",
                "memory_checkpoint_index",
                "post_gc_java_heap_used_bytes",
                "post_gc_java_heap_committed_bytes",
                "runtime_max_memory_bytes",
                "memory_class_mib",
                "total_pss_kb",
                "dalvik_pss_kb",
                "native_pss_kb",
                "other_pss_kb",
                "total_private_dirty_kb",
                "total_shared_dirty_kb",
                "correctness_status",
                "boundary",
            )
    private val ENTRY_COLUMNS: Set<String> =
        WORKLOAD_COLUMNS +
            setOf(
                "name",
                "entry_index",
                "block_index",
                "target_argb_hex",
                "before_revision",
                "after_revision",
                "invalidation_origin_x",
                "invalidation_origin_y",
                "invalidation_width",
                "invalidation_height",
                "correctness_status",
            )
    private val SUMMARY_COLUMNS: Set<String> =
        WORKLOAD_COLUMNS +
            setOf(
                "name",
                "final_revision",
                "history_after",
                "document_hash",
                "snapshot_hash",
                "final_pixel_digest_sha256",
                "entry_descriptor_digest_sha256",
                "projection_pixel_count",
                "projection_first_x",
                "projection_first_y",
                "projection_first_argb_hex",
                "projection_last_x",
                "projection_last_y",
                "projection_last_argb_hex",
                "projection_digest_sha256",
                "projection_mismatch_count",
                "post_gc_churn_status",
                "peak_headroom_status",
                "candidate_retained_memory_status",
                "candidate_projection_status",
                "correctness_status",
                "boundary",
            )
    private val RAW_ROW_COUNT: Int = METADATA_NAMES.size + P2AndroidMemoryProtocol.HISTORY_ENTRIES + 5
}
