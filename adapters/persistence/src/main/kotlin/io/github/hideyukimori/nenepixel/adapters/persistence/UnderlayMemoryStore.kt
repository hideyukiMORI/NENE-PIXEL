package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayRecollection
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId

/**
 * The decisions of the underlay memory (ADR 0034) over [files]: which records to read, write and delete.
 * Synchronous and unsynchronized; the caller serializes the calls. An [java.io.IOException] of [files] is
 * not caught here. An unreadable pair is not an exception: it is deleted and answered as `Absent`.
 */
internal class UnderlayMemoryStore(
    private val files: UnderlayRecordFiles,
) {
    private val housekeeping = UnderlayMemoryHousekeeping(files)

    /** The one image instance this store last wrote or recalled, for one work, with its CRC-32. */
    private var recentImage: RecentImage? = null

    fun recall(document: DocumentId): UnderlayRecollection {
        val stateBytes = files.read(stateName(document), UnderlayMemoryLayout.STATE_BYTE_COUNT)
        val imageBytes = files.read(imageName(document), UnderlayMemoryLayout.IMAGE_MAX_BYTE_COUNT)
        return when {
            stateBytes == null && imageBytes == null -> absent(document)
            stateBytes == null || imageBytes == null -> discard(document)
            else -> recallPair(document, stateBytes, imageBytes)
        }
    }

    fun remember(
        document: DocumentId,
        underlay: RememberedUnderlay,
    ) {
        val imageChecksum = rememberImage(document, underlay.image)
        val stateBytes = UnderlayStateRecord.encode(imageChecksum, underlay)
        val storedState = files.read(stateName(document), UnderlayMemoryLayout.STATE_BYTE_COUNT)
        if (storedState == null || !storedState.contentEquals(stateBytes)) {
            files.write(stateName(document), stateBytes)
        }
        files.markUsed(stateName(document))
        housekeeping.tidy(document.value)
    }

    fun forget(document: DocumentId) {
        discard(document)
    }

    private fun recallPair(
        document: DocumentId,
        stateBytes: ByteArray,
        imageBytes: ByteArray,
    ): UnderlayRecollection {
        val state = UnderlayStateRecord.decode(stateBytes)
        val image = UnderlayImageRecord.decode(imageBytes)
        val joined =
            if (state is UnderlayRecordDecodeResult.Decoded && image is UnderlayRecordDecodeResult.Decoded) {
                state.value.withImage(image.value, UnderlayImageRecord.checksum(imageBytes))
            } else {
                UnderlayRecordDecodeResult.Unreadable
            }
        return when (joined) {
            is UnderlayRecordDecodeResult.Decoded -> recalled(document, joined.value, imageBytes)
            UnderlayRecordDecodeResult.Unreadable -> discard(document)
        }
    }

    private fun recalled(
        document: DocumentId,
        underlay: RememberedUnderlay,
        imageBytes: ByteArray,
    ): UnderlayRecollection {
        recentImage = RecentImage(document, underlay.image, UnderlayImageRecord.checksum(imageBytes))
        files.markUsed(stateName(document))
        return UnderlayRecollection.Remembered(underlay)
    }

    /** Writes the image record unless the stored one already has this image; answers its CRC-32. */
    private fun rememberImage(
        document: DocumentId,
        image: ReferenceImage,
    ): Int {
        val recent = recentImage
        if (recent != null && recent.isOf(document, image)) {
            return recent.checksum
        }
        val record = UnderlayImageRecord.encode(image)
        val checksum = UnderlayImageRecord.checksum(record)
        val stored = files.read(imageName(document), UnderlayMemoryLayout.IMAGE_MAX_BYTE_COUNT)
        if (stored == null || stored.size != record.size || UnderlayImageRecord.checksum(stored) != checksum) {
            files.write(imageName(document), record)
        }
        recentImage = RecentImage(document, image, checksum)
        return checksum
    }

    private fun discard(document: DocumentId): UnderlayRecollection {
        absent(document)
        files.delete(imageName(document))
        files.delete(stateName(document))
        return UnderlayRecollection.Absent
    }

    /** Drops the recent image of [document]: its files are gone or about to be. */
    private fun absent(document: DocumentId): UnderlayRecollection {
        if (recentImage?.document == document) {
            recentImage = null
        }
        return UnderlayRecollection.Absent
    }

    private fun imageName(document: DocumentId): String = UnderlayRecordNames.image(document.value)

    private fun stateName(document: DocumentId): String = UnderlayRecordNames.state(document.value)

    private class RecentImage(
        val document: DocumentId,
        val image: ReferenceImage,
        val checksum: Int,
    ) {
        fun isOf(
            document: DocumentId,
            image: ReferenceImage,
        ): Boolean = this.document == document && this.image === image
    }
}
