package no.nav.syfo.rules

import no.nav.syfo.model.RuleMetadata
import no.nav.syfo.model.Status

enum class TssRuleChain(
    override val ruleId: Int?,
    override val status: Status,
    override val messageForUser: String,
    override val messageForSender: String,
    override val predicate: (RuleData<RuleMetadata>) -> Boolean
) : Rule<RuleData<RuleMetadata>> {
    @Description("Behandlers TSS-ident er ikkje funnet automatisk av systemet")
    TSS_IDENT_MANGLER(
            9999,
            Status.MANUAL_PROCESSING,
            "Behandlers TSS-ident er ikkje funnet automatisk av systemet",
            "Behandlers TSS-ident er ikkje funnet automatisk av systemet", { (_, metadata) ->
        metadata.tssid.isNullOrBlank()
    }),
}
