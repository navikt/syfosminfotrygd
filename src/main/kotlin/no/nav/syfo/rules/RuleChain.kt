package no.nav.syfo.rules

interface RuleChain {
    val rules: List<Rule<*>>

    fun getRuleByName(name: String): Rule<*> {
        return rules.find { name == it.name } ?: throw IllegalArgumentException("Unable to find with name $name")
    }

    fun executeRules(): List<RuleResult<*>> {
        return rules.map { it.executeRule() }
    }
}
