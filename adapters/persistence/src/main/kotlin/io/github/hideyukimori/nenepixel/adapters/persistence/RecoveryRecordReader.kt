package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspectionFailure
import kotlinx.coroutines.CancellationException
import java.io.FileNotFoundException
import java.io.InputStream

internal class RecoveryRecordReader(
    private val file: RecoveryAtomicFileAccess,
) {
    fun inspect(): InternalInspection =
        when (val raw = readRaw()) {
            is RawRecordRead.Bytes -> {
                mapDecoded(RecoveryRecordCodec.decode(raw.value))
            }

            RawRecordRead.Missing -> {
                InternalInspection.Missing
            }

            RawRecordRead.ResourceLimitExceeded -> {
                InternalInspection.Failed(RecoveryInspectionFailure.RESOURCE_LIMIT_EXCEEDED)
            }

            RawRecordRead.ReadFailed -> {
                InternalInspection.Failed(RecoveryInspectionFailure.READ_FAILED)
            }

            RawRecordRead.CloseFailed -> {
                InternalInspection.Failed(RecoveryInspectionFailure.CLOSE_FAILED)
            }
        }

    fun readRaw(): RawRecordRead =
        when (val opened = openRecord()) {
            is RecordOpen.Opened -> readOpened(opened.input)
            RecordOpen.Missing -> RawRecordRead.Missing
            RecordOpen.Failed -> RawRecordRead.ReadFailed
        }

    private fun readOpened(input: InputStream): RawRecordRead {
        val attempt = read(input)
        val closeFailed = AtomicAccessResult.of { input.close() } is AtomicAccessResult.Failed
        return when (attempt) {
            is RecoveryReadAttempt.Cancelled -> {
                throw attempt.cancellation
            }

            RecoveryReadAttempt.Failed -> {
                RawRecordRead.ReadFailed
            }

            is RecoveryReadAttempt.Completed -> {
                if (closeFailed) RawRecordRead.CloseFailed else mapBoundedRead(attempt.result)
            }
        }
    }

    private fun openRecord(): RecordOpen =
        try {
            RecordOpen.Opened(file.openRead())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: FileNotFoundException) {
            // A failed open is the only absent-record signal AtomicFile exposes.
            RecordOpen.Missing
        } catch (_: Exception) {
            // AtomicFile and filesystem exceptions are normalized only at this adapter boundary.
            RecordOpen.Failed
        }

    private fun read(input: InputStream): RecoveryReadAttempt =
        try {
            RecoveryReadAttempt.Completed(
                BoundedStreamReader.read(input, null, RecoveryRecordCodec.MAX_RECORD_BYTE_COUNT),
            )
        } catch (cancelled: CancellationException) {
            RecoveryReadAttempt.Cancelled(cancelled)
        } catch (_: Exception) {
            // AtomicFile and filesystem exceptions are normalized only at this adapter boundary.
            RecoveryReadAttempt.Failed
        }

    private fun mapBoundedRead(result: BoundedReadResult): RawRecordRead =
        when (result) {
            is BoundedReadResult.Bytes -> RawRecordRead.Bytes(result.value)

            BoundedReadResult.ResourceLimitExceeded -> RawRecordRead.ResourceLimitExceeded

            BoundedReadResult.ZeroProgress,
            BoundedReadResult.PrematureEnd,
            -> RawRecordRead.ReadFailed
        }

    private fun mapDecoded(result: RecoveryDecodeResult): InternalInspection =
        when (result) {
            is RecoveryDecodeResult.Accepted -> {
                InternalInspection.Record(result.record)
            }

            is RecoveryDecodeResult.Rejected -> {
                InternalInspection.Failed(mapRejection(result.rejection))
            }
        }

    private fun mapRejection(rejection: RecoveryRejection): RecoveryInspectionFailure =
        when (rejection) {
            RecoveryRejection.RESOURCE_LIMIT_EXCEEDED -> RecoveryInspectionFailure.RESOURCE_LIMIT_EXCEEDED
            RecoveryRejection.UNSUPPORTED_VERSION -> RecoveryInspectionFailure.UNSUPPORTED_VERSION
            RecoveryRejection.CORRUPT -> RecoveryInspectionFailure.CORRUPT
        }
}

internal sealed interface InternalInspection {
    data object Missing : InternalInspection

    data class Record(
        val record: RecoveryRecord,
    ) : InternalInspection

    data class Failed(
        val failure: RecoveryInspectionFailure,
    ) : InternalInspection
}

internal sealed interface RawRecordRead {
    data class Bytes(
        val value: ByteArray,
    ) : RawRecordRead

    data object Missing : RawRecordRead

    data object ResourceLimitExceeded : RawRecordRead

    data object ReadFailed : RawRecordRead

    data object CloseFailed : RawRecordRead
}

private sealed interface RecordOpen {
    data class Opened(
        val input: InputStream,
    ) : RecordOpen

    data object Missing : RecordOpen

    data object Failed : RecordOpen
}

private sealed interface RecoveryReadAttempt {
    data class Completed(
        val result: BoundedReadResult,
    ) : RecoveryReadAttempt

    data class Cancelled(
        val cancellation: CancellationException,
    ) : RecoveryReadAttempt

    data object Failed : RecoveryReadAttempt
}
