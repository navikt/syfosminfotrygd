package no.nav.syfo.rules.tss

import no.nav.syfo.model.sykmelding.Status
import no.nav.syfo.rules.common.RuleHit

enum class TSSRuleHit(
    val ruleHit: RuleHit,
) {
    TSS_IDENT_MANGLER(
        ruleHit =
            RuleHit(
                rule = "TSS_IDENT_MANGLER",
                status = Status.MANUAL_PROCESSING,
                messageForSender = "Behandlers TSS-ident er ikke funnet automatisk av systemet",
                messageForUser = "Behandlers TSS-ident er ikke funnet automatisk av systemet",
            ),
    ),
}
