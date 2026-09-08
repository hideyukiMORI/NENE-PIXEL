package io.github.hideyukimori.nenepixel.core.projectformat

public class ProjectFormatBytes private constructor(
    private val bytes: ByteArray,
) {
    public val byteCount: Int
        get() = bytes.size

    public fun copyBytes(): ByteArray = bytes.copyOf()

    internal fun byteAt(index: Int): Byte = bytes[index]

    public override fun equals(other: Any?): Boolean =
        this === other || (other is ProjectFormatBytes && bytes.contentEquals(other.bytes))

    public override fun hashCode(): Int = bytes.contentHashCode()

    public override fun toString(): String = "ProjectFormatBytes(byteCount=$byteCount)"

    public companion object {
        public const val MAX_FILE_BYTE_COUNT: Int = 262_186
        public const val MAX_PROBE_BYTE_COUNT: Int = MAX_FILE_BYTE_COUNT + 1

        public fun create(bytes: ByteArray): ProjectFormatResult<ProjectFormatBytes> =
            if (bytes.size > MAX_PROBE_BYTE_COUNT) {
                rejected(
                    ProjectFormatRejection.ResourceLimitExceeded(
                        actualByteCount = bytes.size,
                        maximumByteCount = MAX_PROBE_BYTE_COUNT,
                    ),
                )
            } else {
                accepted(ProjectFormatBytes(bytes.copyOf()))
            }
    }
}
