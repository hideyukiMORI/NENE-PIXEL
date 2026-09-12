package io.github.hideyukimori.nenepixel.core.projectformat.palette

import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

private const val WARMUPS: Int = 5
private const val SAMPLES: Int = 20
private const val ANOMALY_NANOS: Long = 1_000_000_000L

internal fun main() {
    val inputs = PaletteEvidenceInputs()
    inputs.verify()
    println("schema,palette-json-host-v1")
    println("java_version,${System.getProperty("java.version")}")
    println("java_vm,${System.getProperty("java.vm.name")}")
    println("os,${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}")
    println("warmups,$WARMUPS")
    println("samples_per_group,$SAMPLES")
    println("canonical_bytes,${inputs.minimumBytes.byteCount},${inputs.maximumBytes.byteCount}")
    println("group,sample,latency_nanos")
    System.out.flush()
    val observations = observe(inputs)
    inputs.verify()
    observations.groupBy(PaletteLatencyObservation::group).forEach { (group, rows) ->
        val minimum = rows.minOf(PaletteLatencyObservation::nanos)
        val maximum = rows.maxOf(PaletteLatencyObservation::nanos)
        println("summary,$group,$minimum,$maximum")
    }
    System.out.flush()
}

private fun observe(inputs: PaletteEvidenceInputs): List<PaletteLatencyObservation> =
    buildList {
        addAll(measure("palette_32", { Palette.create(inputs.colors32) }) { verifyPalette(it, 32) })
        addAll(measure("palette_256", { Palette.create(inputs.colors256) }) { verifyPalette(it, 256) })
        addAll(
            measure("encode_2", { PaletteJsonCodec.encode(inputs.minimum) }) {
                check(it.byteCount == inputs.minimumBytes.byteCount)
            },
        )
        addAll(measure("decode_2", { PaletteJsonCodec.decode(inputs.minimumBytes) }) { verifyDefinition(it, 2, 0) })
        addAll(
            measure("encode_256", { PaletteJsonCodec.encode(inputs.maximum) }) {
                check(it.byteCount == inputs.maximumBytes.byteCount)
            },
        )
        addAll(
            measure("decode_256", { PaletteJsonCodec.decode(inputs.maximumBytes) }) { verifyDefinition(it, 256, 255) },
        )
        addAll(
            measure("decode_16k", { PaletteJsonCodec.decode(inputs.paddedBytes) }) { verifyDefinition(it, 256, 255) },
        )
    }

private fun <T> measure(
    group: String,
    operation: () -> T,
    verify: (T) -> Unit,
): List<PaletteLatencyObservation> {
    repeat(WARMUPS) { verify(operation()) }
    return List(SAMPLES) { index ->
        val start = System.nanoTime()
        val result = operation()
        val nanos = System.nanoTime() - start
        println("$group,$index,$nanos")
        System.out.flush()
        verify(result)
        check(nanos <= ANOMALY_NANOS) { "$group sample $index exceeded one second" }
        PaletteLatencyObservation(group, nanos)
    }
}

private fun verifyPalette(
    result: DomainValueResult<Palette>,
    count: Int,
) {
    check(result is DomainValueResult.Created && result.value.entryCount == count)
}

private fun verifyDefinition(
    result: PaletteJsonResult<PaletteDefinition>,
    count: Int,
    default: Int,
) {
    check(result is PaletteJsonResult.Accepted)
    check(result.value.palette.entryCount == count && result.value.defaultIndex.value == default)
}

private class PaletteEvidenceInputs {
    val minimum = PaletteJsonTestValues.accepted(PaletteJsonTestValues.decode(PaletteJsonTestValues.MINIMAL))
    val maximum = PaletteJsonTestValues.definition(256)
    val minimumBytes = PaletteJsonTestValues.carrier(PaletteJsonTestValues.MINIMAL.encodeToByteArray())
    val maximumBytes = PaletteJsonCodec.encode(maximum)
    val paddedBytes =
        PaletteJsonTestValues.carrier(
            maximumBytes
                .copyBytes()
                .decodeToString()
                .padEnd(PaletteJsonBytes.MAX_FILE_BYTE_COUNT)
                .encodeToByteArray(),
        )
    val colors32 =
        maximum.palette
            .entries()
            .take(32)
            .map { it.color }
    val colors256 = maximum.palette.entries().map { it.color }

    fun verify() {
        check(PaletteJsonCodec.encode(minimum) == minimumBytes)
        check(PaletteJsonTestValues.accepted(PaletteJsonCodec.decode(minimumBytes)) == minimum)
        check(PaletteJsonTestValues.accepted(PaletteJsonCodec.decode(maximumBytes)) == maximum)
        check(PaletteJsonTestValues.accepted(PaletteJsonCodec.decode(paddedBytes)) == maximum)
        check(paddedBytes.byteCount == PaletteJsonBytes.MAX_FILE_BYTE_COUNT)
    }
}

private data class PaletteLatencyObservation(
    val group: String,
    val nanos: Long,
)
