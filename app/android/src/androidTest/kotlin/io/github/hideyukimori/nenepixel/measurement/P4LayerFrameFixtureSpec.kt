package io.github.hideyukimori.nenepixel.measurement

/** Local setup guard; the host still verifies the complete canonical phase slot and artifact catalog. */
internal data class P4LayerFrameFixtureSpec(
    val sequence: Int,
    val group: String,
    val fixture: P4LayerFixture,
    val name: String,
    val reportDirectory: String,
) {
    companion object {
        fun from(admission: P4LayerRunAdmission): P4LayerFrameFixtureSpec {
            val match =
                checkNotNull(
                    Regex("frame-([5-9]|1[0-2])-(layers16|underlay)-(baseline|candidate)-(decision|diagnostic)")
                        .matchEntire(admission.slotId),
                )
            val (number, group, role, runner) = match.destructured
            val sequence = number.toInt()
            val position = sequence - if (group == "layers16") 5 else 9
            check(position in 0..3)
            check(role == if (position % 2 == 0) "baseline" else "candidate")
            check(runner == if (position < 2) "decision" else "diagnostic")
            check(admission.artifactRole == if (role == "baseline") "baseline_$group" else "candidate")
            val fixture = if (group == "layers16") P4LayerFixture.MAXIMUM else P4LayerFixture.UNDERLAY
            val extension = if (group == "layers16") "nenepixel" else "png"
            val prefix = admission.preflightSha256.take(12)
            return P4LayerFrameFixtureSpec(
                sequence,
                group,
                fixture,
                "i89-145-$prefix-frame-$sequence-$group.$extension",
                "p4-layer-frame-fixture-$prefix-$sequence",
            )
        }
    }
}
