package io.github.hideyukimori.nenepixel.measurement

import io.github.hideyukimori.nenepixel.adapters.persistence.DocumentCreationRequest
import io.github.hideyukimori.nenepixel.adapters.persistence.DocumentOpenRequest
import io.github.hideyukimori.nenepixel.adapters.persistence.DocumentOutputFormat
import io.github.hideyukimori.nenepixel.adapters.persistence.ProjectDocumentPicker
import io.github.hideyukimori.nenepixel.adapters.persistence.ProjectPickerResult

internal class P4LayerSafPicker(
    private val destinations: List<P4GrantedDocument>,
    private val journal: P4LayerSafJournal,
) : ProjectDocumentPicker {
    private var consumed = 0

    init {
        check(destinations.size == 25 && destinations.map { it.uri }.distinct().size == 25)
    }

    override suspend fun createDocument(request: DocumentCreationRequest): ProjectPickerResult = consume(request)

    override suspend fun openDocument(request: DocumentOpenRequest): ProjectPickerResult = error("Save-only picker")

    @Synchronized
    fun consumedCount(): Int = consumed

    @Synchronized
    private fun consume(request: DocumentCreationRequest): ProjectPickerResult {
        check(journal.isOpen() && request.format == DocumentOutputFormat.PROJECT && consumed < destinations.size)
        return ProjectPickerResult.Selected(destinations[consumed++].uri)
    }
}
