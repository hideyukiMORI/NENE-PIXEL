package io.github.hideyukimori.nenepixel.core.application.workspace.palette

import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryPayload
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryRetentionPolicy
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryRetentionResult
import io.github.hideyukimori.nenepixel.core.application.editor.RuntimeSourceToken
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteRemap

/**
 * The ephemeral palette draft and its draft-only history (ADR 0022). It owns no document state and no document history;
 * only an applied `ReplacePaletteCommand` changes the document palette.
 */
public class PaletteEditSession private constructor(
    internal val base: RuntimeSourceToken,
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
            PaletteDraftTransition.Changed(PaletteEditSession(base, timeline[cursor - 1].before, timeline, cursor - 1))
        } else {
            draftRejected(PaletteDraftRejection.NoUndoAvailable)
        }

    /** Moves the cursor forward one entry and restores that entry's `after`; the timeline is unchanged. */
    internal fun redo(): PaletteDraftTransition =
        if (cursor < timeline.size) {
            PaletteDraftTransition.Changed(PaletteEditSession(base, timeline[cursor].after, timeline, cursor + 1))
        } else {
            draftRejected(PaletteDraftRejection.NoRedoAvailable)
        }

    /** Appends after the cursor, then evicts oldest-first under the shared ADR 0022 retention budget. */
    private fun appended(remap: PaletteRemap): PaletteEditSession {
        val after = remap.target
        val entry = PaletteDraftEntry(draft, after, remap, PaletteDraftPayload.bytes(draft, after))
        val candidates = timeline.take(cursor) + entry
        val payloads = candidates.map { HistoryPayload(changeCount = 0, byteCount = it.payloadBytes) }
        val retained =
            when (val retention = HistoryRetentionPolicy.retain(payloads)) {
                is HistoryRetentionResult.Retained -> {
                    candidates.drop(retention.evictedEntryCount)
                }

                is HistoryRetentionResult.Rejected -> {
                    error("Palette draft entry cannot exceed the retained payload budget: ${retention.rejection}")
                }
            }
        return PaletteEditSession(base, after, retained, retained.size)
    }

    private fun draftRejected(reason: PaletteDraftRejection): PaletteDraftTransition =
        PaletteDraftTransition.Rejected(WorkspaceActionRejection.PaletteDraftRejected(reason))

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is PaletteEditSession &&
                    base == other.base &&
                    draft == other.draft &&
                    timeline == other.timeline &&
                    cursor == other.cursor
            )

    override fun hashCode(): Int =
        listOf(base, draft, timeline, cursor)
            .fold(INITIAL_HASH) { hash, value -> hash * HASH_MULTIPLIER + value.hashCode() }

    override fun toString(): String =
        "PaletteEditSession(base=$base, draft=$draft, timelineSize=${timeline.size}, cursor=$cursor)"

    internal companion object {
        private const val INITIAL_HASH: Int = 1
        private const val HASH_MULTIPLIER: Int = 31

        fun begin(
            base: RuntimeSourceToken,
            definition: PaletteDefinition,
        ): PaletteEditSession = PaletteEditSession(base, definition, emptyList(), 0)
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
