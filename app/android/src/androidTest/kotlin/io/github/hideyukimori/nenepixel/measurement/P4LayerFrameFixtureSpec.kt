package io.github.hideyukimori.nenepixel.measurement

/**
 * Local setup guard; the host still verifies the complete canonical phase slot and artifact catalog.
 *
 * Issue #145 R1 stages decision slots only: `layers16` is frame-3 baseline and frame-4 candidate,
 * `underlay` is frame-5 baseline and frame-6 candidate. The single slots frame-1 and frame-2 are not staged.
 */
internal data class P4LayerFrameFixtureSpec(
    val slot: P4LayerFrameFixtureSlot,
    val output: P4LayerFrameFixtureOutput,
) {
    val sequence: Int get() = slot.sequence
    val group: String get() = slot.group
    val fixture: P4LayerFixture get() = slot.fixture
    val name: String get() = output.name
    val reportDirectory: String get() = output.reportDirectory

    companion object {
        fun from(admission: P4LayerRunAdmission): P4LayerFrameFixtureSpec {
            val match =
                checkNotNull(
                    Regex("frame-([3-6])-(layers16|underlay)-(baseline|candidate)-decision")
                        .matchEntire(admission.slotId),
                )
            val (number, group, role) = match.destructured
            val sequence = number.toInt()
            val position = sequence - if (group == "layers16") 3 else 5
            check(position in 0..1)
            check(role == if (position == 0) "baseline" else "candidate")
            check(admission.artifactRole == if (role == "baseline") "baseline_$group" else "candidate")
            val fixture = if (group == "layers16") P4LayerFixture.MAXIMUM else P4LayerFixture.UNDERLAY
            val extension = if (group == "layers16") "nenepixel" else "png"
            val prefix = admission.preflightSha256.take(12)
            return P4LayerFrameFixtureSpec(
                P4LayerFrameFixtureSlot(sequence, group, fixture),
                P4LayerFrameFixtureOutput(
                    "i89-145-$prefix-frame-$sequence-$group.$extension",
                    "p4-layer-frame-fixture-$prefix-$sequence",
                ),
            )
        }
    }
}

internal data class P4LayerFrameFixtureSlot(
    val sequence: Int,
    val group: String,
    val fixture: P4LayerFixture,
)

internal data class P4LayerFrameFixtureOutput(
    val name: String,
    val reportDirectory: String,
)
