package io.github.hideyukimori.nenepixel.measurement

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.DocumentCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.RedoCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.document.command.ReplacePaletteCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.UndoCommand
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteRemap
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import java.util.concurrent.atomic.AtomicInteger

internal enum class P2CommandWorkloadKind(
    val metricName: String,
) {
    SparseApply("sparse_apply_stroke"),
    DenseApply("dense_apply_stroke"),
    DenseEraser("dense_eraser_stroke"),
    DenseNoOp("dense_same_target_no_op"),
    DenseUndo("dense_undo"),
    DenseRedo("dense_redo"),
    PaletteRecolorFull("palette_recolor_full"),
    PaletteDefaultOnly("palette_default_only"),
    PaletteManyToOneDense("palette_many_to_one_dense"),
    PaletteManyToOneUndo("palette_many_to_one_undo"),
    PaletteManyToOneRedo("palette_many_to_one_redo"),
}

internal data class P2CommandWorkloadSpec(
    val kind: P2CommandWorkloadKind,
    val canvasWidth: Int,
    val canvasHeight: Int,
) {
    val positionCount: Int
        get() =
            if (kind == P2CommandWorkloadKind.SparseApply) {
                minOf(canvasWidth, canvasHeight)
            } else {
                canvasWidth * canvasHeight
            }
}

internal object P2CommandWorkloadCatalog {
    private val CANVAS_EDGES: List<Int> = listOf(16, 64, 256)
    val commonKinds: List<P2CommandWorkloadKind> = P2CommandWorkloadKind.entries.take(COMMON_WORKLOAD_COUNT)
    val candidateKinds: List<P2CommandWorkloadKind> = P2CommandWorkloadKind.entries

    val specs: List<P2CommandWorkloadSpec> =
        CANVAS_EDGES.flatMap(::squareSpecs)

    fun squareSpecs(edge: Int): List<P2CommandWorkloadSpec> {
        require(edge > 0) { "A square workload edge must be positive." }
        return shapeSpecs(edge, edge)
    }

    fun shapeSpecs(
        width: Int,
        height: Int,
        kinds: List<P2CommandWorkloadKind> = commonKinds,
    ): List<P2CommandWorkloadSpec> {
        require(width > 0 && height > 0) { "Workload width and height must be positive." }
        return kinds.map { kind -> P2CommandWorkloadSpec(kind, width, height) }
    }

    private const val COMMON_WORKLOAD_COUNT: Int = 6
}

internal data class CommandOutcomeDescriptor(
    val resultKind: String,
    val sourceRevision: Long,
    val revision: Long,
    val history: String,
    val changeSetBeforeRevision: Long?,
    val changeSetAfterRevision: Long?,
    val renderInvalidation: P2CommandRegionDescriptor?,
    val definitionTransition: String,
    val defaultIndexBefore: Int,
    val defaultIndexAfter: Int,
    val expectedDefinitionIdentity: Boolean,
    val unchangedStateIdentity: Boolean,
)

internal data class CommandCorrectnessDescriptor(
    val spec: P2CommandWorkloadSpec,
    val outcome: CommandOutcomeDescriptor,
    val documentHash: Int,
    val snapshotHash: Int,
)

internal data class P2CommandRegionDescriptor(
    val originX: Int,
    val originY: Int,
    val width: Int,
    val height: Int,
)

internal data class P2CommandResultDescriptor(
    val resultKind: String,
    val changeSetBeforeRevision: Long?,
    val changeSetAfterRevision: Long?,
    val renderInvalidation: P2CommandRegionDescriptor?,
)

internal object P2CommandOraclePreparationTracker {
    private val eraserExpectedDocumentCount = AtomicInteger()

    fun resetEraserExpectedDocumentCount() {
        eraserExpectedDocumentCount.set(0)
    }

    fun recordEraserExpectedDocument() {
        eraserExpectedDocumentCount.incrementAndGet()
    }

    fun eraserExpectedDocumentCount(): Int = eraserExpectedDocumentCount.get()
}

