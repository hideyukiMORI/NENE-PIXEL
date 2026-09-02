package io.github.hideyukimori.nenepixel.measurement

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class P2AndroidMemoryAggregateTest {
    @Test
    fun auditFiveImmutableRunsAndWriteAggregate() {
        val environment = P2AndroidMeasurementEnvironment.fromRunnerArguments()
        P2AndroidMemoryProtocol.validateEnvironment(environment)
        val identity = P2AndroidMemoryProtocol.aggregateIdentity()
        val runs = P2AndroidMemoryRawAudit.readAll(environment, identity)
        val output =
            P2AndroidMemoryAggregateReport.write(
                P2AndroidMemoryAggregateInput(environment, identity, runs),
            )
        assertTrue(output.isFile)
        assertTrue(output.length() > 0L)
        println("P2_ANDROID_MEMORY_AGGREGATE_OUTPUT=${output.absolutePath}")
    }
}
