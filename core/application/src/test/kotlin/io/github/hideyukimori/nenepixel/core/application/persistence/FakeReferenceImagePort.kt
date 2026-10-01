package io.github.hideyukimori.nenepixel.core.application.persistence

internal class FakeReferenceImagePort : ReferenceImagePort {
    var calls: Int = 0
        private set
    var handler: suspend () -> ReferenceImageOutcome = { ReferenceImageOutcome.Cancelled }

    override suspend fun pick(): ReferenceImageOutcome {
        calls += 1
        return handler()
    }
}
