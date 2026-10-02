package io.github.hideyukimori.nenepixel.adapters.persistence

/** The file names of a work's two underlay memory records: the 32 lower-case hexadecimal id and an extension. */
internal object UnderlayRecordNames {
    private val IMAGE_NAME = Regex("[0-9a-f]{32}" + Regex.escape(UnderlayMemoryLayout.IMAGE_FILE_EXTENSION))

    fun image(id: String): String = id + UnderlayMemoryLayout.IMAGE_FILE_EXTENSION

    fun state(id: String): String = id + UnderlayMemoryLayout.STATE_FILE_EXTENSION

    /** The ids that have both an image record and a state record among [names]. */
    fun completePairIds(names: Collection<String>): Set<String> {
        val present = names.toSet()
        return present
            .filter { name -> IMAGE_NAME.matches(name) }
            .map { name -> name.removeSuffix(UnderlayMemoryLayout.IMAGE_FILE_EXTENSION) }
            .filter { id -> state(id) in present }
            .toSet()
    }
}
