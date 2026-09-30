package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult
import org.junit.jupiter.api.fail

/** Builds the pick workflow straight from the fixture's runtime until `PersistencePorts` carries the port. */
internal fun referenceImageWorkflow(
    fixture: Fixture,
    port: ReferenceImagePort,
): ReferenceImageWorkflow =
    ReferenceImageWorkflow(
        PersistenceReferenceImageFlow(
            fixture.runtime.referenceImageOperations,
            port,
            PersistenceAutosaveFlow(fixture.runtime.autosaveOperations, fixture.recovery),
        ),
    )

internal fun referenceImage(
    width: Int,
    height: Int,
): ReferenceImage =
    when (val result = ReferenceImage.create(width, height, IntArray(width * height))) {
        is ReferenceImageResult.Created -> result.image
        else -> fail("Reference image fixture was rejected: $result")
    }
