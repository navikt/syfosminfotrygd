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
    @Description("Behandlers TSS-ident ikkje funnet")
    TSS_IDENT_MANGLER(
            9999,
            Status.MANUAL_PROCESSING,
            "Behandlers TSS-ident ikkje funnet",
            "Behandlers TSS-ident ikkje funnet", { (_, metadata) ->
        metadata.tssid.isNullOrBlank()
    }),
}
