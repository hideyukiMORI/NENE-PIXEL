package io.github.hideyukimori.nenepixel.core.projectformat.palette

public sealed interface PaletteJsonResult<out T> {
    public data class Accepted<out T> internal constructor(
        public val value: T,
    ) : PaletteJsonResult<T>

    public data class Rejected internal constructor(
        public val rejection: PaletteJsonRejection,
    ) : PaletteJsonResult<Nothing>
}

internal fun <T> accepted(value: T): PaletteJsonResult<T> = PaletteJsonResult.Accepted(value)

internal fun rejected(rejection: PaletteJsonRejection): PaletteJsonResult<Nothing> =
    PaletteJsonResult.Rejected(rejection)

internal inline fun <T, R> PaletteJsonResult<T>.andThen(next: (T) -> PaletteJsonResult<R>): PaletteJsonResult<R> =
    when (this) {
        is PaletteJsonResult.Accepted -> next(value)
        is PaletteJsonResult.Rejected -> this
    }
