package com.pinterest.ktlint.rule.engine.core.api

import com.pinterest.ktlint.rule.engine.core.internal.IdNamingPolicy
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

@JvmInline
public value class RuleId(public val value: String) {
    init {
        IdNamingPolicy.enforceRuleIdNaming(value)
    }

    public val ruleSetId: RuleSetId
        get() = RuleSetId(value.substringBefore(DELIMITER, ""))

    public companion object {
        private const val STANDARD_RULE_SET_ID = "standard"
        private const val DELIMITER = ":"

        // TODO: Remove in future version when backward compatibility of rule id references can be dropped.
        public fun prefixWithStandardRuleSetIdWhenMissing(id: String): String =
            if (id.contains(DELIMITER)) {
                id
            } else {
                "$STANDARD_RULE_SET_ID$DELIMITER$id"
            }
    }
}

@JvmInline
public value class RuleSetId(public val value: String) {
    init {
        IdNamingPolicy.enforceRuleSetIdNaming(value)
    }
}

/**
 * The [Rule] contains the life cycle hooks which are called by the KtLint rule engine to execute the rule.
 *
 * The implementation of a [Rule] **doesn't** have to be thread-safe or stateless provided that the [RuleProvider] creates a new instance of
 * [Rule] on each call to [RuleProvider.createNewRuleInstance]. The KtLint Rule Engine never re-uses a [Rule] instance once is has been used
 * for traversal of the AST of a file.
 *
 * When wrapping a rule from the ktlint project and modifying its behavior, please change the [ruleId] and [about] fields, so that it is
 * clear to users whenever they used the original rule provided by KtLint versus a modified version which is not maintained by the KtLint
 * project.
 */
public open class Rule(
    /**
     * Identification of the rule. A [ruleId] has a value that must adhere the convention "<rule-set-id>:<rule-id>". The rule set id
     * 'standard' is reserved for rules which are maintained by the KtLint project. Rules created by custom rule set providers and API
     * Consumers should use a prefix other than 'standard' to mark the origin of rules which are not maintained by the KtLint project.
     */
    public open val ruleId: RuleId,

    /**
     * About the rule. Background information about the rule and its maintainer. About information is meant to be used in stack traces or
     * API consumers to provide more detailed information about the rule.
     */
    public open val about: About,

    /**
     * Set of modifiers of the visitor. Preferably a rule has no modifiers at all, meaning that it is completely
     * independent of all other rules.
     */
    public open val visitorModifiers: Set<VisitorModifier> = emptySet(),
) {
    private var traversalState = TraversalState.NOT_STARTED

    /**
     * This method is called once before the first node is visited. It can be used to initialize the state of the rule
     * before processing of nodes starts.
     */
    public open fun beforeFirstNode(editorConfigProperties: EditorConfigProperties) {}

    /**
     * This method is called on a node in AST before visiting the child nodes. This is repeated recursively for the
     * child nodes resulting in a depth first traversal of the AST.
     *
     * @param node AST node
     * @param autoCorrect indicates whether rule should attempt autocorrection
     * @param emit a way for rule to notify about a violation (lint error)
     */
    public open fun beforeVisitChildNodes(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {}

    /**
     * This method is called on a node in AST after all its child nodes have been visited.
     */
    @Suppress("unused")
    public open fun afterVisitChildNodes(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {}

    /**
     * This method is called once after the last node in the AST is visited. It can be used for teardown of the state
     * of the rule.
     */
    public open fun afterLastNode() {}

    /**
     * Checks whether the [Rule] instance is used for traversal of the AST and as of that potentially has changed the state of the [Rule]
     * provided that it has state.
     */
    public fun isUsedForTraversalOfAST(): Boolean = traversalState != TraversalState.NOT_STARTED

    /**
     * Marks the [Rule] instance as being used for traversal of an AST. From this moment on, this instance of the [Rule]
     * can not be used to start a new traversal of the same or another AST as the instance might contain state.
     */
    public fun startTraversalOfAST() {
        require(traversalState == TraversalState.NOT_STARTED)
        traversalState = TraversalState.CONTINUE
    }

    /**
     * Checks whether the next node in the AST is to be traversed. By default, the entire AST is traversed.
     */
    public fun shouldContinueTraversalOfAST(): Boolean = traversalState == TraversalState.CONTINUE

    /**
     * Stops traversal of the AST. Intended usage it to prevent parsing of the remainder of the AST once the goal of the
     * rule is achieved. For example, if the ".editorconfig" property indent_size is set to 0 or -1 then the indent rule
     * should be disabled.
     *
     * When called in [beforeFirstNode], no AST nodes will be visited. [afterLastNode] is still called.
     *
     * When called in [beforeVisitChildNodes], the child nodes of that node will not be visited. [afterVisitChildNodes]
     * is still called for the node and each of its parent nodes. Other nodes in the AST will not be visited. Finally
     * [afterLastNode] is called.
     *
     * When called in [afterVisitChildNodes] the child nodes of that node are already visited. [afterVisitChildNodes] is
     * still called for each of its parent nodes. Other nodes in the AST will not be visited. Finally [afterLastNode] is
     * called.
     *
     * Calling in [afterLastNode] has no effect as traversal of the AST has already been completed.
     */
    public fun stopTraversalOfAST() {
        traversalState = TraversalState.STOP
    }

    private enum class TraversalState {
        /**
         * Traversal of the AST is not started. As no life cycle hooks of the [Rule] have been executed, the [Rule]
         * instance can not contain state specific for the AST.
         */
        NOT_STARTED,

        /**
         * Traversal of the AST is started and should be continued with next node.
         */
        CONTINUE,

        /**
         * Stops traversal of yet unvisited nodes in the AST. See [stopTraversalOfAST] for more details.
         */
        STOP,
    }

    /**
     * About the rule. Background information about the rule and its maintainer. About information is meant to be used in stack traces or
     * API consumers to provide more detailed information about the rule. Please provide all details below, so that users of your rule set
     * can easily get up-to-date information about the rule.
     */
    public data class About(
        /**
         * Name of person, organisation or group maintaining the rule.
         */
        val maintainer: String = "Not specified (and not maintained by the Ktlint project)",

        /**
         * Url to the repository containing the rule.
         */
        val repositoryUrl: String = "Not specified",

        /**
         * Url to the issue tracker of the project which provides the rule.
         */
        val issueTrackerUrl: String = "Not specified",
    )

    public sealed class VisitorModifier {
        public data class RunAfterRule(
            val ruleId: RuleId,
            /**
             * The annotated rule will only be loaded in case the other rule is loaded as well.
             */
            val loadOnlyWhenOtherRuleIsLoaded: Boolean = false,
            /**
             * The annotated rule will only be run in case the other rule is enabled.
             */
            val runOnlyWhenOtherRuleIsEnabled: Boolean = false,
        ) : VisitorModifier()

        public object RunAsLateAsPossible : VisitorModifier()
    }

    /**
     * This interface marks a rule as an 'experimental' rule. A rule marked with this interface will only be executed by ktlint in case the
     * '.editorconfig' allows this rule specifically or all experimental rules. This interface is used by Ktlint internally but is also
     * explicitly meant to be used by custom rule providers.
     */
    public interface Experimental

    /**
     * This interface marks a rule as an Official rule. A rule marked with this interface will only be executed when by ktlint in case the
     * '.editorconfig' contains property "code_style = ktlint_official" or when enabled explicitly. This interface is intended to be used
     * in Ktlint internally only. It may be subject to change at any time without providing any backward compatibility.
     */
    public interface OfficialCodeStyle
}
