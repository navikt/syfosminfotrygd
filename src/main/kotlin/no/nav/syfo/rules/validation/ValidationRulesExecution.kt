package no.nav.syfo.rules.validation

import no.nav.syfo.log
import no.nav.syfo.model.RuleMetadata
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.rules.common.RuleExecution
import no.nav.syfo.rules.common.RuleResult
import no.nav.syfo.rules.common.UtenJuridisk
import no.nav.syfo.rules.dsl.ResultNode
import no.nav.syfo.rules.dsl.RuleNode
import no.nav.syfo.rules.dsl.TreeNode
import no.nav.syfo.rules.dsl.TreeOutput
import no.nav.syfo.rules.dsl.join
import no.nav.syfo.rules.dsl.printRulePath

typealias ValidationTreeOutput = TreeOutput<ValidationRules, RuleResult>
typealias ValidationTreeNode = TreeNode<ValidationRules, RuleResult>

class ValidationRulesExecution(private val rootNode: ValidationTreeNode = validationRuleTree) :
    RuleExecution<ValidationRules> {
    override fun runRules(sykmelding: Sykmelding, ruleMetadata: RuleMetadata) =
        rootNode
            .evaluate(sykmelding, ruleMetadata)
            .also { validationRulePath ->
                log.info("Rules sykmeldingId: ${sykmelding.id}, ${validationRulePath.printRulePath()}")
            } to UtenJuridisk
}

private fun TreeNode<ValidationRules, RuleResult>.evaluate(
    sykmelding: Sykmelding,
    ruleMetadata: RuleMetadata,
): ValidationTreeOutput =
    when (this) {
        is ResultNode -> ValidationTreeOutput(treeResult = result)
        is RuleNode -> {
            val rule = getRule(rule)
            val result = rule(sykmelding, ruleMetadata)
            val childNode = if (result.ruleResult) yes else no
            result join childNode.evaluate(sykmelding, ruleMetadata)
        }
    }
