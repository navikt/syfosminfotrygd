package no.nav.syfo.rules.dsl

data class RuleResult<T>(
    val ruleInputs: Map<String, Any> = emptyMap(),
    val ruleResult: Boolean,
    val rule: T,
)

data class TreeOutput<T, S>(
    val ruleInputs: Map<String, Any> = mapOf(),
    val rulePath: List<RuleResult<T>> = emptyList(),
    val treeResult: S,
)

fun <T, S> TreeOutput<T, S>.printRulePath(): String {
    return rulePath
        .joinToString(separator = "->") { "${it.rule}(${if (it.ruleResult) "yes" else "no"})" }
        .plus("->$treeResult")
}

infix fun <T, S> RuleResult<T>.join(rulesOutput: TreeOutput<T, S>) =
    TreeOutput(
        ruleInputs = ruleInputs + rulesOutput.ruleInputs,
        rulePath = listOf(this) + rulesOutput.rulePath,
        treeResult = rulesOutput.treeResult,
    )
