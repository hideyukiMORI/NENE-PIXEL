package io.github.hideyukimori.nenepixel.presentation.compose.editor

internal object P2HostMeasurementReport {
    fun csvRow(vararg values: Any): String =
        values.joinToString(",") { value -> "\"${value.toString().replace("\"", "\"\"")}\"" }

    fun metadataRow(
        columnCount: Int,
        name: String,
        value: String,
    ): String = paddedRow(columnCount, "metadata", name, value)

    fun paddedRow(
        columnCount: Int,
        vararg values: Any,
    ): String {
        require(values.size <= columnCount) { "Too many P2 host report values." }
        return csvRow(*values, *Array(columnCount - values.size) { "" })
    }

    fun systemDescription(): String =
        listOf("os.name", "os.version", "os.arch").joinToString(" ", transform = ::requiredSystemProperty)

    fun jvmDescription(): String =
        listOf("java.vm.name", "java.runtime.version").joinToString(" ", transform = ::requiredSystemProperty)

    private fun requiredSystemProperty(name: String): String =
        requireNotNull(System.getProperty(name)) { "Required JVM system property '$name' is unavailable." }
}
