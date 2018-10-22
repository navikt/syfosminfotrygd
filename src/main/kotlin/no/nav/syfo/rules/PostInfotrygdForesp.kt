package no.nav.syfo.rules

import no.nav.syfo.InfotrygdForespAndHealthInformation
import no.nav.syfo.OutcomeType
import no.nav.syfo.Rule
import no.nav.syfo.RuleChain
import java.math.BigInteger
import javax.xml.datatype.XMLGregorianCalendar

val postInfotrygdQueryChain = RuleChain<InfotrygdForespAndHealthInformation>(
        name = "Validation rule chain",
        description = "Rules for the Infotrygd query, to check that that it can be updedet in Infotrygd",
        rules = listOf(
                Rule(
                        name = "Patients has moved, has stopped kode FL",
                        outcomeType = OutcomeType.PERSON_MOVING_KODE_FL,
                        description = "This is a rule that hits whenever there is travel grant"
                ) {
                    it.infotrygdForesp.sMhistorikk.status.equals("FL")
                },
                Rule(
                        name = "Patients has moved, has stopped kode DOD",
                        outcomeType = OutcomeType.PATIENT_DEAD,
                        description = "This is a rule that hits whenever there is travel grant"
                ) {
                    it.infotrygdForesp.sMhistorikk.status.equals("DØD")
                },
                Rule(
                        name = "Patients not i IP",
                        outcomeType = OutcomeType.PATIENT_NOT_IN_IP,
                        description = "This is a rule that hits whenever the patient is not found in IP"
                ) {
                    !it.infotrygdForesp.pasient.isFinnes
                },
                Rule(
                        name = "Patients new clean bill date before payout",
                        outcomeType = OutcomeType.NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT,
                        description = "This is a rule that hits whenever the patient new clean bill date is before payout date"
                ) {
                    it.infotrygdForesp.sMhistorikk.sykmelding.any {
                        it.periode.friskmeldtDato.compare(it.periode.utbetTOM) > 0
                    }
                },
                Rule(
                        name = "Patients new clean bill date before payout",
                        outcomeType = OutcomeType.NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE,
                        description = "New clean bill date is earlier than registered clean bill date of registration in Infotrygd"
                ) {

                    val newfriskmeldtDato: XMLGregorianCalendar = it.infotrygdForesp.sMhistorikk.sykmelding.first().periode.friskmeldtDato
                    val secoundfriskmeldtDato: XMLGregorianCalendar = it.infotrygdForesp.sMhistorikk.sykmelding.drop(1).first().periode.friskmeldtDato

                    newfriskmeldtDato.compare(secoundfriskmeldtDato) > 0
                },
                Rule(
                        name = "Patients has partially conincident sick leave period with previously registrered sick lave",
                        outcomeType = OutcomeType.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE,
                        description = "This is a rule that hits whenever the patient has partially conincident sick leave period with previously registrered sick lave"
                ) {

                    val healthInformationPeriodeFomdato: XMLGregorianCalendar = it.healthInformation.aktivitet.periode.first().periodeFOMDato
                    val infotrygdforespArbuforFom: XMLGregorianCalendar = it.infotrygdForesp.sMhistorikk.sykmelding.drop(1).first().periode.arbufoerFOM

                    healthInformationPeriodeFomdato.compare(infotrygdforespArbuforFom) > 0
                },
                Rule(
                        name = "Patients",
                        outcomeType = OutcomeType.MESSAGE_NOT_IN_INFOTRYGD,
                        description = "Hvis meldingen ikke kan knyttes til noe registrert tilfelle i Infotrygd, og legen har spesifisert syketilfellets startdato forskjellig fra første fraværsdag, sendes meldingen til manuell behandling"
                ) {
                    val infotrygdforespSmHistFinnes: Boolean = it.infotrygdForesp.sMhistorikk.status.kodeMelding == "04"
                    val healthInformationSyketilfelleStartDato: XMLGregorianCalendar = it.healthInformation.syketilfelleStartDato
                    val healthInformationPeriodeFomdato: XMLGregorianCalendar = it.healthInformation.aktivitet.periode.first().periodeFOMDato

                    infotrygdforespSmHistFinnes && healthInformationSyketilfelleStartDato.equals(healthInformationPeriodeFomdato)
                },
                Rule(
                        // TODO need to check if the rule is implemented correctly
                        name = "Patients",
                        outcomeType = OutcomeType.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE,
                        description = "Hvis sykmeldingen er forlengelse av registrert sykepengehistorikk fra annet kontor så medlingen gå til manuell behandling slik at  saksbehandler kan registrere sykepengetilfellet på ny identdato og  send oppgave til Nav forvaltning for registrering av inntektsopplysninger."
                ) {
                    val infotrygdforespArbuforFom: XMLGregorianCalendar = it.infotrygdForesp.sMhistorikk.sykmelding.first().periode.arbufoerFOM
                    val infotrygdforespHistArbuforFom: XMLGregorianCalendar = it.infotrygdForesp.sMhistorikk.sykmelding.drop(1).first().periode.arbufoerFOM
                    val healthInformationPeriodeFomdato: XMLGregorianCalendar = it.healthInformation.aktivitet.periode.first().periodeFOMDato
                    val infotrygdforespUtbetalingTOM: XMLGregorianCalendar = it.infotrygdForesp.sMhistorikk.sykmelding.drop(1).first().periode.utbetTOM
                    val infotrygdforespFriskKode: String = it.infotrygdForesp.sMhistorikk.sykmelding.first().periode.friskKode

                    healthInformationPeriodeFomdato.compare(infotrygdforespHistArbuforFom) > 0 &&
                            infotrygdforespUtbetalingTOM.compare(infotrygdforespHistArbuforFom) > 0 &&
                            infotrygdforespArbuforFom.compare(infotrygdforespHistArbuforFom) > 0 &&
                            !infotrygdforespFriskKode.equals("H")
                },
                Rule(
                        // TODO need to check if the rule is implemented correctly
                        name = "Patients",
                        outcomeType = OutcomeType.DIABILITY_GRADE_CANGED,
                        description = "Hvis uføregrad er endret går meldingen til manuell behandling") {
                    val disabilityGradeIT: BigInteger = it.infotrygdForesp.sMhistorikk.sykmelding.first().periode.ufoeregrad
                    val healthInformationdisabilityGrade: Int = it.healthInformation.aktivitet.periode.first().gradertSykmelding.sykmeldingsgrad

                    !disabilityGradeIT.equals(healthInformationdisabilityGrade)
                },
                Rule(
                        // TODO need to check if the rule is implemented correctly
                        name = "Patients",
                        outcomeType = OutcomeType.ERROR_FROM_IT,
                        description = "Feilmelding fra Infotrygd") {
                    val hovedStatusKodemelding: Int? = it.infotrygdForesp.hovedStatus.kodeMelding.toIntOrNull()
                    val sMhistorikktStatusKodemelding: Int? = it.infotrygdForesp.sMhistorikk.status.kodeMelding.toIntOrNull()
                    val parallelleYtelserStatusKodemelding: Int? = it.infotrygdForesp.parallelleYtelser.status.kodeMelding.toIntOrNull()
                    val diagnoseOKUttrekkStatusKodemelding: Int? = it.infotrygdForesp.diagnosekodeOK.status.kodeMelding.toIntOrNull()
                    val pasientUttrekkStatusKodemelding: Int? = it.infotrygdForesp.pasient.status.kodeMelding.toIntOrNull()

                    hovedStatusKodemelding ?: 0 > 4 &&
                            sMhistorikktStatusKodemelding ?: 0 > 4 &&
                            parallelleYtelserStatusKodemelding ?: 0 > 4 &&
                            diagnoseOKUttrekkStatusKodemelding ?: 0 > 4 &&
                            pasientUttrekkStatusKodemelding ?: 0 > 4
                }

        ))