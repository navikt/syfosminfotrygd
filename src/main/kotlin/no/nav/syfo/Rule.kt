package no.nav.syfo

import no.nav.syfo.model.Status

interface Rule<in T> {
    val name: String
    val ruleId: Int?
    val status: Status
    val predicate: (T) -> Boolean
}
data class Outcome(val outcomeName: String, val status: Status)

interface RuleChain<in T> {
    val name: String
    val rules: List<Rule<T>>
    fun executeFlow(input: T): List<Outcome> = rules
            .filter { rule -> RULE_HIT_SUMMARY.labels(rule.name).startTimer().use { rule.predicate(input) } }
            .onEach { RULE_HIT_COUNTER.labels(it.name) }
            .map { Outcome(it.name, it.status) }
}

@Retention(AnnotationRetention.RUNTIME)
annotation class Description(val description: String)
