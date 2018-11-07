package no.nav.syfo.rules

import no.nav.model.infotrygdSporing.InfotrygdForesp
import no.nav.model.infotrygdSporing.TypeMottakerKode
import no.nav.model.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.Description
import no.nav.syfo.Rule
import no.nav.syfo.model.Status
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.YEARS

data class RuleData(
    val infotrygdForesp: InfotrygdForesp,
    val healthInformation: HelseOpplysningerArbeidsuforhet
)

enum class ValidationRules(override val ruleId: Int?, override val status: Status, override val predicate: (RuleData) -> Boolean) : Rule<RuleData> {

    @Description("Hvis pasienten ikke finnes i infotrygd")
    PATIENT_NOT_IN_IP(1501, Status.MANUAL_PROCESSING, { (infotrygdForesp) ->
        when (infotrygdForesp.pasient?.isFinnes) {
            null -> false
            else -> !infotrygdForesp.pasient.isFinnes
        }
    }),

    @Description("Hvis meldingen ikke kan knyttes til noe registrert tilfelle i Infotrygd, og legen har spesifisert syketilfellets startdato forskjellig fra første fraværsdag")
    MESSAGE_NOT_IN_INFOTRYGD(1510, Status.MANUAL_PROCESSING, { (infotrygdForesp, healthInformation) ->
        val infotrygdforespSmHistFinnes: kotlin.Boolean = infotrygdForesp.sMhistorikk?.status?.kodeMelding != "04"
        val healthInformationSyketilfelleStartDato: LocalDate? = healthInformation.syketilfelleStartDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
        val healthInformationPeriodeFomdato: LocalDate? = healthInformation.aktivitet.periode.firstOrNull()?.periodeFOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()

        if (healthInformationPeriodeFomdato != null &&
                healthInformationSyketilfelleStartDato != null) {
            !infotrygdforespSmHistFinnes && healthInformationSyketilfelleStartDato != (healthInformationPeriodeFomdato)
        } else {
            false
        }
    }),

    @Description("Hvis delvis sammenfallende sykmeldingsperiode er registrert i Infotrygd")
    PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE(1513, Status.MANUAL_PROCESSING, { (infotrygdForesp, healthInformation) ->
        val infotrygdforespArbuforFom: LocalDate? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.arbufoerFOM?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()

        healthInformation.aktivitet.periode?.any { periode ->
            if (periode.periodeFOMDato != null && infotrygdforespArbuforFom != null) {
            periode.periodeFOMDato.toGregorianCalendar().toZonedDateTime().toLocalDate().isBefore(infotrygdforespArbuforFom)
            } else {
                false
            }
        } ?: false
    }),

    @Description("Hvis sykmeldingsperioden er større enn 1 år")
    SICK_LEAVE_PERIOD_OVER_1_YEAR_NO_HISTORY(1514, Status.MANUAL_PROCESSING, { (infotrygdForesp, healthInformation) ->
        val sMhistorikkArbuforFOM: LocalDate? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.arbufoerFOM?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
        val sMhistorikkArbuforTOM: LocalDate? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.arbufoerTOM?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
        val healthInformationPeriodeFomdato: LocalDate? = healthInformation.aktivitet.periode.firstOrNull()?.periodeFOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
        val healthInformationPeriodeTomdato: LocalDate? = healthInformation.aktivitet.periode.firstOrNull()?.periodeTOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()

        if (healthInformationPeriodeFomdato != null &&
                healthInformationPeriodeTomdato != null &&
                sMhistorikkArbuforFOM == null &&
                sMhistorikkArbuforTOM == null) {
            YEARS.between(healthInformationPeriodeFomdato, healthInformationPeriodeTomdato) >= 1
        } else {
            false
        }
    }),

