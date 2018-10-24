package no.nav.syfo.rules

import no.nav.syfo.InfotrygdForespAndHealthInformation
import no.nav.syfo.OutcomeType
import no.nav.syfo.Rule
import no.nav.syfo.RuleChain
import java.time.LocalDate
import java.time.Period

val postInfotrygdQueryChain = RuleChain<InfotrygdForespAndHealthInformation>(
        name = "Validation rule chain",
        description = "Rules for the Infotrygd query, to check that that it can be updedet in Infotrygd",
        rules = listOf(
                Rule(
                        name = "Patients has moved, has stopped kode FL",
                        outcomeType = OutcomeType.PERSON_MOVING_KODE_FL,
                        description = "This is a rule that hits whenever there is a stopped kode FL"
                ) {
                    it.infotrygdForesp.sMhistorikk.sykmelding.any {
                        it.periode.stans == "FL"
                    }
                },
                Rule(
                        name = "Patients has stopped kode DØD",
                        outcomeType = OutcomeType.PATIENT_DEAD,
                        description = "This is a rule that hits whenever there is a stopped kode DØD"
                ) {
                    it.infotrygdForesp.sMhistorikk.sykmelding.any {
                        it.periode.stans == "DØD"
                    }
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
                    val sMhistorikkfriskmeldtDato: LocalDate? = it.infotrygdForesp.sMhistorikk.sykmelding.firstOrNull()?.periode?.friskmeldtDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
                    val sMhistorikkutbetTOM: LocalDate? = it.infotrygdForesp.sMhistorikk.sykmelding.firstOrNull()?.periode?.utbetTOM?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()

                    sMhistorikkfriskmeldtDato?.isBefore(sMhistorikkutbetTOM) ?: false
                },
                Rule(
                        name = "Patients new clean bill date before payout",
                        outcomeType = OutcomeType.NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE,
                        description = "New clean bill date is earlier than registered clean bill date of registration in Infotrygd"
                ) {

                    val newfriskmeldtDato: LocalDate? = it.infotrygdForesp.sMhistorikk.sykmelding.firstOrNull()?.periode?.friskmeldtDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
                    val secoundfriskmeldtDato: LocalDate? = it.infotrygdForesp.sMhistorikk.sykmelding.drop(1).firstOrNull()?.periode?.friskmeldtDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()

                    when (secoundfriskmeldtDato) {
                        null -> false
                        else -> newfriskmeldtDato?.isAfter(secoundfriskmeldtDato) ?: false
                    }
                },
                Rule(
                        name = "Patients has partially conincident sick leave period with previously registrered sick lave",
                        outcomeType = OutcomeType.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE,
                        description = "This is a rule that hits whenever the patient has partially conincident sick leave period with previously registrered sick lave"
                ) {

                    val healthInformationPeriodeFomdato: LocalDate? = it.healthInformation.aktivitet.periode.firstOrNull()?.periodeFOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
                    val infotrygdforespArbuforFom: LocalDate? = it.infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.arbufoerFOM?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()

                    when (infotrygdforespArbuforFom) {
                        null -> false
                        else -> healthInformationPeriodeFomdato?.isAfter(infotrygdforespArbuforFom) ?: false
                    }
                },
                Rule(
                        name = "Message not registered in IT",
                        outcomeType = OutcomeType.MESSAGE_NOT_IN_INFOTRYGD,
                        description = "Hvis meldingen ikke kan knyttes til noe registrert tilfelle i Infotrygd, og legen har spesifisert syketilfellets startdato forskjellig fra første fraværsdag"
                ) {
                    val infotrygdforespSmHistFinnes: Boolean = it.infotrygdForesp.sMhistorikk.status.kodeMelding == "04"
                    val healthInformationSyketilfelleStartDato: LocalDate? = it.healthInformation.syketilfelleStartDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
                    val healthInformationPeriodeFomdato: LocalDate? = it.healthInformation.aktivitet.periode.firstOrNull()?.periodeFOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()

                    when (healthInformationPeriodeFomdato) {
                        null -> false
                        else -> !infotrygdforespSmHistFinnes && healthInformationSyketilfelleStartDato?.isEqual(healthInformationPeriodeFomdato) ?: false
                    }
                },
                Rule(
                        // TODO need to check if the rule is implemented correctly
                        name = "Patient has a diffrent NAV Office",
                        outcomeType = OutcomeType.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE,
                        description = "Hvis sykmeldingen er forlengelse av registrert sykepengehistorikk fra annet kontor så medlingen gå til manuell behandling slik at  saksbehandler kan registrere sykepengetilfellet på ny identdato og  send oppgave til Nav forvaltning for registrering av inntektsopplysninger."
                ) {
                    val infotrygdforespArbuforFom: LocalDate? = it.infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.arbufoerFOM?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
                    val infotrygdforespHistArbuforFom: LocalDate? = it.infotrygdForesp.sMhistorikk?.sykmelding?.drop(1)?.firstOrNull()?.periode?.arbufoerFOM?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
                    val healthInformationPeriodeFomdato: LocalDate? = it.healthInformation.aktivitet?.periode?.firstOrNull()?.periodeFOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
                    val infotrygdforespUtbetalingTOM: LocalDate? = it.infotrygdForesp.sMhistorikk?.sykmelding?.drop(1)?.firstOrNull()?.periode?.utbetTOM?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
                    val infotrygdforespFriskKode: String? = it.infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.friskKode

                    when (infotrygdforespHistArbuforFom) {
                        null -> false
                        else -> healthInformationPeriodeFomdato?.isAfter(infotrygdforespHistArbuforFom) ?: false &&
                                infotrygdforespUtbetalingTOM?.isAfter(infotrygdforespHistArbuforFom) ?: false &&
                                infotrygdforespArbuforFom?.isAfter(infotrygdforespHistArbuforFom) ?: false &&
                                !infotrygdforespFriskKode.equals("H")
                    }
                },
                Rule(
                        name = "Patients disability is changed",
                        outcomeType = OutcomeType.DIABILITY_GRADE_CANGED,
                        description = "Hvis uføregrad er endret går meldingen til manuell behandling") {
                    val disabilityGradeIT: Int? = it.infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.ufoeregrad?.toInt()
                    val healthInformationDisabilityGrade: Int? = it.healthInformation.aktivitet.periode.firstOrNull()?.gradertSykmelding?.sykmeldingsgrad
                    val sMhistorikkArbuforFOM: LocalDate? = it.infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.arbufoerFOM?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
                    val healthInformationPeriodeFOMDato: LocalDate? = it.healthInformation.aktivitet.periode.firstOrNull()?.periodeFOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
                    val healthInformationPeriodeTOMDato: LocalDate? = it.healthInformation.aktivitet.periode.firstOrNull()?.periodeTOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()

                    disabilityGradeIT != healthInformationDisabilityGrade &&
                            sMhistorikkArbuforFOM?.isAfter(healthInformationPeriodeFOMDato) ?: false &&
                            sMhistorikkArbuforFOM?.isBefore(healthInformationPeriodeTOMDato) ?: false
                },
                Rule(
                        // TODO need to check if the rule is implemented correctly
                        name = "Error message from Infotrygd",
                        outcomeType = OutcomeType.ERROR_FROM_IT,
                        description = "Feilmelding fra Infotrygd") {
                    val hovedStatusKodemelding: Int? = it.infotrygdForesp.hovedStatus.kodeMelding.toIntOrNull()
                    val sMhistorikktStatusKodemelding: Int? = it.infotrygdForesp.sMhistorikk?.status?.kodeMelding?.toIntOrNull()
                    val parallelleYtelserStatusKodemelding: Int? = it.infotrygdForesp.parallelleYtelser?.status?.kodeMelding?.toIntOrNull()
                    val diagnoseOKUttrekkStatusKodemelding: Int? = it.infotrygdForesp.diagnosekodeOK?.status?.kodeMelding?.toIntOrNull()
                    val pasientUttrekkStatusKodemelding: Int? = it.infotrygdForesp.pasient?.status?.kodeMelding?.toIntOrNull()

                    hovedStatusKodemelding ?: 0 > 4 &&
                            sMhistorikktStatusKodemelding ?: 0 > 4 &&
                            parallelleYtelserStatusKodemelding ?: 0 > 4 &&
                            diagnoseOKUttrekkStatusKodemelding ?: 0 > 4 &&
                            pasientUttrekkStatusKodemelding ?: 0 > 4
                },
                Rule(
                        name = "Patient has extantion is type FA",
                        outcomeType = OutcomeType.EXTANION_OVER_FA,
                        description = "Hvis forlengelse utover registrert tiltak FA tiltak ") {
                    val sMhistorikkTilltakTypeFA: Boolean = it.infotrygdForesp.sMhistorikk.sykmelding.any {
                        it.historikk.firstOrNull()?.tilltak?.type == "FA"
                    }

                    val healthInformationPeriodeFomdato: LocalDate? = it.healthInformation.aktivitet.periode.firstOrNull()?.periodeFOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
                    val sMhistorikkTilltakTypeFATomDato: LocalDate? = it.infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.historikk?.firstOrNull {
                        it.tilltak.type == "FA"
                    }?.tilltak?.tom?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()

                    sMhistorikkTilltakTypeFA && healthInformationPeriodeFomdato?.isAfter(sMhistorikkTilltakTypeFATomDato) ?: false
                },
                Rule(
                        name = "Patient has sick leav periode ended",
                        outcomeType = OutcomeType.PERIOD_FOR_AA_ENDED,
                        description = "Hvis perioden er avsluttet (AA)") {
                    it.infotrygdForesp.sMhistorikk.sykmelding.any {
                        it.periode.stans == "AA"
                    }
                },
                Rule(
                        name = "Patient",
                        outcomeType = OutcomeType.PERIOD_IS_AF,
                        description = "Hvis perioden er avsluttet-frisk (AF) ") {
                    it.infotrygdForesp.sMhistorikk.sykmelding.any {
                        it.periode.stans == "AF"
                    }
                },
                Rule(
                        name = "Patient",
                        outcomeType = OutcomeType.NOT_VALDIG_DIAGNOSE,
                        description = "Hvis det er oppgitt ugyldig hoveddiagnose i forhold til angitt kodeverk") {
                    !it.infotrygdForesp.diagnosekodeOK.isDiagnoseOk
                },
                Rule(
                        name = "Patient",
                        outcomeType = OutcomeType.PERIOD_ENDED_DEAD,
                        description = "Hvis perioden er avsluttet-død(AD).") {
                    it.infotrygdForesp.sMhistorikk.sykmelding.any {
                        it.periode.stans == "AD"
                    }
                },
                Rule(
                        name = "Patient has recived max sick leave payout",
                        outcomeType = OutcomeType.MAX_SICK_LEAVE_PAYOUT,
                        description = "Hvis maks sykepenger er utbetalt") {
                    it.infotrygdForesp.sMhistorikk.sykmelding.any {
                        it.periode.stans == "MAX"
                    }
                },
                Rule(
                        name = "Patient has recived a refusal",
                        outcomeType = OutcomeType.REFUSAL_IS_REGISTERED,
                        description = "Hvis det er registrert avslag i IT") {
                    it.infotrygdForesp.sMhistorikk.sykmelding.any {
                        !it.periode.avslag.isNullOrEmpty()
                    }
                },
                Rule(
                        name = "Patient sick leave period is over 1 year",
                        outcomeType = OutcomeType.SICK_LEAVE_PERIOD_OVER_1_YEAR,
                        description = "Hvis sykmeldingsperioden er større enn 1 år meldingen til manuell behandling") {
                    val sMhistorikkArbuforFOM: LocalDate? = it.infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.arbufoerFOM?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
                    val sMhistorikkArbuforTOM: LocalDate? = it.infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.arbufoerTOM?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
                    if (sMhistorikkArbuforFOM != null && sMhistorikkArbuforTOM != null) {
                        Period.between(sMhistorikkArbuforFOM, sMhistorikkArbuforTOM).years > 1
                    } else {
                        false
                    }
                }

        ))