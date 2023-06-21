package no.nav.syfo.rules.tss

import no.nav.syfo.model.RuleMetadata
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.rules.dsl.RuleResult

typealias Rule<T> = (sykmelding: Sykmelding, ruleMetadata: RuleMetadata) -> RuleResult<T>

typealias TSSRule = Rule<TSSRules>

val tssidentmangler: TSSRule = { _, ruleMetadata ->
    val tssId = ruleMetadata.tssid

    RuleResult(
        ruleInputs = mapOf("tssId" to (tssId ?: "")),
        rule = TSSRules.TSS_IDENT_MANGLER,
        ruleResult = tssId.isNullOrBlank(),
    )
}
