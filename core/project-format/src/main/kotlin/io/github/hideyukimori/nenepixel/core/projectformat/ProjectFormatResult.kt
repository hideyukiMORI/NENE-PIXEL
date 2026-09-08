package io.github.hideyukimori.nenepixel.core.projectformat

public sealed interface ProjectFormatResult<out T> {
    public data class Accepted<out T> internal constructor(
        public val value: T,
    ) : ProjectFormatResult<T>

    public data class Rejected internal constructor(
        public val rejection: ProjectFormatRejection,
    ) : ProjectFormatResult<Nothing>
}

internal fun <T> accepted(value: T): ProjectFormatResult<T> = ProjectFormatResult.Accepted(value)

internal fun rejected(rejection: ProjectFormatRejection): ProjectFormatResult<Nothing> =
    ProjectFormatResult.Rejected(rejection)
