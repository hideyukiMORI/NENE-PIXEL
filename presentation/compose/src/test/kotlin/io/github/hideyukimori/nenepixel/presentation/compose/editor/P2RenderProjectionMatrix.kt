package io.github.hideyukimori.nenepixel.presentation.compose.editor

internal object P2RenderProjectionMatrix {
    val shapes: List<P2RenderProjectionShape> =
        listOf(
            P2RenderProjectionShape("64x64", 64, 64),
            P2RenderProjectionShape("16x256", 16, 256),
            P2RenderProjectionShape("256x16", 256, 16),
            P2RenderProjectionShape("128x128", 128, 128),
            P2RenderProjectionShape("64x256", 64, 256),
            P2RenderProjectionShape("256x64", 256, 64),
            P2RenderProjectionShape("256x256", 256, 256),
        )

    val descriptors: List<P2RenderProjectionDescriptor> =
        shapes.flatMap { shape ->
            P2RenderProjectionContent.entries.map { content -> P2RenderProjectionDescriptor(shape, content) }
        }

    fun validate(descriptors: List<P2RenderProjectionDescriptor>) {
        check(descriptors.size == METRIC_COUNT) { "Host render-projection metric count changed." }
        check(descriptors.toSet().size == METRIC_COUNT) { "Host render-projection descriptors are duplicated." }
        check(descriptors.toSet() == this.descriptors.toSet()) { "Host render-projection matrix changed." }
    }

    const val METRIC_COUNT: Int = 28
    const val WARMUP_ITERATIONS: Int = 5
    const val SAMPLE_COUNT: Int = 10
    const val RAW_SAMPLE_COUNT: Int = METRIC_COUNT * SAMPLE_COUNT
    val sampling: P2HostSampling = P2HostSampling(WARMUP_ITERATIONS, SAMPLE_COUNT)
}

internal data class P2RenderProjectionDescriptor(
    val shape: P2RenderProjectionShape,
    val content: P2RenderProjectionContent,
)

internal data class P2RenderProjectionShape(
    val csvName: String,
    val width: Int,
    val height: Int,
) {
    val pixelCount: Int
        get() = width * height
}

internal enum class P2RenderProjectionContent(
    val csvName: String,
    val description: String,
) {
    ReferenceBlank(
        "reference_blank_opaque_white",
        "uniform opaque-white current reference blank",
    ),
    OneColor(
        "one_color_opaque_red",
        "uniform opaque red one-color input",
    ),
    Exact256Rgba(
        "exact_256_deterministic_rgba",
        "exactly 256 deterministic RGBA colors repeated row-major",
    ),
    HighEntropyRgba(
        "deterministic_high_entropy_rgba",
        "deterministic high-entropy RGBA unique by row-major index",
    ),
    ;

    fun argbAt(index: Int): Int =
        when (this) {
            ReferenceBlank -> OPAQUE_WHITE
            OneColor -> OPAQUE_RED
            Exact256Rgba -> mixedArgb(index and UBYTE_MASK)
            HighEntropyRgba -> mixedArgb(index)
        }

    fun expectedColorCardinality(pixelCount: Int): Int =
        when (this) {
            ReferenceBlank, OneColor -> ONE_COLOR
            Exact256Rgba -> EXACT_PALETTE_COLORS
            HighEntropyRgba -> pixelCount
        }

    private companion object {
        const val ONE_COLOR: Int = 1
        const val EXACT_PALETTE_COLORS: Int = 256
        const val UBYTE_MASK: Int = 0xff
        const val MIX_ROTATION: Int = 13
        const val OPAQUE_WHITE: Int = -1
        const val OPAQUE_RED: Int = -65_536
        val MIX_MULTIPLIER: Int = 0x9e3779b9.toInt()
        val MIX_XOR: Int = 0xa5c3e27d.toInt()

        fun mixedArgb(index: Int): Int = Integer.rotateLeft(index * MIX_MULTIPLIER, MIX_ROTATION) xor MIX_XOR
    }
}
