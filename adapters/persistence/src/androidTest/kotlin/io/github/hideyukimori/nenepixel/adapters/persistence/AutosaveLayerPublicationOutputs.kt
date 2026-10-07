package io.github.hideyukimori.nenepixel.adapters.persistence

import java.io.File

/** Failed reservations intentionally retain every entry already created. */
internal object AutosaveLayerPublicationOutputs {
    fun reserve(
        directory: File,
        identityLine: String,
    ): AutosavePublicationEvidenceOutputReservation {
        check(directory.mkdir()) { "Layer publication report directory already exists or cannot be created" }
        val csv = File(directory, "publication.csv")
        val status = File(directory, "publication.status")
        val projection = File(directory, "identity.txt")
        check(csv.createNewFile() && status.createNewFile() && projection.createNewFile())
        status.writeText("invalid")
        projection.writeText("$identityLine\n")
        return AutosavePublicationEvidenceOutputReservation(csv, status)
    }
}
