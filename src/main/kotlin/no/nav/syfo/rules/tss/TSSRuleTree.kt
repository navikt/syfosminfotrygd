package no.nav.syfo.rules.tss

import no.nav.syfo.model.sykmelding.Status
import no.nav.syfo.rules.common.RuleResult
import no.nav.syfo.rules.dsl.RuleNode
import no.nav.syfo.rules.dsl.tree

val tssRuleTree =
    tree<TSSRules, RuleResult>(TSSRules.TSS_IDENT_MANGLER) {
        yes(Status.MANUAL_PROCESSING, TSSRuleHit.TSS_IDENT_MANGLER)
        no(Status.OK)
    }

internal fun RuleNode<TSSRules, RuleResult>.yes(status: Status, ruleHit: TSSRuleHit? = null) {
    yes(RuleResult(status, ruleHit?.ruleHit))
}

internal fun RuleNode<TSSRules, RuleResult>.no(status: Status, ruleHit: TSSRuleHit? = null) {
    no(RuleResult(status, ruleHit?.ruleHit))
}

fun getRule(rules: TSSRules): Rule<TSSRules> {
    return when (rules) {
        TSSRules.TSS_IDENT_MANGLER -> tssidentmangler
    }
}