    @Description("Hvis sykmeldingsperioden er større enn 1 år")
    SICK_LEAVE_PERIOD_OVER_1_YEAR_NO_MAXDATO(1514, Status.MANUAL_PROCESSING, { (infotrygdForesp, healthInformation) ->
        val healthInformationPeriodeFomdato: java.time.LocalDate? = healthInformation.aktivitet.periode.firstOrNull()?.periodeFOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
        val healthInformationPeriodeTomdato: java.time.LocalDate? = healthInformation.aktivitet.periode.firstOrNull()?.periodeTOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
        val sMhistorikkMaxDato: java.time.LocalDate? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.maxDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()

        if (healthInformationPeriodeFomdato != null &&
                healthInformationPeriodeTomdato != null &&
                sMhistorikkMaxDato == null) {
            YEARS.between(healthInformationPeriodeFomdato, healthInformationPeriodeTomdato) >= 1
        } else {
            false
        }
    }),

    @Description("Hvis sykmeldingsperioden er større enn 1 år")
    SICK_LEAVE_PERIOD_OVER_1_YEAR_MAXDATO(1514, Status.MANUAL_PROCESSING, { (infotrygdForesp, healthInformation) ->
        val healthInformationPeriodeFomdato: java.time.LocalDate? = healthInformation.aktivitet.periode.firstOrNull()?.periodeFOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
        val healthInformationPeriodeTomdato: java.time.LocalDate? = healthInformation.aktivitet.periode.firstOrNull()?.periodeTOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
        val sMhistorikkMaxDato: java.time.LocalDate? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.maxDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()

        if (healthInformationPeriodeFomdato != null &&
                healthInformationPeriodeTomdato != null &&
                sMhistorikkMaxDato != null) {
            YEARS.between(healthInformationPeriodeFomdato, healthInformationPeriodeTomdato) >= 1 &&
                    sMhistorikkMaxDato.isBefore(healthInformationPeriodeTomdato)
        } else {
            false
        }
    }),

    @Description("Hvis sykmeldingen er forlengelse av registrert sykepengehistorikk fra annet kontor så medlingen gå til manuell behandling slik at  saksbehandler kan registrere sykepengetilfellet på ny identdato og  send oppgave til Nav forvaltning for registrering av inntektsopplysninger")
    SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1(1515, Status.MANUAL_PROCESSING, { (infotrygdForesp, healthInformation) ->
        val infotrygdforespHistArbuforFom: LocalDate? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.arbufoerFOM?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
        val healthInformationPeriodeFomdato: LocalDate? = healthInformation.aktivitet?.periode?.firstOrNull()?.periodeFOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
        val infotrygdforespHistUtbetalingTOM: LocalDate? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.utbetTOM?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
        val infotrygdforespFriskKode: String? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.friskKode
        val infotrygdforespDiagnoseKode: String? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.hovedDiagnosekode

        if (infotrygdforespHistArbuforFom != null &&
                healthInformationPeriodeFomdato != null &&
                !infotrygdforespDiagnoseKode.isNullOrBlank() &&
                !infotrygdforespFriskKode.isNullOrBlank()) {
                    infotrygdforespHistArbuforFom.isBefore(healthInformationPeriodeFomdato) &&
                            infotrygdforespHistUtbetalingTOM?.isAfter(healthInformationPeriodeFomdato) ?: false &&
                            !infotrygdforespDiagnoseKode.equals("000") &&
                            !infotrygdforespFriskKode.equals("H")
        } else {
            false
        }
    }),

    @Description("Hvis sykmeldingen er forlengelse av registrert sykepengehistorikk fra annet kontor så medlingen gå til manuell behandling slik at  saksbehandler kan registrere sykepengetilfellet på ny identdato og  send oppgave til Nav forvaltning for registrering av inntektsopplysninger")
    SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2(1515, Status.MANUAL_PROCESSING, { (infotrygdForesp, healthInformation) ->
        val healthInformationPeriodeFomdato: LocalDate? = healthInformation.aktivitet?.periode?.firstOrNull()?.periodeFOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
        val infotrygdforespHistUtbetalingTOM: LocalDate? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.utbetTOM?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
        val infotrygdforespFriskKode: String? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.friskKode
        val infotrygdforespDiagnoseKode: String? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.hovedDiagnosekode

        if (healthInformationPeriodeFomdato != null &&
                infotrygdforespHistUtbetalingTOM != null &&
                !infotrygdforespFriskKode.isNullOrBlank() &&
                !infotrygdforespDiagnoseKode.isNullOrBlank()) {
                infotrygdforespHistUtbetalingTOM.plusDays(1) == healthInformationPeriodeFomdato &&
                        infotrygdforespDiagnoseKode != "000" &&
                        infotrygdforespFriskKode != "H"
        } else {
            false
        }
    }),

