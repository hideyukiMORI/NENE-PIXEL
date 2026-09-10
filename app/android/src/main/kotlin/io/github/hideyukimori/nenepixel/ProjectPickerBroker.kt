package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.adapters.persistence.ProjectDocumentPicker
import io.github.hideyukimori.nenepixel.adapters.persistence.ProjectPickerResult
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectTransportPhase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class ProjectPickerBroker : ProjectDocumentPicker {
    private val lock = Any()
    private var active: ActivePickerRequest? = null
    private val mutablePendingRequest = MutableStateFlow<ProjectPickerRequest?>(null)

    val pendingRequest: StateFlow<ProjectPickerRequest?> = mutablePendingRequest.asStateFlow()

    override suspend fun createDocument(suggestedName: String): ProjectPickerResult =
        awaitRequest { ProjectPickerRequest.Create(suggestedName) }

    override suspend fun openDocument(): ProjectPickerResult = awaitRequest { ProjectPickerRequest.Open() }

    fun claim(request: ProjectPickerRequest): Boolean =
        synchronized(lock) {
            val current = active
            if (current == null || current.request !== request || mutablePendingRequest.value !== request) {
                false
            } else {
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
        val accepted =
            synchronized(lock) {
                if (active == null) {
                    active = ActivePickerRequest(request, completion)
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
        } finally {
            synchronized(lock) {
                if (active?.request === request) {
                    active = null
                    mutablePendingRequest.value = null
                }
            }
        }
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
        active = null
        mutablePendingRequest.value = null
        current.completion.complete(result)
    }
}

internal sealed interface ProjectPickerRequest {
    val kind: ProjectPickerKind

    class Create(
        val suggestedName: String,
    ) : ProjectPickerRequest {
        override val kind: ProjectPickerKind = ProjectPickerKind.Create
    }

    class Open : ProjectPickerRequest {
        override val kind: ProjectPickerKind = ProjectPickerKind.Open
    }
}

internal enum class ProjectPickerKind { Create, Open }

private data class ActivePickerRequest(
    val request: ProjectPickerRequest,
    val completion: CompletableDeferred<ProjectPickerResult>,
)