internal class PreparedCommandWorkload internal constructor(
    val spec: P2CommandWorkloadSpec,
    private val gateway: CommandGateway,
    private var command: DocumentCommand?,
    private val expectedState: DocumentState?,
    private val unchangedStateReference: DocumentState?,
    private val expectedResult: ExpectedCommandResult,
    private val sourceDocument: DocumentState,
    private val expectedDefinition: PaletteDefinition,
    private val expectedBeforeRevision: Long,
    private val expectedAfterRevision: Long,
    private val expectedHistory: HistoryAvailability,
    private val expectedRenderInvalidation: PixelRegion?,
    private val expectSameStateInstance: Boolean,
) {
    internal val correctnessOraclePrepared: Boolean = expectedState != null

    internal var fullStateVerificationPerformed: Boolean = false
        private set

    fun execute(): CommandResult {
        val nextCommand = requireNotNull(command) { "A measurement command may execute only once." }
        command = null
        return gateway.execute(nextCommand)
    }

    fun verifySample(result: CommandResult): CommandOutcomeDescriptor {
        val runtimeState = gateway.runtimeState
        assertEquals(expectedBeforeRevision, sourceDocument.revision.value)
        assertSame(expectedDefinition, runtimeState.documentState.definition)
        if (expectSameStateInstance) assertSame(requireNotNull(unchangedStateReference), runtimeState.documentState)
        assertEquals(expectedAfterRevision, runtimeState.documentState.revision.value)
        assertEquals(expectedHistory, runtimeState.historyAvailability)
        val resultDescriptor =
            expectedResult.verify(
                result,
                P2ExpectedCommandTransition(
                    beforeRevision = expectedBeforeRevision,
                    afterRevision = expectedAfterRevision,
                    renderInvalidation = expectedRenderInvalidation,
                ),
            )
        return CommandOutcomeDescriptor(
            resultKind = resultDescriptor.resultKind,
            sourceRevision = sourceDocument.revision.value,
            revision = runtimeState.documentState.revision.value,
            history = runtimeState.historyAvailability.csvName(),
            changeSetBeforeRevision = resultDescriptor.changeSetBeforeRevision,
            changeSetAfterRevision = resultDescriptor.changeSetAfterRevision,
            renderInvalidation = resultDescriptor.renderInvalidation,
            definitionTransition = if (sourceDocument.definition === expectedDefinition) "unchanged" else "changed",
            defaultIndexBefore = sourceDocument.definition.defaultIndex.value,
            defaultIndexAfter = runtimeState.documentState.definition.defaultIndex.value,
            expectedDefinitionIdentity = runtimeState.documentState.definition === expectedDefinition,
            unchangedStateIdentity = runtimeState.documentState === unchangedStateReference,
        )
    }

    fun verifyCorrectness(result: CommandResult): CommandCorrectnessDescriptor {
        val runtimeState = gateway.runtimeState
        assertEquals(requireNotNull(expectedState), runtimeState.documentState)
        fullStateVerificationPerformed = true
        return CommandCorrectnessDescriptor(
            spec = spec,
            outcome = verifySample(result),
            documentHash = runtimeState.documentState.hashCode(),
            snapshotHash = runtimeState.documentState.snapshot.hashCode(),
        )
    }

    companion object {
        fun createLatency(spec: P2CommandWorkloadSpec): PreparedCommandWorkload = create(spec, correctness = false)

        fun createCorrectness(spec: P2CommandWorkloadSpec): PreparedCommandWorkload = create(spec, correctness = true)

        private fun create(
            spec: P2CommandWorkloadSpec,
            correctness: Boolean,
        ): PreparedCommandWorkload =
            when (spec.kind) {
                P2CommandWorkloadKind.SparseApply -> WorkloadFactory.apply(spec, sparse = true, correctness)
                P2CommandWorkloadKind.DenseApply -> WorkloadFactory.apply(spec, sparse = false, correctness)
                P2CommandWorkloadKind.DenseEraser -> WorkloadFactory.erase(spec, correctness)
                P2CommandWorkloadKind.DenseNoOp -> WorkloadFactory.noOp(spec, correctness)
                P2CommandWorkloadKind.DenseUndo -> WorkloadFactory.undo(spec, correctness)
                P2CommandWorkloadKind.DenseRedo -> WorkloadFactory.redo(spec, correctness)
                P2CommandWorkloadKind.PaletteRecolorFull -> WorkloadFactory.paletteRecolor(spec, correctness)
                P2CommandWorkloadKind.PaletteDefaultOnly -> WorkloadFactory.paletteDefault(spec, correctness)
                P2CommandWorkloadKind.PaletteManyToOneDense -> WorkloadFactory.paletteManyToOne(spec, correctness)
                P2CommandWorkloadKind.PaletteManyToOneUndo -> WorkloadFactory.paletteManyToOneUndo(spec, correctness)
                P2CommandWorkloadKind.PaletteManyToOneRedo -> WorkloadFactory.paletteManyToOneRedo(spec, correctness)
            }
    }
}

