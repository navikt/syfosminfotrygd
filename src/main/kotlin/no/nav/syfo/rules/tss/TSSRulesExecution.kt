package no.nav.syfo.rules.tss

import no.nav.syfo.log
import no.nav.syfo.model.RuleMetadata
import no.nav.syfo.model.sykmelding.Sykmelding
import no.nav.syfo.rules.common.RuleExecution
import no.nav.syfo.rules.common.RuleResult
import no.nav.syfo.rules.common.UtenJuridisk
import no.nav.syfo.rules.dsl.ResultNode
import no.nav.syfo.rules.dsl.RuleNode
import no.nav.syfo.rules.dsl.TreeNode
import no.nav.syfo.rules.dsl.TreeOutput
import no.nav.syfo.rules.dsl.join
import no.nav.syfo.rules.dsl.printRulePath

typealias TSSTreeOutput = TreeOutput<TSSRules, RuleResult>

class TSSRulesExecution(val rootNode: TreeNode<TSSRules, RuleResult> = tssRuleTree) :
    RuleExecution<TSSRules> {
    override fun runRules(sykmelding: Sykmelding, ruleMetadata: RuleMetadata) =
        rootNode.evaluate(sykmelding, ruleMetadata).also { gradertRulePath ->
            log.info("Rules sykmeldingId: ${sykmelding.id}, ${gradertRulePath.printRulePath()}")
        } to UtenJuridisk
}

private fun TreeNode<TSSRules, RuleResult>.evaluate(
    sykmelding: Sykmelding,
    ruleMetadata: RuleMetadata,
): TSSTreeOutput =
    when (this) {
        is ResultNode -> TSSTreeOutput(treeResult = result)
        is RuleNode -> {
            val rule = getRule(rule)
            val result = rule(sykmelding, ruleMetadata)
            val childNode = if (result.ruleResult) yes else no
            result join childNode.evaluate(sykmelding, ruleMetadata)
        }
    }
