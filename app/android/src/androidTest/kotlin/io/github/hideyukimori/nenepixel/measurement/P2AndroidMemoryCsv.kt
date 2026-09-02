package io.github.hideyukimori.nenepixel.measurement

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

internal data class P2AndroidMemoryCsvRow(
    val values: Map<String, String>,
) {
    fun required(column: String): String =
        requireNotNull(values[column]?.takeIf(String::isNotEmpty)) {
            "Retained-memory CSV row '${values["record_type"]}' is missing '$column'."
        }
}

internal object P2AndroidMemoryCsv {
    fun row(vararg values: Pair<String, Any?>): P2AndroidMemoryCsvRow {
        val names = values.map(Pair<String, Any?>::first)
        check(names.size == names.toSet().size) { "Duplicate retained-memory CSV column value." }
        check(names.all(COLUMNS::contains)) { "Unknown retained-memory CSV column value." }
        return P2AndroidMemoryCsvRow(
            values.associate { (name, value) -> name to (value?.toString() ?: "") },
        )
    }

    fun writeImmutable(
        output: File,
        rows: List<P2AndroidMemoryCsvRow>,
    ): File {
        validateRows(rows)
        val directory = requireNotNull(output.parentFile)
        check(directory.isDirectory || directory.mkdirs()) {
            "Failed to create retained-memory output directory."
        }
        check(!output.exists()) { "Immutable retained-memory output already exists: ${output.name}" }
        val temporary = File(directory, "${output.name}.tmp")
        val publicationLock = File(directory, "${output.name}.publish-lock")
        check(publicationLock.mkdir()) {
            "Retained-memory immutable publication is already locked: ${output.name}"
        }
        try {
            check(!output.exists()) { "Immutable retained-memory output already exists: ${output.name}" }
            check(!temporary.exists()) { "Retained-memory temporary output already exists: ${temporary.name}" }
            writeAndSync(temporary, rows)
            check(!output.exists()) { "Immutable retained-memory output appeared during publication: ${output.name}" }
            check(temporary.renameTo(output)) { "Failed to atomically publish retained-memory output." }
        } finally {
            if (temporary.exists()) temporary.delete()
            if (publicationLock.exists()) publicationLock.delete()
        }
        return output
    }

    fun read(file: File): List<P2AndroidMemoryCsvRow> {
        check(file.isFile) { "Retained-memory raw artifact is missing: ${file.name}" }
        val lines = file.readLines(StandardCharsets.UTF_8)
        check(lines.isNotEmpty()) { "Retained-memory raw artifact is empty: ${file.name}" }
        check(parseLine(lines.first()) == COLUMNS) { "Retained-memory CSV header changed: ${file.name}" }
        val rows =
            lines.drop(1).map { line ->
                val values = parseLine(line)
                check(values.size == COLUMNS.size) { "Retained-memory CSV row width changed: ${file.name}" }
                P2AndroidMemoryCsvRow(COLUMNS.zip(values).toMap())
            }
        validateRows(rows)
        return rows
    }

    private fun validateRows(rows: List<P2AndroidMemoryCsvRow>) {
        check(rows.isNotEmpty()) { "Retained-memory CSV requires data rows." }
        rows.forEach { row -> check(row.values.keys.all(COLUMNS::contains)) }
    }

    private fun writeAndSync(
        file: File,
        rows: List<P2AndroidMemoryCsvRow>,
    ) {
        FileOutputStream(file).use { stream ->
            val writer = OutputStreamWriter(stream, StandardCharsets.UTF_8).buffered()
            writer.appendLine(encodeLine(COLUMNS))
            rows.forEach { row ->
                writer.appendLine(encodeLine(COLUMNS.map { column -> row.values[column].orEmpty() }))
            }
            writer.flush()
            stream.fd.sync()
        }
    }

    private fun encodeLine(values: List<String>): String =
        values.joinToString(",") { value -> "\"${value.replace("\"", "\"\"")}\"" }

    private fun parseLine(line: String): List<String> {
        check(line.isNotEmpty() && line.last() != ',') { "Retained-memory CSV must not end with a trailing comma." }
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0
        while (index < line.length) {
            check(line[index] == '"') { "Retained-memory CSV field must be quoted." }
            index += 1
            index = parseQuotedValue(line, index, current)
            values += current.toString()
            current.setLength(0)
            if (index < line.length) {
                check(line[index] == ',') { "Retained-memory CSV separator changed." }
                index += 1
            }
        }
        return values
    }

    private fun parseQuotedValue(
        line: String,
        startIndex: Int,
        target: StringBuilder,
    ): Int {
        var index = startIndex
        while (index < line.length) {
            val character = line[index]
            if (character != '"') {
                target.append(character)
                index += 1
            } else if (index + 1 < line.length && line[index + 1] == '"') {
                target.append('"')
                index += 2
            } else {
                return index + 1
            }
        }
        error("Retained-memory CSV quoted field is unterminated.")
    }

    val COLUMNS: List<String> =
        listOf(
            "record_type",
            "name",
            "value",
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
            "canvas_width",
            "canvas_height",
            "pixel_count",
            "history_entries",
            "change_count_per_entry",
            "total_retained_changes",
            "entry_index",
            "block_index",
            "target_argb_hex",
            "before_revision",
            "after_revision",
            "invalidation_origin_x",
            "invalidation_origin_y",
            "invalidation_width",
            "invalidation_height",
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
            "baseline_java_heap_used_bytes",
            "baseline_java_heap_committed_bytes",
            "baseline_total_pss_kb",
            "retained_java_heap_used_bytes",
            "retained_java_heap_committed_bytes",
            "retained_total_pss_kb",
            "paired_pss_delta_kb",
            "display_mode_id",
            "display_width_pixels",
            "display_height_pixels",
            "refresh_rate_hertz",
            "thermal_status",
            "power_save_mode",
            "interactive",
            "usb_powered",
            "battery_level_percent",
            "raw_file_name",
            "raw_byte_length",
            "raw_sha256",
            "median_paired_pss_delta_kb",
            "maximum_paired_pss_delta_kb",
            "median_retained_java_heap_used_bytes",
            "maximum_retained_java_heap_used_bytes",
            "pss_median_condition",
            "pss_individual_condition",
            "steady_art_live_heap_condition",
            "post_gc_churn_status",
            "peak_headroom_status",
            "candidate_retained_memory_status",
            "candidate_projection_status",
            "correctness_status",
            "boundary",
        )
}
