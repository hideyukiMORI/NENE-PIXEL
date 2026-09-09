package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatRejection

internal object ProjectStorageRejectionMapper {
    fun map(rejection: ProjectFormatRejection): ProjectStorageFailure =
        when (rejection) {
            is ProjectFormatRejection.ResourceLimitExceeded -> ProjectStorageFailure.ResourceLimitExceeded

            is ProjectFormatRejection.UnsupportedVersion -> ProjectStorageFailure.UnsupportedProjectVersion

            is ProjectFormatRejection.Truncated,
            ProjectFormatRejection.InvalidMagic,
            ProjectFormatRejection.InvalidCanvas,
            ProjectFormatRejection.InvalidRevision,
            is ProjectFormatRejection.TrailingData,
            is ProjectFormatRejection.ChecksumMismatch,
            -> ProjectStorageFailure.InvalidProject
        }
}
