package com.pinterest.ktlint.rule.engine.internal

import com.pinterest.ktlint.logger.api.initKtLintKLogger
import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.KtLint
import com.pinterest.ktlint.rule.engine.api.KtLintParseException
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine.Companion.UTF8_BOM
import com.pinterest.ktlint.rule.engine.api.KtLintRuleException
import com.pinterest.ktlint.rule.engine.core.api.EditorConfigProperties
import com.pinterest.ktlint.rule.engine.core.api.Rule
import mu.KotlinLogging
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.lang.FileASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFile

private val KOTLIN_PSI_FILE_FACTORY_PROVIDER = KotlinPsiFileFactoryProvider()

private val LOGGER = KotlinLogging.logger {}.initKtLintKLogger()

internal class RuleExecutionContext private constructor(
    val code: Code,
    val rootNode: FileASTNode,
    val ruleRunners: Set<RuleRunner>,
    val editorConfigProperties: EditorConfigProperties,
    val positionInTextLocator: (offset: Int) -> LineAndColumn,
) {
    private lateinit var suppressionLocator: SuppressionLocator

    init {
        rebuildSuppressionLocator()
    }

    fun rebuildSuppressionLocator() {
        suppressionLocator = SuppressionLocatorBuilder.buildSuppressedRegionsLocator(rootNode)
    }

    fun executeRule(
        rule: Rule,
        fqRuleId: String,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        try {
            rule.startTraversalOfAST()
            rule.beforeFirstNode(editorConfigProperties)
            this.executeRuleOnNodeRecursively(rootNode, rule, fqRuleId, autoCorrect, emit)
            rule.afterLastNode()
        } catch (e: RuleExecutionException) {
            throw KtLintRuleException(
                e.line,
                e.col,
                e.rule.ruleId.value,
                """
                Rule '${e.rule.ruleId.value}' throws exception in file '${code.fileName}' at position (${e.line}:${e.col})
                   Rule maintainer: ${e.rule.about.maintainer}
                   Issue tracker  : ${e.rule.about.issueTrackerUrl}
                   Repository     : ${e.rule.about.repositoryUrl}
                """.trimIndent(),
                e.cause,
            )
        }
    }

    private fun executeRuleOnNodeRecursively(
        node: ASTNode,
        rule: Rule,
        fqRuleId: String,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        /**
         * The [suppressionLocator] can be changed during each visit of node when running [KtLint.format]. So a new handler is to be built
         * before visiting the nodes.
         */
        val suppressHandler = SuppressHandler(suppressionLocator, autoCorrect, emit)
        if (rule.shouldContinueTraversalOfAST()) {
            try {
                suppressHandler.handle(node, rule.ruleId) { autoCorrect, emit ->
                    rule.beforeVisitChildNodes(node, autoCorrect, emit)
                }
                if (rule.shouldContinueTraversalOfAST()) {
                    node
                        .getChildren(null)
                        .forEach { childNode ->
                            suppressHandler.handle(childNode, rule.ruleId) { autoCorrect, emit ->
                                this.executeRuleOnNodeRecursively(
                                    childNode,
                                    rule,
                                    fqRuleId,
                                    autoCorrect,
                                    emit,
                                )
                            }
                        }
                }
                suppressHandler.handle(node, rule.ruleId) { autoCorrect, emit ->
                    rule.afterVisitChildNodes(node, autoCorrect, emit)
                }
            } catch (e: Exception) {
                if (autoCorrect) {
                    // line/col cannot be reliably mapped as exception might originate from a node not present in the
                    // original AST
                    throw RuleExecutionException(
                        rule,
                        0,
                        0,
                        // Prevent extreme long stack trace caused by recursive call and only pass root cause
                        e.cause ?: e,
                    )
                } else {
                    val (line, col) = positionInTextLocator(node.startOffset)
                    throw RuleExecutionException(
                        rule,
                        line,
                        col,
                        // Prevent extreme long stack trace caused by recursive call and only pass root cause
                        e.cause ?: e,
                    )
                }
            }
        }
    }

    companion object {
        internal fun createRuleExecutionContext(
            ktLintRuleEngine: KtLintRuleEngine,
            code: Code,
        ): RuleExecutionContext {
            val psiFileFactory = KOTLIN_PSI_FILE_FACTORY_PROVIDER.getKotlinPsiFileFactory(ktLintRuleEngine.isInvokedFromCli)
            val normalizedText = normalizeText(code.content)
            val positionInTextLocator = buildPositionInTextLocator(normalizedText)

            val psiFileName =
                code.fileName
                    ?: if (code.script) {
                        "File.kts"
                    } else {
                        "File.kt"
                    }
            val psiFile = psiFileFactory.createFileFromText(
                psiFileName,
                KotlinLanguage.INSTANCE,
                normalizedText,
            ) as KtFile

            psiFile
                .findErrorElement()
                ?.let { errorElement ->
                    val (line, col) = positionInTextLocator(errorElement.textOffset)
                    throw KtLintParseException(line, col, errorElement.errorDescription)
                }

            val rootNode = psiFile.node

            val ruleRunners =
                ktLintRuleEngine
                    .ruleProviders
                    .map { RuleRunner(it) }
                    .distinctBy { it.ruleId }
                    .toSet()
            val editorConfigProperties = with(ktLintRuleEngine) {
                val rules =
                    ruleRunners
                        .map { it.getRule() }
                        .toSet()
                ktLintRuleEngine.editorConfigLoader.load(
                    filePath = code.filePath,
                    rules = rules,
                    editorConfigDefaults = editorConfigDefaults,
                    editorConfigOverride = editorConfigOverride,
                    ignoreEditorConfigOnFileSystem = ignoreEditorConfigOnFileSystem,
                ).also {
                    // TODO: Remove warning below in KtLint 0.52 or later as some users skips multiple versions
                    it.warnIfPropertyIsObsolete("disabled_rules", "0.49")
                    // TODO: Remove warning below in KtLint 0.52 or later as some users skips multiple versions
                    it.warnIfPropertyIsObsolete("ktlint_disabled_rules", "0.49")
                }
            }

            if (!code.isStdIn) {
                // TODO: Remove in KtLint 0.49
                rootNode.putUserData(KtLint.FILE_PATH_USER_DATA_KEY, code.filePath.toString())
            }

            return RuleExecutionContext(
                code,
                rootNode,
                ruleRunners,
                editorConfigProperties,
                positionInTextLocator,
            )
        }

        private fun normalizeText(text: String): String {
            return text
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceFirst(UTF8_BOM, "")
        }

        private fun PsiElement.findErrorElement(): PsiErrorElement? {
            if (this is PsiErrorElement) {
                return this
            }
            this.children.forEach { child ->
                val errorElement = child.findErrorElement()
                if (errorElement != null) {
                    return errorElement
                }
            }
            return null
        }
    }
}

private fun EditorConfigProperties.warnIfPropertyIsObsolete(
    propertyName: String,
    ktlintVersion: String,
) {
    if (this.contains(propertyName)) {
        LOGGER.warn {
            "Editorconfig property '$propertyName' is obsolete and is not used by KtLint starting from version $ktlintVersion. Remove " +
                "the property from all '.editorconfig' files."
        }
    }
}

private class RuleExecutionException(
    val rule: Rule,
    val line: Int,
    val col: Int,
    override val cause: Throwable,
) : Throwable(cause)
