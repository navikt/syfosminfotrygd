package no.nav.syfo.rules.common

import no.nav.syfo.model.RuleMetadata
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.rules.dsl.TreeOutput

interface RuleExecution<T> {
    fun runRules(sykmelding: Sykmelding, ruleMetadata: RuleMetadata): Pair<TreeOutput<T, RuleResult>, Juridisk>
}
