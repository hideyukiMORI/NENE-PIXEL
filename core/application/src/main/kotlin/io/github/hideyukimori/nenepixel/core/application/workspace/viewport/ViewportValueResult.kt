package io.github.hideyukimori.nenepixel.core.application.workspace.viewport

public sealed interface ViewportValueResult<out T> {
    public data class Created<out T> internal constructor(
        public val value: T,
    ) : ViewportValueResult<T>

    public data class Rejected internal constructor(
        public val rejection: ViewportValueRejection,
    ) : ViewportValueResult<Nothing>
}

internal fun <T> viewportCreated(value: T): ViewportValueResult<T> = ViewportValueResult.Created(value)

internal fun viewportRejected(rejection: ViewportValueRejection): ViewportValueResult<Nothing> =
    ViewportValueResult.Rejected(rejection)
