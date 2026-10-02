package io.github.hideyukimori.nenepixel.adapters.persistence

/** [UnderlayRecordFiles] whose every throwing operation throws what [failure] creates. */
internal class ThrowingUnderlayRecordFiles(
    private val failure: () -> Exception,
) : UnderlayRecordFiles {
    override fun names(): List<String> = throw failure()

    override fun read(
        name: String,
        maxLength: Int,
    ): ByteArray? = throw failure()

    override fun write(
        name: String,
        bytes: ByteArray,
    ): Unit = throw failure()

    override fun delete(name: String): Unit = throw failure()

    override fun length(name: String): Long = 0L

    override fun usedAt(name: String): Long = 0L

    override fun markUsed(name: String) {
        // Never throws, as the interface requires.
    }
}
