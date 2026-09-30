package io.github.hideyukimori.nenepixel.core.application.persistence

/** Picks a reference image and, when accepted, shows it under the drawing (ADR 0032). */
public class ReferenceImageWorkflow internal constructor(
    private val flow: PersistenceReferenceImageFlow,
) {
    public suspend fun pick(): PersistenceRequestResult = flow.pick()
}