internal enum class ExpectedCommandResult {
    Applied,
    NoEffectiveChange,
    ;

    fun verify(
        result: CommandResult,
        expected: P2ExpectedCommandTransition,
    ): P2CommandResultDescriptor =
        when (this) {
            Applied -> result.requireApplied(expected)
            NoEffectiveChange -> result.requireNoEffectiveChange()
        }
}

internal data class P2ExpectedCommandTransition(
    val beforeRevision: Long,
    val afterRevision: Long,
    val renderInvalidation: PixelRegion?,
)

private object WorkloadFactory {
    fun apply(
        spec: P2CommandWorkloadSpec,
        sparse: Boolean,
        correctness: Boolean,
    ): PreparedCommandWorkload {
        val values = CoreMeasurementValues(spec.canvasWidth, spec.canvasHeight)
        val initial = values.document(Revision.initial(), values.whitePixels())
        val path = if (sparse) values.diagonalPath() else values.densePath()
        val gateway = CommandGateway.create(initial)
        return prepared(
            spec = spec,
            gateway = gateway,
            command = values.applyCommand(gateway, path),
            sourceDocument = initial,
            expectedDefinition = initial.definition,
            expectedState =
                if (correctness) {
                    val expectedPixels = if (sparse) values.diagonalRedPixels() else values.redPixels()
                    values.document(values.revision(1L), expectedPixels)
                } else {
                    null
                },
            unchangedStateReference = null,
            expectedResult = ExpectedCommandResult.Applied,
            beforeRevision = 0L,
            afterRevision = 1L,
            history = HistoryAvailability.UndoAvailable,
            renderInvalidation = if (sparse) values.diagonalRegion() else values.fullRegion(),
        )
    }

    fun erase(
        spec: P2CommandWorkloadSpec,
        correctness: Boolean,
    ): PreparedCommandWorkload {
        val values = CoreMeasurementValues(spec.canvasWidth, spec.canvasHeight)
        val initial = values.document(Revision.initial(), values.redPixels())
        val gateway = CommandGateway.create(initial)
        return prepared(
            spec = spec,
            gateway = gateway,
            command = values.eraseCommand(gateway, values.densePath()),
            sourceDocument = initial,
            expectedDefinition = initial.definition,
            expectedState =
                if (correctness) {
                    P2CommandOraclePreparationTracker.recordEraserExpectedDocument()
                    values.document(values.revision(1L), values.blankPixels())
                } else {
                    null
                },
            unchangedStateReference = null,
            expectedResult = ExpectedCommandResult.Applied,
            beforeRevision = 0L,
            afterRevision = 1L,
            history = HistoryAvailability.UndoAvailable,
            renderInvalidation = values.fullRegion(),
        )
    }

    fun noOp(
        spec: P2CommandWorkloadSpec,
        correctness: Boolean,
    ): PreparedCommandWorkload {
        val values = CoreMeasurementValues(spec.canvasWidth, spec.canvasHeight)
        val initial = values.document(Revision.initial(), values.redPixels())
        val gateway = CommandGateway.create(initial)
        return prepared(
            spec = spec,
            gateway = gateway,
            command = values.applyCommand(gateway, values.densePath()),
            sourceDocument = initial,
            expectedDefinition = initial.definition,
            expectedState = initial.takeIf { correctness },
            unchangedStateReference = initial,
            expectedResult = ExpectedCommandResult.NoEffectiveChange,
            beforeRevision = 0L,
            afterRevision = 0L,
            history = HistoryAvailability.None,
            renderInvalidation = null,
            expectSameStateInstance = true,
        )
    }

    fun undo(
        spec: P2CommandWorkloadSpec,
        correctness: Boolean,
    ): PreparedCommandWorkload {
        val values = CoreMeasurementValues(spec.canvasWidth, spec.canvasHeight)
        val initial = values.document(Revision.initial(), values.whitePixels())
        val gateway = CommandGateway.create(initial)
        gateway
            .execute(values.applyCommand(gateway, values.densePath()))
            .requireApplied(P2ExpectedCommandTransition(0L, 1L, values.fullRegion()))
        val applied = gateway.runtimeState.documentState
        return prepared(
            spec = spec,
            gateway = gateway,
            command = UndoCommand.create(applied.id, applied.revision),
            sourceDocument = applied,
            expectedDefinition = initial.definition,
            expectedState = initial.takeIf { correctness },
            unchangedStateReference = null,
            expectedResult = ExpectedCommandResult.Applied,
            beforeRevision = 1L,
            afterRevision = 0L,
            history = HistoryAvailability.RedoAvailable,
            renderInvalidation = values.fullRegion(),
        )
    }