    @Description("Hvis sykmeldingen er forlengelse av registrert sykepengehistorikk fra annet kontor så medlingen gå til manuell behandling slik at  saksbehandler kan registrere sykepengetilfellet på ny identdato og  send oppgave til Nav forvaltning for registrering av inntektsopplysninger")
    SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3(1515, Status.MANUAL_PROCESSING, { (infotrygdForesp, healthInformation) ->
        val healthInformationPeriodeFomdato: LocalDate? = healthInformation.aktivitet?.periode?.firstOrNull()?.periodeFOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
        val infotrygdforespHistUtbetalingTOM: LocalDate? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.utbetTOM?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
        val infotrygdforespFriskKode: String? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.friskKode
        val infotrygdforespDiagnoseKode: String? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.hovedDiagnosekode

        if (healthInformationPeriodeFomdato != null &&
                infotrygdforespHistUtbetalingTOM != null &&
                !infotrygdforespFriskKode.isNullOrBlank() &&
                !infotrygdforespDiagnoseKode.isNullOrBlank()) {
                    infotrygdforespHistUtbetalingTOM.isBefore(healthInformationPeriodeFomdato) &&
                            infotrygdforespHistUtbetalingTOM.plusDays(3).isAfter(healthInformationPeriodeFomdato) &&
                            infotrygdforespHistUtbetalingTOM.dayOfWeek == java.time.DayOfWeek.FRIDAY &&
                            healthInformationPeriodeFomdato.dayOfWeek in kotlin.arrayOf(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY, java.time.DayOfWeek.MONDAY) &&
                            infotrygdforespDiagnoseKode != "000" &&
                            infotrygdforespFriskKode != "H"
        } else {
            false
        }
    }),

    @Description("Hvis ny friskmeldingsdato er mindre enn arbuforTOM registrert i Infotrygd")
    NEW_CLEAN_BILL_DATE_BEFORE_ARBUFORTOM(1516, Status.MANUAL_PROCESSING, { (infotrygdForesp) ->
        val sMhistorikkfriskmeldtDato: LocalDate? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.friskmeldtDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
        val sMhistorikkArbuforTom: LocalDate? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.arbufoerTOM?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()

        if (sMhistorikkfriskmeldtDato != null && sMhistorikkArbuforTom != null) {
            sMhistorikkfriskmeldtDato.isBefore(sMhistorikkArbuforTom)
        } else {
            false
        }
    }),

    @Description("Hvis ny friskmeldingsdato er mindre enn utbetalingTOM registrert i Infotrygd")
    NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT(1517, Status.MANUAL_PROCESSING, { (infotrygdForesp) ->
        val sMhistorikkfriskmeldtDato: LocalDate? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.friskmeldtDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
        val sMhistorikkutbetTOM: LocalDate? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.utbetTOM?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()

        when (sMhistorikkutbetTOM) {
            null -> false
            else -> sMhistorikkfriskmeldtDato?.isBefore(sMhistorikkutbetTOM) ?: false
        }
    }),

    @Description("Hvis ny friskmeldingsdato er tidligere enn registrert friskmeldingsdato i Infotrygd")
    NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE(1518, Status.MANUAL_PROCESSING, { (infotrygdForesp) ->
        val newfriskmeldtDato: LocalDate? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.friskmeldtDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
        val secoundfriskmeldtDato: LocalDate? = infotrygdForesp.sMhistorikk?.sykmelding?.drop(1)?.firstOrNull()?.periode?.friskmeldtDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()

        when (secoundfriskmeldtDato) {
            null -> false
            else -> newfriskmeldtDato?.isAfter(secoundfriskmeldtDato) ?: false
        }
    }),

