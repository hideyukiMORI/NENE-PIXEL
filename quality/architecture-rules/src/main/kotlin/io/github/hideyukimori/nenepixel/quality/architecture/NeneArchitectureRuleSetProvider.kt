package io.github.hideyukimori.nenepixel.quality.architecture

import dev.detekt.api.RuleName
import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

public class NeneArchitectureRuleSetProvider : RuleSetProvider {
    override val ruleSetId: RuleSetId = RuleSetId("nene-architecture")

    override fun instance(): RuleSet =
        RuleSet(
            ruleSetId,
            mapOf(RuleName("ForbiddenGenericName") to ::ForbiddenGenericName),
        )
}
