package no.nav.syfo.rules

import no.nav.syfo.model.RuleMetadata
import no.nav.syfo.model.Status

class TssRuleChain(
    private val metadata: RuleMetadata
) : RuleChain {
    // "Behandlers TSS-ident er ikke funnet automatisk av systemet
    override val rules: List<Rule<*>> = listOf(
        Rule(
            name = "TSS_IDENT_MANGLER",
            ruleId = 9999,
            status = Status.MANUAL_PROCESSING,
            messageForUser = "Behandlers TSS-ident er ikke funnet automatisk av systemet",
            messageForSender = "Behandlers TSS-ident er ikke funnet automatisk av systemet",
            input = object {
                val tssId = metadata.tssid
            },
            predicate = { input ->
                input.tssId.isNullOrBlank()
            }
        )
    )
}
