package com.pinterest.ktlint.ruleset.standard.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.RANGE
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.nextLeaf
import com.pinterest.ktlint.rule.engine.core.api.prevLeaf
import com.pinterest.ktlint.ruleset.standard.StandardRule
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace

public class SpacingAroundRangeOperatorRule : StandardRule("range-spacing") {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        if (node.elementType == RANGE) {
            val prevLeaf = node.prevLeaf()
            val nextLeaf = node.nextLeaf()
            when {
                prevLeaf is PsiWhiteSpace && nextLeaf is PsiWhiteSpace -> {
                    emit(node.startOffset, "Unexpected spacing around \"..\"", true)
                    if (autoCorrect) {
                        prevLeaf.node.treeParent.removeChild(prevLeaf.node)
                        nextLeaf.node.treeParent.removeChild(nextLeaf.node)
                    }
                }
                prevLeaf is PsiWhiteSpace -> {
                    emit(prevLeaf.node.startOffset, "Unexpected spacing before \"..\"", true)
                    if (autoCorrect) {
                        prevLeaf.node.treeParent.removeChild(prevLeaf.node)
                    }
                }
                nextLeaf is PsiWhiteSpace -> {
                    emit(nextLeaf.node.startOffset, "Unexpected spacing after \"..\"", true)
                    if (autoCorrect) {
                        nextLeaf.node.treeParent.removeChild(nextLeaf.node)
                    }
                }
            }
        }
    }
}

public val SPACING_AROUND_RANGE_OPERATOR_RULE_ID: RuleId = SpacingAroundRangeOperatorRule().ruleId
