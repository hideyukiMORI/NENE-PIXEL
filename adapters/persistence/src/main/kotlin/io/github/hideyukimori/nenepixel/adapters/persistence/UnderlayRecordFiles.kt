package io.github.hideyukimori.nenepixel.adapters.persistence

/**
 * The files of one directory, addressed by name, that hold the underlay memory records (ADR 0034).
 * Only [names], [read], [write] and [delete] may throw, and only [java.io.IOException].
 */
internal interface UnderlayRecordFiles {
    /** The names of the files in the directory, in no particular order. */
    fun names(): List<String>

    /**
     * Reads at most `maxLength + 1` bytes of [name]; a longer file answers exactly that many bytes, so the
     * caller rejects it by its length. Null only when the file does not exist.
     */
    fun read(
        name: String,
        maxLength: Int,
    ): ByteArray?

    /** Replaces [name] with all of [bytes], or leaves its previous content when it throws. */
    fun write(
        name: String,
        bytes: ByteArray,
    )

    /** Deletes [name]; a missing file is not an error. */
    fun delete(name: String)

    /** The length of [name] in bytes. */
    fun length(name: String): Long

    /** When [name] was last used, in a unit the implementation chooses; only compared. */
    fun usedAt(name: String): Long

    /** Marks [name] as used now; a failure is ignored and never thrown. */
    fun markUsed(name: String)
}
