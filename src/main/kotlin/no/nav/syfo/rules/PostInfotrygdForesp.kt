package no.nav.syfo.rules

import no.nav.model.infotrygdSporing.InfotrygdForesp
import no.nav.syfo.OutcomeType
import no.nav.syfo.Rule
import no.nav.syfo.RuleChain
import javax.xml.datatype.XMLGregorianCalendar

val postInfotrygdQueryChain = RuleChain<InfotrygdForesp>(
        name = "Validation rule chain",
        description = "Rules for the Infotrygd query, to check that that it can be updedet in Infotrygd",
        rules = listOf(
                Rule(
                        name = "Patients has moved, has stopped kode FL",
                        outcomeType = OutcomeType.PERSON_MOVING_KODE_FL,
                        description = "This is a rule that hits whenever there is travel grant"
                ) {
                    it.sMhistorikk.status.equals("FL")
                },
                Rule(
                        name = "Patients has moved, has stopped kode DOD",
                        outcomeType = OutcomeType.PATIENT_DEAD,
                        description = "This is a rule that hits whenever there is travel grant"
                ) {
                    it.sMhistorikk.status.equals("DØD")
                },
                Rule(
                        name = "Patients not i IP",
                        outcomeType = OutcomeType.PATIENT_NOT_IN_IP,
                        description = "This is a rule that hits whenever the patient is not found in IP"
                ) {
                    !it.pasient.isFinnes
                },
                Rule(
                        name = "Patients new clean bill date before payout",
                        outcomeType = OutcomeType.NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT,
                        description = "This is a rule that hits whenever the patient new clean bill date is before payout date"
                ) {
                    it.sMhistorikk.sykmelding.any {
                        it.periode.friskmeldtDato.compare(it.periode.utbetTOM) > 0
                    }
                },
                Rule(
                        name = "Patients new clean bill date before payout",
                        outcomeType = OutcomeType.NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE,
                        description = "New clean bill date is earlier than registered clean bill date of registration in Infotrygd"
                ) {

                    val newfriskmeldtDato: XMLGregorianCalendar = it.sMhistorikk.sykmelding.first().periode.friskmeldtDato
                    val secoundfriskmeldtDato: XMLGregorianCalendar = it.sMhistorikk.sykmelding.drop(1).first().periode.friskmeldtDato

                    newfriskmeldtDato.compare(secoundfriskmeldtDato) > 0
                }

        ))