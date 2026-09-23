package io.github.hideyukimori.nenepixel.core.application.workspace.palette

import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryPayload
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryRetentionPolicy
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryRetentionResult
import io.github.hideyukimori.nenepixel.core.application.editor.RuntimeSourceToken
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteRemap
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/**
 * The ephemeral palette draft and its draft-only history (ADR 0022). It owns no document state and no document history;
 * only an applied `ReplacePaletteCommand` changes the document palette.
 *
 * `origin` is the definition the session began from and never changes. `evicted` is the accumulated remap from
 * `origin` to the `before` of the oldest retained entry (to the draft when the timeline is empty); it absorbs every
 * entry the retention budget drops, so the composed remap always starts at `origin`.
 */
public class PaletteEditSession private constructor(
    internal val base: RuntimeSourceToken,
    internal val origin: PaletteDefinition,
    internal val evicted: PaletteRemap,
    public val draft: PaletteDefinition,
    internal val timeline: List<PaletteDraftEntry>,
    internal val cursor: Int,
) {
    /** Plans one draft operation; a change appends one entry after the cursor and discards the redo branch. */
    internal fun edit(operation: PaletteDraftOperation): PaletteDraftTransition =
        when (val plan = PaletteDraftPlanner.plan(draft, operation)) {
            is PaletteDraftPlan.Planned -> PaletteDraftTransition.Changed(appended(plan.remap))
            PaletteDraftPlan.Unchanged -> PaletteDraftTransition.Unchanged
            is PaletteDraftPlan.Rejected -> PaletteDraftTransition.Rejected(plan.rejection)
        }

    /** Moves the cursor back one entry and restores that entry's `before`; the timeline is unchanged. */
    internal fun undo(): PaletteDraftTransition =
        if (cursor > 0) {
            PaletteDraftTransition.Changed(moved(timeline[cursor - 1].before, cursor - 1))
        } else {
            draftRejected(PaletteDraftRejection.NoUndoAvailable)
        }

    /** Moves the cursor forward one entry and restores that entry's `after`; the timeline is unchanged. */
    internal fun redo(): PaletteDraftTransition =
        if (cursor < timeline.size) {
            PaletteDraftTransition.Changed(moved(timeline[cursor].after, cursor + 1))
        } else {
            draftRejected(PaletteDraftRejection.NoRedoAvailable)
        }

    /**
     * The single remap from `origin` to the current draft: `evicted` followed by every retained entry before the
     * cursor, in order. Its source is always `origin`.
     */
    internal fun composedRemap(): PaletteRemap = compose(evicted, timeline.take(cursor).map { it.remap }, draft)

    /** True when applying the draft would change nothing: the draft equals `origin` and every index maps to itself. */
    internal fun isIdentity(): Boolean {
        val unchanged = origin.palette.entries().map { it.index }
        return draft == origin && composedRemap().destinations() == unchanged
    }

    /**
     * Appends after the cursor, then evicts oldest-first under the shared ADR 0022 retention budget; the remap of
     * every evicted entry is folded into `evicted`.
     */
    private fun appended(remap: PaletteRemap): PaletteEditSession {
        val after = remap.target
        val entry = PaletteDraftEntry(draft, after, remap, PaletteDraftPayload.bytes(draft, after))
        val candidates = timeline.take(cursor) + entry
        val evictedCount = evictedEntryCount(candidates)
        val retained = candidates.drop(evictedCount)
        val folded =
            if (evictedCount > 0) {
                val oldestBefore = retained.firstOrNull()?.before ?: after
                compose(evicted, candidates.take(evictedCount).map { it.remap }, oldestBefore)
            } else {
                evicted
            }
        return PaletteEditSession(base, origin, folded, after, retained, retained.size)
    }

    private fun moved(
        restored: PaletteDefinition,
        movedCursor: Int,
    ): PaletteEditSession = PaletteEditSession(base, origin, evicted, restored, timeline, movedCursor)

    private fun draftRejected(reason: PaletteDraftRejection): PaletteDraftTransition =
        PaletteDraftTransition.Rejected(WorkspaceActionRejection.PaletteDraftRejected(reason))

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is PaletteEditSession &&
                    base == other.base &&
                    origin == other.origin &&
                    evicted == other.evicted &&
                    draft == other.draft &&
                    timeline == other.timeline &&
                    cursor == other.cursor
            )

    override fun hashCode(): Int =
        listOf(base, origin, evicted, draft, timeline, cursor)
            .fold(INITIAL_HASH) { hash, value -> hash * HASH_MULTIPLIER + value.hashCode() }

    override fun toString(): String =
        "PaletteEditSession(base=$base, origin=$origin, evicted=$evicted, draft=$draft, " +
            "timelineSize=${timeline.size}, cursor=$cursor)"

    internal companion object {
        private const val INITIAL_HASH: Int = 1
        private const val HASH_MULTIPLIER: Int = 31

        fun begin(
            base: RuntimeSourceToken,
            definition: PaletteDefinition,
        ): PaletteEditSession {
            val identity = remapOf(definition, definition, definition.palette.entries().map { it.index })
            return PaletteEditSession(base, definition, identity, definition, emptyList(), 0)
        }

        private fun evictedEntryCount(candidates: List<PaletteDraftEntry>): Int {
            val payloads = candidates.map { HistoryPayload(changeCount = 0, byteCount = it.payloadBytes) }
            return when (val retention = HistoryRetentionPolicy.retain(payloads)) {
                is HistoryRetentionResult.Retained -> {
                    retention.evictedEntryCount
                }

                is HistoryRetentionResult.Rejected -> {
                    error("Palette draft entry cannot exceed the retained payload budget: ${retention.rejection}")
                }
            }
        }

        /**
         * The one draft remap composition: sends each destination of `base` through every step in order, producing a
         * single remap from `base.source` to `target`.
         */
        private fun compose(
            base: PaletteRemap,
            steps: List<PaletteRemap>,
            target: PaletteDefinition,
        ): PaletteRemap {
            val destinations = base.destinations().map { start -> steps.fold(start, ::stepped) }
            return remapOf(base.source, target, destinations)
        }

        private fun stepped(
            index: PaletteIndex,
            step: PaletteRemap,
        ): PaletteIndex =
            when (val destination = step.destinationAt(index)) {
                is DomainValueResult.Created -> destination.value
                is DomainValueResult.Rejected -> error("Draft remap chain is broken: ${destination.rejection}")
            }

        private fun remapOf(
            source: PaletteDefinition,
            target: PaletteDefinition,
            destinations: List<PaletteIndex>,
        ): PaletteRemap =
            when (val remap = PaletteRemap.create(source, target, destinations)) {
                is DomainValueResult.Created -> remap.value
                is DomainValueResult.Rejected -> error("Draft remap is invalid: ${remap.rejection}")
            }
    }
}

/** One draft-history step; it owns the before/after definitions and their remap, never document history. */
internal data class PaletteDraftEntry(
    val before: PaletteDefinition,
    val after: PaletteDefinition,
    val remap: PaletteRemap,
    val payloadBytes: Long,
)

/** The outcome of one draft transition; `Rejected` and `Unchanged` leave the session reference untouched. */
internal sealed interface PaletteDraftTransition {
    data class Changed(
        val session: PaletteEditSession,
    ) : PaletteDraftTransition

    data object Unchanged : PaletteDraftTransition

    data class Rejected(
        val rejection: WorkspaceActionRejection,
    ) : PaletteDraftTransition
}
