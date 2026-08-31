package io.github.hideyukimori.nenepixel.core.domain

import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.fail

internal object DomainValueAssertions {
    fun <T> created(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Expected Created but was Rejected(${result.rejection}).")
        }

    fun rejected(result: DomainValueResult<*>): DomainValueRejection =
        when (result) {
            is DomainValueResult.Created -> fail("Expected Rejected but was Created(${result.value}).")
            is DomainValueResult.Rejected -> result.rejection
        }
}
