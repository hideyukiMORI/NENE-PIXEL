package io.github.hideyukimori.nenepixel.buildlogic

internal data class ArchitectureViolation(
    val location: String,
    val message: String,
) : Comparable<ArchitectureViolation> {
    override fun compareTo(other: ArchitectureViolation): Int =
        compareValuesBy(this, other, ArchitectureViolation::location, ArchitectureViolation::message)

    override fun toString(): String = "$location: $message"
}
