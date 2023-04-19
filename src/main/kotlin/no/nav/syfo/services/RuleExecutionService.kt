package no.nav.syfo.services

import no.nav.syfo.model.RuleMetadata
import no.nav.syfo.model.Status
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.rules.common.Juridisk
import no.nav.syfo.rules.common.RuleExecution
import no.nav.syfo.rules.common.RuleResult
import no.nav.syfo.rules.dsl.TreeOutput
import no.nav.syfo.rules.tss.TSSRulesExecution
import no.nav.syfo.rules.tss.tssRuleTree
import no.nav.syfo.rules.validation.ValidationRulesExecution
import no.nav.syfo.rules.validation.validationRuleTree

class RuleExecutionService() {

    private val ruleExecution = sequenceOf(
        ValidationRulesExecution(validationRuleTree),
        TSSRulesExecution(tssRuleTree),
    )

    fun runRules(
        sykmelding: Sykmelding,
        ruleMetadataSykmelding: RuleMetadata,
        sequence: Sequence<RuleExecution<out Enum<*>>> = ruleExecution,
    ): List<Pair<TreeOutput<out Enum<*>, RuleResult>, Juridisk>> {
        var lastStatus = Status.OK
        val results = sequence
            .map { it.runRules(sykmelding, ruleMetadataSykmelding) }
            .takeWhile {
                if (lastStatus == Status.OK) {
                    lastStatus = it.first.treeResult.status
                    true
                } else {
                    false
                }
            }
        return results.toList()
    }
}