    fun redo(
        spec: P2CommandWorkloadSpec,
        correctness: Boolean,
    ): PreparedCommandWorkload {
        val values = CoreMeasurementValues(spec.canvasWidth, spec.canvasHeight)
        val initial = values.document(Revision.initial(), values.whitePixels())
        val gateway = CommandGateway.create(initial)
        gateway
            .execute(values.applyCommand(gateway, values.densePath()))
            .requireApplied(P2ExpectedCommandTransition(0L, 1L, values.fullRegion()))
        val applied = gateway.runtimeState.documentState
        gateway
            .execute(UndoCommand.create(applied.id, applied.revision))
            .requireApplied(P2ExpectedCommandTransition(1L, 0L, values.fullRegion()))
        val undone = gateway.runtimeState.documentState
        return prepared(
            spec = spec,
            gateway = gateway,
            command = RedoCommand.create(undone.id, undone.revision),
            sourceDocument = undone,
            expectedDefinition = applied.definition,
            expectedState = applied.takeIf { correctness },
            unchangedStateReference = null,
            expectedResult = ExpectedCommandResult.Applied,
            beforeRevision = 0L,
            afterRevision = 1L,
            history = HistoryAvailability.UndoAvailable,
            renderInvalidation = values.fullRegion(),
        )
    }

    fun paletteRecolor(
        spec: P2CommandWorkloadSpec,
        correctness: Boolean,
    ): PreparedCommandWorkload {
        val values = IndexedPaletteMeasurementValues(spec.canvasWidth, spec.canvasHeight)
        return paletteForward(spec, correctness, values.recolorTransition())
    }

    fun paletteDefault(
        spec: P2CommandWorkloadSpec,
        correctness: Boolean,
    ): PreparedCommandWorkload {
        val values = IndexedPaletteMeasurementValues(spec.canvasWidth, spec.canvasHeight)
        return paletteForward(spec, correctness, values.defaultTransition())
    }

    fun paletteManyToOne(
        spec: P2CommandWorkloadSpec,
        correctness: Boolean,
    ): PreparedCommandWorkload {
        val values = IndexedPaletteMeasurementValues(spec.canvasWidth, spec.canvasHeight)
        return paletteForward(spec, correctness, values.manyToOneTransition())
    }

    fun paletteManyToOneUndo(
        spec: P2CommandWorkloadSpec,
        correctness: Boolean,
    ): PreparedCommandWorkload {
        val values = IndexedPaletteMeasurementValues(spec.canvasWidth, spec.canvasHeight)
        val transition = values.manyToOneTransition()
        val gateway = CommandGateway.create(transition.initial)
        gateway.execute(ReplacePaletteCommand.create(gateway.captureSource(), transition.remap)).requireApplied(
            P2ExpectedCommandTransition(0L, 1L, values.fullRegion()),
        )
        val applied = gateway.runtimeState.documentState
        return prepared(
            spec = spec,
            gateway = gateway,
            command = UndoCommand.create(applied.id, applied.revision),
            sourceDocument = applied,
            expectedDefinition = transition.initial.definition,
            expectedState = transition.initial.takeIf { correctness },
            unchangedStateReference = null,
            expectedResult = ExpectedCommandResult.Applied,
            beforeRevision = 1L,
            afterRevision = 0L,
            history = HistoryAvailability.RedoAvailable,
            renderInvalidation = values.fullRegion(),
        )
    }