    @Description("Hvis en sykmelding fra manuellterapeut overstiger 12 uker regnet fra første sykefraværsdag")
    DOCTOR_IS_KI_AND_OVER_12_WEEKS(1519, Status.INVALID, { (infotrygdForesp, healthInformation) ->
        val samhandlerType = infotrygdForesp.behandlerInfo?.behandler?.firstOrNull()?.mottakerKode
        val healthInformationPeriodeFomdato: LocalDate? = healthInformation.aktivitet.periode.firstOrNull()?.periodeFOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
        val healthInformationPeriodeTomdato: LocalDate? = healthInformation.aktivitet.periode.firstOrNull()?.periodeTOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()

        samhandlerType == TypeMottakerKode.KI && DAYS.between(healthInformationPeriodeFomdato, healthInformationPeriodeTomdato) > 84
    }),

    @Description("Hvis en sykmelding fra manuellterapeut overstiger 12 uker regnet fra første sykefraværsdag")
    DOCTOR_IS_MT_AND_OVER_12_WEEKS(1520, Status.INVALID, { (infotrygdForesp, healthInformation) ->
        val samhandlerType = infotrygdForesp.behandlerInfo?.behandler?.firstOrNull()?.mottakerKode
        val healthInformationPeriodeFomdato: LocalDate? = healthInformation.aktivitet.periode.firstOrNull()?.periodeFOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
        val healthInformationPeriodeTomdato: LocalDate? = healthInformation.aktivitet.periode.firstOrNull()?.periodeTOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()

        samhandlerType == TypeMottakerKode.MT && DAYS.between(healthInformationPeriodeFomdato, healthInformationPeriodeTomdato) > 84
    }),

    @Description("Hvis uføregrad er endret")
    DIABILITY_GRADE_CANGED(1530, Status.MANUAL_PROCESSING, { (infotrygdForesp, healthInformation) ->
        val disabilityGradeIT: kotlin.Int? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.ufoeregrad?.toInt()
        val healthInformationDisabilityGrade: Int? = healthInformation.aktivitet.periode.firstOrNull()?.gradertSykmelding?.sykmeldingsgrad
        val sMhistorikkArbuforFOM: LocalDate? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.arbufoerFOM?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
        val healthInformationPeriodeFOMDato: LocalDate? = healthInformation.aktivitet.periode.firstOrNull()?.periodeFOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
        val healthInformationPeriodeTOMDato: LocalDate? = healthInformation.aktivitet.periode.firstOrNull()?.periodeTOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()

        disabilityGradeIT != healthInformationDisabilityGrade &&
                sMhistorikkArbuforFOM?.isAfter(healthInformationPeriodeFOMDato) ?: false &&
                sMhistorikkArbuforFOM?.isBefore(healthInformationPeriodeTOMDato) ?: false
    }),

    @Description("Hvis forlengelse utover registrert tiltak FA tiltak")
    EXTANION_OVER_FA(1544, Status.MANUAL_PROCESSING, { (infotrygdForesp, healthInformation) ->
        val sMhistorikkTilltakTypeFA: kotlin.Boolean? = infotrygdForesp.sMhistorikk?.sykmelding?.any { sykemelding ->
            sykemelding.historikk.firstOrNull()?.tilltak?.type == "FA"
        } ?: false

        val healthInformationPeriodeFomdato: LocalDate? = healthInformation.aktivitet.periode.firstOrNull()?.periodeFOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
        val sMhistorikkTilltakTypeFATomDato: LocalDate? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.historikk?.firstOrNull { historikk ->
            historikk?.tilltak?.type == "FA"
        }?.tilltak?.tom?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()

        if (sMhistorikkTilltakTypeFA != null &&
                healthInformationPeriodeFomdato != null &&
                sMhistorikkTilltakTypeFATomDato != null) {
            sMhistorikkTilltakTypeFA && healthInformationPeriodeFomdato.isAfter(sMhistorikkTilltakTypeFATomDato)
        } else {
            false
        }
    }),

