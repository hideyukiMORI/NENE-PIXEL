package io.github.hideyukimori.nenepixel.presentation.compose.editor

import org.junit.jupiter.api.Test

internal class P2RenderProjectionMeasurementTest {
    @Test
    fun `measure current host render projection matrix`() {
        P2RenderProjectionMeasurementReport.write(P2RenderProjectionMeasurement().measureAll())
    }
}