    fun paletteManyToOneRedo(
        spec: P2CommandWorkloadSpec,
        correctness: Boolean,
    ): PreparedCommandWorkload {
        val values = IndexedPaletteMeasurementValues(spec.canvasWidth, spec.canvasHeight)
        val transition = values.manyToOneTransition()
        val gateway = CommandGateway.create(transition.initial)
        gateway.execute(ReplacePaletteCommand.create(gateway.captureSource(), transition.remap)).requireApplied(
            P2ExpectedCommandTransition(0L, 1L, values.fullRegion()),
        )
        val applied = gateway.runtimeState.documentState
        gateway.execute(UndoCommand.create(applied.id, applied.revision)).requireApplied(
            P2ExpectedCommandTransition(1L, 0L, values.fullRegion()),
        )
        val undone = gateway.runtimeState.documentState
        return prepared(
            spec = spec,
            gateway = gateway,
            command = RedoCommand.create(undone.id, undone.revision),
            sourceDocument = undone,
            expectedDefinition = transition.expected.definition,
            expectedState = transition.expected.takeIf { correctness },
            unchangedStateReference = null,
            expectedResult = ExpectedCommandResult.Applied,
            beforeRevision = 0L,
            afterRevision = 1L,
            history = HistoryAvailability.UndoAvailable,
            renderInvalidation = values.fullRegion(),
        )
    }

    private fun paletteForward(
        spec: P2CommandWorkloadSpec,
        correctness: Boolean,
        transition: IndexedPaletteTransitionFixture,
    ): PreparedCommandWorkload {
        val gateway = CommandGateway.create(transition.initial)
        return prepared(
            spec = spec,
            gateway = gateway,
            command = ReplacePaletteCommand.create(gateway.captureSource(), transition.remap),
            sourceDocument = transition.initial,
            expectedDefinition = transition.expected.definition,
            expectedState = transition.expected.takeIf { correctness },
            unchangedStateReference = null,
            expectedResult = ExpectedCommandResult.Applied,
            beforeRevision = 0L,
            afterRevision = 1L,
            history = HistoryAvailability.UndoAvailable,
            renderInvalidation = transition.expected.size.fullRegion(),
        )
    }

    private fun prepared(
        spec: P2CommandWorkloadSpec,
        gateway: CommandGateway,
        command: DocumentCommand,
        sourceDocument: DocumentState,
        expectedDefinition: PaletteDefinition,
        expectedState: DocumentState?,
        unchangedStateReference: DocumentState?,
        expectedResult: ExpectedCommandResult,
        beforeRevision: Long,
        afterRevision: Long,
        history: HistoryAvailability,
        renderInvalidation: PixelRegion?,
        expectSameStateInstance: Boolean = false,
    ): PreparedCommandWorkload =
        PreparedCommandWorkload(
            spec,
            gateway,
            command,
            expectedState,
            unchangedStateReference,
            expectedResult,
            sourceDocument,
            expectedDefinition,
            beforeRevision,
            afterRevision,
            history,
            renderInvalidation,
            expectSameStateInstance,
        )
}

private data class IndexedPaletteTransitionFixture(
    val initial: DocumentState,
    val expected: DocumentState,
    val remap: PaletteRemap,
)

