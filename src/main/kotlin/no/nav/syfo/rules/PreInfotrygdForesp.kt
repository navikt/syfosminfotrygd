package no.nav.syfo.rules

import no.nav.model.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.OutcomeType
import no.nav.syfo.Rule
import no.nav.syfo.RuleChain

val preInfotrygdQueryChain = RuleChain<HelseOpplysningerArbeidsuforhet>(
        name = "Validation rule chain",
        description = "Rules to run before the Infotrygd query",
        rules = listOf(
                Rule(
                        name = "Patient is has treatments days",
                        outcomeType = OutcomeType.TREATMENT_DAYS,
                        description = "This is a rule that hits whenever there is treatments days"
                ) {
                    it.aktivitet.periode.any {
                        it.behandlingsdager.antallBehandlingsdagerUke > 0
                    }
                },
                Rule(
                        name = "Patient has travel grants",
                        outcomeType = OutcomeType.TRAVEL_GRANTS,
                        description = "This is a rule that hits whenever the patients has travel grants"
                ) {
                    it.aktivitet.periode.any {
                        it.isReisetilskudd
                    }
                },
                Rule(
                        name = "New sickdateaccurence start date",
                        outcomeType = OutcomeType.MESSAGE_NOT_IN_INFOTRYGD,
                        description = "This is a rule that hits whenever the patient is not found in IP"
                ) {
                    true
                    // include rule here
                }
        ))