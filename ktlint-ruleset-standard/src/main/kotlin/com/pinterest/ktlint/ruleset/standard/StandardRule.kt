package com.pinterest.ktlint.ruleset.standard

import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleId

internal val STANDARD_RULE_ABOUT = Rule.About(
    maintainer = "KtLint",
    repositoryUrl = "https://github.com/pinterest/ktlint",
    issueTrackerUrl = "https://github.com/pinterest/ktlint/issues",
)

/**
 * Standard rules can only be declared and instantiated in the 'ktlint-ruleset-standard'. Custom rule set providers or API consumers have
 * to extend the [Rule] class to define a custom rule.
 */
public open class StandardRule internal constructor(
    id: String,
    override val visitorModifiers: Set<VisitorModifier> = emptySet(),
) : Rule(
    ruleId = RuleId("$STANDARD_RULE_SET_ID:$id"),
    visitorModifiers = visitorModifiers,
    about = STANDARD_RULE_ABOUT,
)