private class IndexedPaletteMeasurementValues(
    canvasWidth: Int,
    canvasHeight: Int,
) {
    private val canvas =
        CanvasSize.create(
            CanvasWidth.create(canvasWidth).requiredValue(),
            CanvasHeight.create(canvasHeight).requiredValue(),
        )
    private val documentId = DocumentId.create(INDEXED_DOCUMENT_ID).requiredValue()
    private val sourcePalette =
        Palette.create(List(PALETTE_SIZE) { slot -> PixelColor.fromPackedRgba8888(slot) }).requiredValue()
    private val sourceDefinition = PaletteDefinition.create(sourcePalette, PaletteIndex.first).requiredValue()
    private val identityDestinations = List(PALETTE_SIZE) { slot -> PaletteIndex.create(slot).requiredValue() }

    fun recolorTransition(): IndexedPaletteTransitionFixture {
        val targetColors = sourcePalette.entries().map { entry -> entry.color }.toMutableList()
        targetColors[0] = PixelColor.fromPackedRgba8888(RECOLORED_SLOT_ZERO)
        val target =
            PaletteDefinition.create(Palette.create(targetColors).requiredValue(), PaletteIndex.first).requiredValue()
        val pixels = List(canvas.pixelCount.toInt()) { PaletteIndex.first }
        return fixture(sourceDefinition, target, pixels, pixels, identityDestinations)
    }

    fun defaultTransition(): IndexedPaletteTransitionFixture {
        val target =
            PaletteDefinition
                .create(sourcePalette, PaletteIndex.create(PALETTE_SIZE - 1).requiredValue())
                .requiredValue()
        val pixels = List(canvas.pixelCount.toInt()) { PaletteIndex.first }
        return fixture(sourceDefinition, target, pixels, pixels, identityDestinations)
    }

    fun manyToOneTransition(): IndexedPaletteTransitionFixture {
        val targetColors = sourcePalette.entries().take(2).map { entry -> entry.color }
        val target =
            PaletteDefinition.create(Palette.create(targetColors).requiredValue(), PaletteIndex.first).requiredValue()
        val before =
            List(canvas.pixelCount.toInt()) { position ->
                PaletteIndex.create(position % PALETTE_SIZE).requiredValue()
            }
        val destinations =
            List(PALETTE_SIZE) { slot ->
                PaletteIndex.create(if (slot == 1) 0 else 1).requiredValue()
            }
        val after = before.map { source -> destinations[source.value] }
        return fixture(sourceDefinition, target, before, after, destinations)
    }

    fun fullRegion(): PixelRegion = canvas.fullRegion()

    private fun fixture(
        source: PaletteDefinition,
        target: PaletteDefinition,
        before: List<PaletteIndex>,
        after: List<PaletteIndex>,
        destinations: List<PaletteIndex>,
    ): IndexedPaletteTransitionFixture =
        IndexedPaletteTransitionFixture(
            initial = document(source, Revision.initial(), before),
            expected = document(target, Revision.create(1L).requiredValue(), after),
            remap = PaletteRemap.create(source, target, destinations).requiredValue(),
        )

    private fun document(
        definition: PaletteDefinition,
        revision: Revision,
        indices: List<PaletteIndex>,
    ): DocumentState =
        DocumentState
            .create(
                documentId,
                definition,
                PixelSnapshot.create(canvas, revision, indices).requiredValue(),
            ).requiredValue()

    private companion object {
        const val PALETTE_SIZE: Int = 256
        const val RECOLORED_SLOT_ZERO: Int = 0x01000000
        const val INDEXED_DOCUMENT_ID: String = "55555555555555555555555555555555"
    }
}

private class CoreMeasurementValues(
    canvasWidth: Int,
    canvasHeight: Int,
) {
    private val canvas: CanvasSize =
        CanvasSize.create(
            CanvasWidth.create(canvasWidth).requiredValue(),
            CanvasHeight.create(canvasHeight).requiredValue(),
        )
    private val documentId: DocumentId = DocumentId.create(DOCUMENT_ID).requiredValue()
    private val definition: PaletteDefinition =
        PaletteDefinition
            .create(
                Palette
                    .create(
                        listOf(
                            color(CHANNEL_MAX, CHANNEL_MAX, CHANNEL_MAX, CHANNEL_MAX),
                            color(CHANNEL_MAX, CHANNEL_MIN, CHANNEL_MIN, CHANNEL_MAX),
                            PixelColor.blank,
                        ),
                    ).requiredValue(),
                DEFAULT_INDEX,
            ).requiredValue()

    fun whitePixels(): List<PaletteIndex> = List(canvas.pixelCount.toInt()) { PaletteIndex.first }

    fun redPixels(): List<PaletteIndex> = List(canvas.pixelCount.toInt()) { RED_INDEX }

    fun blankPixels(): List<PaletteIndex> = List(canvas.pixelCount.toInt()) { DEFAULT_INDEX }

    fun diagonalRedPixels(): List<PaletteIndex> =
        List(canvas.pixelCount.toInt()) { index ->
            if (index % canvas.width.value == index / canvas.width.value) RED_INDEX else PaletteIndex.first
        }

    fun diagonalPath(): List<PixelPosition> =
        List(minOf(canvas.width.value, canvas.height.value)) { coordinate -> position(coordinate, coordinate) }

    fun densePath(): List<PixelPosition> =
        List(canvas.pixelCount.toInt()) { index ->
            position(index % canvas.width.value, index / canvas.width.value)
        }

    fun document(
        revision: Revision,
        pixels: List<PaletteIndex>,
    ): DocumentState =
        DocumentState
            .create(
                documentId,
                definition,
                PixelSnapshot.create(canvas, revision, pixels).requiredValue(),
            ).requiredValue()

    fun applyCommand(
        gateway: CommandGateway,
        path: List<PixelPosition>,
    ): ApplyStrokeCommand =
        ApplyStrokeCommand.create(
            gateway.captureSource(),
            Stroke.create(canvas, path, StrokeEffect.Paint(RED_INDEX)).requiredValue(),
        )

    fun eraseCommand(
        gateway: CommandGateway,
        path: List<PixelPosition>,
    ): ApplyStrokeCommand =
        ApplyStrokeCommand.create(
            gateway.captureSource(),
            Stroke.create(canvas, path, StrokeEffect.Erase(DEFAULT_INDEX)).requiredValue(),
        )

    fun revision(value: Long): Revision = Revision.create(value).requiredValue()

    fun fullRegion(): PixelRegion = PixelRegion.create(canvas, position(0, 0), canvas).requiredValue()

    fun diagonalRegion(): PixelRegion {
        val edge = minOf(canvas.width.value, canvas.height.value)
        val size =
            CanvasSize.create(
                CanvasWidth.create(edge).requiredValue(),
                CanvasHeight.create(edge).requiredValue(),
            )
        return PixelRegion.create(canvas, position(0, 0), size).requiredValue()
    }

    private fun position(
        x: Int,
        y: Int,
    ): PixelPosition =
        PixelPosition.create(
            PixelX.create(x).requiredValue(),
            PixelY.create(y).requiredValue(),
        )

    private fun color(
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
    ): PixelColor =
        PixelColor.create(
            ColorChannel.create(red).requiredValue(),
            ColorChannel.create(green).requiredValue(),
            ColorChannel.create(blue).requiredValue(),
            ColorChannel.create(alpha).requiredValue(),
        )

    private companion object {
        const val DOCUMENT_ID: String = "33333333333333333333333333333333"
        const val CHANNEL_MIN: Int = 0
        const val CHANNEL_MAX: Int = 255
        val RED_INDEX: PaletteIndex = PaletteIndex.create(1).requiredValue()
        val DEFAULT_INDEX: PaletteIndex = PaletteIndex.create(2).requiredValue()
    }
}

