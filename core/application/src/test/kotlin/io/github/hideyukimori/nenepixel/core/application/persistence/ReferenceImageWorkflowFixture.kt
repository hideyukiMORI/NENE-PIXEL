package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult
import org.junit.jupiter.api.fail

internal fun referenceImage(
    width: Int,
    height: Int,
): ReferenceImage =
    when (val result = ReferenceImage.create(width, height, IntArray(width * height))) {
        is ReferenceImageResult.Created -> result.image
        else -> fail("Reference image fixture was rejected: $result")
    }
