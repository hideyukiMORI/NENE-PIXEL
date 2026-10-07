package io.github.hideyukimori.nenepixel.measurement

import java.io.File

/** Exercises actual pure slot/name guards, with no Android method or device measurement. */
internal object P4LayerFrameFixtureHostContract {
    @JvmStatic
    fun main(arguments: Array<String>) {
        require(arguments.size == 1)
        val directory = File(arguments.single())
        require(directory.isAbsolute && directory.mkdir())
        val rows = ArrayList<String>()
        for (sequence in 3..6) {
            val group = if (sequence < 5) "layers16" else "underlay"
            val role = if (sequence % 2 == 1) "baseline" else "candidate"
            val artifact = if (role == "baseline") "baseline_$group" else "candidate"
            val admission = admission("frame-$sequence-$group-$role-decision", artifact)
            val spec = P4LayerFrameFixtureSpec.from(admission)
            check(spec.sequence == sequence && spec.group == group)
            check(spec.fixture == if (group == "layers16") P4LayerFixture.MAXIMUM else P4LayerFixture.UNDERLAY)
            val extension = if (group == "layers16") "nenepixel" else "png"
            check(spec.name == "i89-145-${"a".repeat(12)}-frame-$sequence-$group.$extension")
            check(spec.reportDirectory == "p4-layer-frame-fixture-${"a".repeat(12)}-$sequence")
            rows.add("${admission.slotId},$artifact,${spec.name},${spec.fixture.asset},${spec.reportDirectory}")
            refuse(admission.copy(artifactRole = "baseline_single"))
            val swapped = if (role == "baseline") "candidate" else "baseline"
            refuse(admission.copy(slotId = admission.slotId.replace(role, swapped)))
            refuse(admission.copy(slotId = admission.slotId.replace("decision", "diagnostic")))
        }
        for (slot in listOf(
            "frame-1-single-baseline-decision",
            "frame-2-single-candidate-decision",
            "frame-03-layers16-baseline-decision",
            "frame-3-underlay-baseline-decision",
            "frame-5-layers16-baseline-decision",
            "frame-7-underlay-baseline-decision",
            "memory-layers16-candidate-1",
            "frame-4-layers16-candidate-decision\n",
        )) {
            refuse(admission(slot, "candidate"))
        }
        File(directory, "fixture-specs.csv").writeText(
            (listOf("slot_id,artifact_role,fixture_name,fixture_asset,report_directory") + rows)
                .joinToString("\n", postfix = "\n"),
        )
        File(directory, "result.txt").writeText("PASS: 4 valid slots and 20 direct refusals; no Android methods\n")
    }

    private fun refuse(admission: P4LayerRunAdmission) {
        check(runCatching { P4LayerFrameFixtureSpec.from(admission) }.isFailure)
    }

    private fun admission(
        slot: String,
        artifact: String,
    ): P4LayerRunAdmission =
        P4LayerRunAdmission(
            P4LayerRunAdmission.PROTOCOL,
            "host-contract",
            "a".repeat(64),
            "b".repeat(64),
            "host-contract",
            slot,
            artifact,
            "c".repeat(40),
            "d".repeat(40),
            "e".repeat(64),
            "f".repeat(64),
        )
}
