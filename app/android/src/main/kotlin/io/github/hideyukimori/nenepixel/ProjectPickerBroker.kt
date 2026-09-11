package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.adapters.persistence.DocumentCreationRequest
import io.github.hideyukimori.nenepixel.adapters.persistence.ProjectDocumentPicker
import io.github.hideyukimori.nenepixel.adapters.persistence.ProjectPickerResult
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectTransportPhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

internal class ProjectPickerBroker : ProjectDocumentPicker {
    private val lock = Any()
    private var active: ActivePickerRequest? = null
    private val mutablePendingRequest = MutableStateFlow<ProjectPickerRequest?>(null)

    val pendingRequest: StateFlow<ProjectPickerRequest?> = mutablePendingRequest.asStateFlow()

    override suspend fun createDocument(request: DocumentCreationRequest): ProjectPickerResult =
        awaitRequest { ProjectPickerRequest.Create(request) }

    override suspend fun openDocument(): ProjectPickerResult = awaitRequest { ProjectPickerRequest.Open() }

    fun claim(request: ProjectPickerRequest): Boolean =
        synchronized(lock) {
            val current = active
            if (current == null || current.request !== request || mutablePendingRequest.value !== request) {
                false
            } else {
                current.claimed = true
                mutablePendingRequest.value = null
                true
            }
        }

    fun completeCreate(result: ProjectPickerResult) {
        complete(ProjectPickerKind.Create, result)
    }

    fun completeOpen(result: ProjectPickerResult) {
        complete(ProjectPickerKind.Open, result)
    }

    fun failLaunch(
        request: ProjectPickerRequest,
        failure: ProjectStorageFailure,
    ) {
        synchronized(lock) {
            val current = active
            if (current != null && current.request === request) {
                completeLocked(current, ProjectPickerResult.Failed(failure))
            }
        }
    }

    private suspend fun awaitRequest(createRequest: () -> ProjectPickerRequest): ProjectPickerResult {
        val completion = CompletableDeferred<ProjectPickerResult>()
        val request = createRequest()
        val submitted = ActivePickerRequest(request, completion)
        val accepted =
            synchronized(lock) {
                if (active == null) {
                    active = submitted
                    mutablePendingRequest.value = request
                    true
                } else {
                    false
                }
            }
        if (!accepted) {
            return ProjectPickerResult.Failed(
                ProjectStorageFailure.ProviderUnavailable(ProjectTransportPhase.PICKER_RESULT),
            )
        }
        return try {
            completion.await()
        } catch (cancelled: CancellationException) {
            drainClaimed(submitted, cancelled)
        } finally {
            synchronized(lock) {
                if (active?.request === request) {
                    active = null
                    mutablePendingRequest.value = null
                }
            }
        }
    }

    private suspend fun drainClaimed(
        submitted: ActivePickerRequest,
        cancelled: CancellationException,
    ): ProjectPickerResult {
        val mustDrain =
            synchronized(lock) {
                if (submitted.claimed || submitted.completion.isCompleted) {
                    true
                } else {
                    if (active === submitted) {
                        active = null
                        mutablePendingRequest.value = null
                    }
                    false
                }
            }
        if (!mustDrain) throw cancelled
        // A selected fresh URI must reach adapter cleanup before the physical lease is released.
        val result = withContext(NonCancellable) { submitted.completion.await() }
        if (result !is ProjectPickerResult.Selected) throw cancelled
        return result
    }

    private fun complete(
        expectedKind: ProjectPickerKind,
        result: ProjectPickerResult,
    ) {
        synchronized(lock) {
            val current = active
            if (current != null && current.request.kind == expectedKind) {
                completeLocked(current, result)
            }
        }
    }

    private fun completeLocked(
        current: ActivePickerRequest,
        result: ProjectPickerResult,
    ) {
        mutablePendingRequest.value = null
        current.completion.complete(result)
    }
}

internal sealed interface ProjectPickerRequest {
    val kind: ProjectPickerKind

    class Create(
        val creation: DocumentCreationRequest,
    ) : ProjectPickerRequest {
        override val kind: ProjectPickerKind = ProjectPickerKind.Create
    }

    class Open : ProjectPickerRequest {
        override val kind: ProjectPickerKind = ProjectPickerKind.Open
    }
}

internal enum class ProjectPickerKind { Create, Open }

private class ActivePickerRequest(
    val request: ProjectPickerRequest,
    val completion: CompletableDeferred<ProjectPickerResult>,
    var claimed: Boolean = false,
)