    @Description("Hvis personen har stanskode DØD i Infotrygd")
    PATIENT_DEAD(1545, Status.MANUAL_PROCESSING, { (infotrygdForesp) ->
        infotrygdForesp.sMhistorikk?.sykmelding?.any { sykmelding ->
            sykmelding?.periode?.stans == "DØD"
        } ?: false
    }),

    @Description("Personen har flyttet ( stanskode FL i Infotrygd)")
    PERSON_MOVING_KODE_FL(1546, Status.MANUAL_PROCESSING, { (infotrygdForesp) ->
        infotrygdForesp.sMhistorikk?.sykmelding?.any { sykmelding ->
            sykmelding?.periode?.stans == "FL"
        } ?: false
        }),

    @Description("Hvis perioden er avsluttet-død(AD)")
    PERIOD_ENDED_DEAD(1548, Status.MANUAL_PROCESSING, { (infotrygdForesp) ->
        infotrygdForesp.sMhistorikk?.sykmelding?.any { sykemelding ->
            sykemelding?.periode?.stans == "AD"
        } ?: false
    }),

    @Description("Hvis perioden er avsluttet (AA)")
    PERIOD_FOR_AA_ENDED(1549, Status.MANUAL_PROCESSING, { (infotrygdForesp) ->
        when (infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.stans) {
            null -> false
            else -> infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.stans == "AA"
        }
    }),

    @Description("Hvis perioden er avsluttet-frisk (AF)")
    PERIOD_IS_AF(1550, Status.MANUAL_PROCESSING, { (infotrygdForesp) ->
        when (infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.stans) {
            null -> false
            else -> infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.stans == "AF"
        }
    }),

    @Description("Hvis maks sykepenger er utbetalt")
    MAX_SICK_LEAVE_PAYOUT(1551, Status.MANUAL_PROCESSING, { (infotrygdForesp) ->
        infotrygdForesp.sMhistorikk?.sykmelding?.any { sykemelding ->
            sykemelding?.periode?.stans == "MAX"
        } ?: false
    }),

    @Description("Hvis det er registrert avslag i IT")
    REFUSAL_IS_REGISTERED(1552, Status.MANUAL_PROCESSING, { (infotrygdForesp) ->
        infotrygdForesp.sMhistorikk?.sykmelding?.any { sykemelding ->
            !sykemelding?.periode?.avslag.isNullOrEmpty()
        } ?: false
    }),

    @Description("Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd")
    ERROR_FROM_IT(1591, Status.MANUAL_PROCESSING, { (infotrygdForesp) ->
        val hovedStatusKodemelding: Int? = infotrygdForesp.hovedStatus.kodeMelding.toIntOrNull()
        val sMhistorikktStatusKodemelding: Int? = infotrygdForesp.sMhistorikk?.status?.kodeMelding?.toIntOrNull()
        val parallelleYtelserStatusKodemelding: kotlin.Int? = infotrygdForesp.parallelleYtelser?.status?.kodeMelding?.toIntOrNull()
        val diagnoseOKUttrekkStatusKodemelding: kotlin.Int? = infotrygdForesp.diagnosekodeOK?.status?.kodeMelding?.toIntOrNull()
        val pasientUttrekkStatusKodemelding: kotlin.Int? = infotrygdForesp.pasient?.status?.kodeMelding?.toIntOrNull()

        hovedStatusKodemelding ?: 0 > 4 ||
                sMhistorikktStatusKodemelding ?: 0 > 4 ||
                parallelleYtelserStatusKodemelding ?: 0 > 4 ||
                diagnoseOKUttrekkStatusKodemelding ?: 0 > 4 ||
                pasientUttrekkStatusKodemelding ?: 0 > 4
    }),
}

/* TODO only count work days
fun workdaysBetween(a: LocalDate, b: LocalDate): Int = (1..(DAYS.between(a, b) - 1))
        .map { a.plusDays(it) }
        .filter { it.dayOfWeek !in arrayOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }
        .count()
*/