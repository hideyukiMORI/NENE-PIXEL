package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectTransportPhase
import kotlinx.coroutines.CancellationException
import java.io.FileNotFoundException
import java.io.IOException

internal sealed interface ProjectAccessResult<out T> {
    data class Value<T>(
        val value: T,
    ) : ProjectAccessResult<T>

    data class Failed(
        val failure: ProjectStorageFailure,
    ) : ProjectAccessResult<Nothing>

    companion object {
        fun <T> of(
            phase: ProjectTransportPhase,
            block: () -> T,
        ): ProjectAccessResult<T> =
            try {
                Value(block())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                Failed(ProjectStorageFailure.PermissionDenied(phase))
            } catch (_: UnsupportedOperationException) {
                Failed(ProjectStorageFailure.UnsupportedProvider(phase))
            } catch (_: FileNotFoundException) {
                Failed(ProjectStorageFailure.ProviderUnavailable(phase))
            } catch (_: IOException) {
                Failed(ProjectStorageFailure.IoFailure(phase))
            } catch (_: RuntimeException) {
                Failed(ProjectStorageFailure.ProviderUnavailable(phase))
            }

        fun closeFailure(
            stream: AutoCloseable,
            phase: ProjectTransportPhase,
        ): ProjectStorageFailure? =
            when (val result = of(phase) { stream.close() }) {
                is Value -> null
                is Failed -> result.failure
            }
    }
}
