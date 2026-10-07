package io.github.hideyukimori.nenepixel.measurement

import java.io.File

/** Only the fresh reserved directory is written; every incomplete artifact remains available. */
internal class P4LayerSafOutput private constructor(
    private val directory: File,
) {
    private val csv = File(directory, "save.csv")
    private val status = File(directory, "save.status")
    private val identity = File(directory, "identity.txt")
    private val setup = File(directory, "setup.csv")
    private var setupCount = 0
    private var identityAttempted = false
    private var reportAttempted = false

    @Synchronized
    fun recordSetup(
        kind: String,
        index: Int,
        destination: P4LayerSafDestination,
    ) {
        check(!identityAttempted && !reportAttempted && setupCount < 26)
        val expectedKind =
            when (setupCount) {
                0 -> "source"
                in 1..5 -> "warmup"
                else -> "sample"
            }
        val expectedIndex =
            when (setupCount) {
                0 -> 0
                in 1..5 -> setupCount - 1
                else -> setupCount - 6
            }
        check(kind == expectedKind && index == expectedIndex)
        check(destination.uri.none { it == ',' || it == '\r' || it == '\n' })
        val bytes = if (kind == "source") 1_182_862 else 0
        setup.appendText(
            "$kind,$index,${destination.uri},${destination.granteeUid},${destination.providerUid},$bytes\n",
        )
        setupCount++
    }

    @Synchronized
    fun publishIdentity(line: String) {
        check(setupCount == 26 && !identityAttempted && !reportAttempted)
        identityAttempted = true
        identity.writeText("$line\n")
    }

    @Synchronized
    fun report(
        rows: List<String>,
        complete: Boolean,
    ) {
        check(!reportAttempted)
        check(!complete || (identityAttempted && setupCount == 26 && rows.size == 27))
        reportAttempted = true
        csv.writeText((listOf(P4LayerSafSample.HEADER) + rows).joinToString("\n", postfix = "\n"))
        status.writeText(if (complete) "complete" else "invalid")
    }

    companion object {
        fun reserve(
            filesDirectory: File,
            prefix: String,
        ): P4LayerSafOutput {
            require(prefix.matches(Regex("p4-layer-saf-[0-9a-f]{12}")))
            val directory = File(filesDirectory, prefix)
            check(directory.mkdir()) { "SAF report directory exists or cannot be created" }
            for (name in listOf("save.csv", "save.status", "identity.txt", "setup.csv")) {
                check(File(directory, name).createNewFile())
            }
            File(directory, "save.status").writeText("invalid")
            File(directory, "setup.csv").writeText("kind,index,destination_uri,grantee_uid,provider_uid,byte_count\n")
            return P4LayerSafOutput(directory)
        }
    }
}