private fun CommandResult.requireApplied(expected: P2ExpectedCommandTransition): P2CommandResultDescriptor =
    when (this) {
        is CommandResult.Applied -> {
            assertEquals(expected.beforeRevision, changeSet.beforeRevision.value)
            assertEquals(expected.afterRevision, changeSet.afterRevision.value)
            assertEquals(requireNotNull(expected.renderInvalidation), changeSet.renderInvalidation)
            P2CommandResultDescriptor(
                resultKind = "applied",
                changeSetBeforeRevision = changeSet.beforeRevision.value,
                changeSetAfterRevision = changeSet.afterRevision.value,
                renderInvalidation = changeSet.renderInvalidation.descriptor(),
            )
        }

        is CommandResult.Rejected -> {
            measurementFailure("Expected applied command but was rejected: $reason")
        }

        is CommandResult.Failed -> {
            measurementFailure("Expected applied command but failed: $failure")
        }
    }

private fun CommandResult.requireNoEffectiveChange(): P2CommandResultDescriptor =
    when (this) {
        is CommandResult.Rejected -> {
            assertEquals(RejectionReason.NoEffectiveChange, reason)
            P2CommandResultDescriptor(
                resultKind = "rejected_no_effective_change",
                changeSetBeforeRevision = null,
                changeSetAfterRevision = null,
                renderInvalidation = null,
            )
        }

        is CommandResult.Applied -> {
            measurementFailure("Expected no-op rejection but command was applied: $changeSet")
        }

        is CommandResult.Failed -> {
            measurementFailure("Expected no-op rejection but command failed: $failure")
        }
    }

private fun PixelRegion.descriptor(): P2CommandRegionDescriptor =
    P2CommandRegionDescriptor(
        originX = origin.x.value,
        originY = origin.y.value,
        width = size.width.value,
        height = size.height.value,
    )

private fun CanvasSize.fullRegion(): PixelRegion {
    val origin =
        PixelPosition.create(
            PixelX.create(0).requiredValue(),
            PixelY.create(0).requiredValue(),
        )
    return PixelRegion.create(this, origin, this).requiredValue()
}

private fun HistoryAvailability.csvName(): String =
    when (this) {
        HistoryAvailability.None -> "none"
        HistoryAvailability.UndoAvailable -> "undo_available"
        HistoryAvailability.RedoAvailable -> "redo_available"
        HistoryAvailability.UndoAndRedoAvailable -> "undo_and_redo_available"
    }

private fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> measurementFailure("Measurement fixture was rejected: $rejection")
    }

private fun measurementFailure(message: String): Nothing = throw AssertionError(message)
