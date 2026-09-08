package io.github.hideyukimori.nenepixel.core.projectformat

@JvmInline
public value class ProjectFormatVersion private constructor(
    public val value: UShort,
) {
    public companion object {
        internal fun fromWireValue(value: Int): ProjectFormatVersion {
            check(value in UShort.MIN_VALUE.toInt()..UShort.MAX_VALUE.toInt()) {
                "Project format wire version is outside U16: $value"
            }
            return ProjectFormatVersion(value.toUShort())
        }
    }
}
