package io.github.hideyukimori.nenepixel.core.domain.document

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.rejected
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class DocumentIdTest {
    @Test
    fun `exact lowercase hexadecimal identity is created with value equality`() {
        val first = created(DocumentId.create(FIRST_ID))
        val same = created(DocumentId.create(FIRST_ID))
        val different = created(DocumentId.create(SECOND_ID))

        assertEquals(FIRST_ID, first.value)
        assertEquals(first, same)
        assertNotEquals(first, different)
    }

    @Test
    fun `identity length outside exactly thirty two is rejected`() {
        val shortRejection = rejected(DocumentId.create(FIRST_ID.dropLast(1)))
        val longRejection = rejected(DocumentId.create(FIRST_ID + "0"))

        assertInstanceOf(DomainValueRejection.InvalidDocumentIdLength::class.java, shortRejection)
        assertInstanceOf(DomainValueRejection.InvalidDocumentIdLength::class.java, longRejection)
    }

    @Test
    fun `uppercase and non hexadecimal characters are rejected at their index`() {
        val uppercase =
            assertInstanceOf(
                DomainValueRejection.InvalidDocumentIdCharacter::class.java,
                rejected(DocumentId.create("A" + FIRST_ID.drop(1))),
            )
        val nonHexadecimal =
            assertInstanceOf(
                DomainValueRejection.InvalidDocumentIdCharacter::class.java,
                rejected(DocumentId.create(FIRST_ID.dropLast(1) + "g")),
            )

        assertEquals(0, uppercase.index)
        assertEquals('A', uppercase.character)
        assertEquals(FIRST_ID.lastIndex, nonHexadecimal.index)
        assertEquals('g', nonHexadecimal.character)
    }

    private companion object {
        const val FIRST_ID: String = "0123456789abcdef0123456789abcdef"
        const val SECOND_ID: String = "fedcba9876543210fedcba9876543210"
    }
}
