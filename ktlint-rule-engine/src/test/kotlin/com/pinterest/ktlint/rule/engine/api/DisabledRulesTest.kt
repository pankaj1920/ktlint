package com.pinterest.ktlint.rule.engine.api

import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.RuleExecution
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.createRuleExecutionEditorConfigProperty
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DisabledRulesTest {
    @Test
    fun `Given some code and a enabled standard rule resulting in a violation then the violation is reported`() {
        assertThat(
            ArrayList<LintError>().apply {
                KtLintRuleEngine(
                    ruleProviders = setOf(
                        RuleProvider { NoVarRule("test:no-var") },
                    ),
                ).lint("var foo") { e -> add(e) }
            },
        ).contains(
            LintError(1, 1, "test:no-var", NoVarRule.SOME_NO_VAR_RULE_VIOLATION, false),
        )
    }

    @ParameterizedTest(name = "RuleId: {0}, Disabled ruleId: {1}")
    @CsvSource(
        value = [
            "standard:no-var,standard:no-var",
            "custom:no-var,custom:no-var",
        ],
    )
    fun `Given a rule that is disabled via property 'ktlint_some-rule-id' and some code then no violation is reported`(
        ruleId: String,
        disabledRuleId: String,
    ) {
        assertThat(
            ArrayList<LintError>().apply {
                KtLintRuleEngine(
                    ruleProviders = setOf(
                        RuleProvider { NoVarRule(ruleId) },
                    ),
                    editorConfigOverride = EditorConfigOverride.from(
                        RuleId(disabledRuleId).createRuleExecutionEditorConfigProperty() to RuleExecution.disabled,
                    ),
                ).lint("var foo") { e -> add(e) }
            },
        ).isEmpty()
    }

    class NoVarRule(id: String) : Rule(
        ruleId = RuleId(id),
        about = About(),
    ) {
        override fun beforeVisitChildNodes(
            node: ASTNode,
            autoCorrect: Boolean,
            emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
        ) {
            emit(node.startOffset, SOME_NO_VAR_RULE_VIOLATION, false)
        }

        companion object {
            const val SOME_NO_VAR_RULE_VIOLATION = "some-no-var-rule-violation"
        }
    }
}
