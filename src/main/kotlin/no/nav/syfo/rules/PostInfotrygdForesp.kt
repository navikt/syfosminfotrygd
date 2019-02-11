package no.nav.syfo.rules

import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.Description
import no.nav.syfo.Rule
import no.nav.syfo.model.Status
import java.time.LocalDate

data class RuleData(
    val infotrygdForesp: InfotrygdForesp,
    val healthInformation: HelseOpplysningerArbeidsuforhet
)

enum class ValidationRules(override val ruleId: Int?, override val status: Status, override val predicate: (RuleData) -> Boolean) : Rule<RuleData> {

    @Description("Hvis gradert sykmelding og reisetilskudd er oppgitt for samme periode")
    GRADUAL_SYKMELDING_COMBINED_WITH_TRAVEL(1250, Status.MANUAL_PROCESSING, { (_, healthInformation) ->
        healthInformation.aktivitet.periode.any { it.gradertSykmelding != null && it?.isReisetilskudd ?: false }
    }),

    @Description("Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.")
    NUMBER_OF_TREATMENT_DAYS_SET(1260, Status.MANUAL_PROCESSING, { (_, healthInformation) ->
        healthInformation.aktivitet.periode.any { it.behandlingsdager != null }
    }),

    @Description("Hvis sykmeldingen angir reisetilskudd går meldingen til manuell behandling.")
    TRAVEL_SUBSIDY_SPECIFIED(1270, Status.MANUAL_PROCESSING, { (_, healthInformation) ->
        healthInformation.aktivitet.periode.any { it.isReisetilskudd == true } // Can be null, so use equality
    }),

    @Description("Hvis pasienten ikke finnes i infotrygd")
    PATIENT_NOT_IN_IP(1501, Status.MANUAL_PROCESSING, { (infotrygdForesp) ->
        when (infotrygdForesp.pasient?.isFinnes) {
            null -> false
            else -> !infotrygdForesp.pasient.isFinnes
        }
    }),

    @Description("Hvis delvis sammenfallende sykmeldingsperiode er registrert i Infotrygd")
    PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE(1513, Status.MANUAL_PROCESSING, { (infotrygdForesp, healthInformation) ->
        healthInformation.aktivitet.periode.any { healthInformationPeriods ->
            if (infotrygdForesp.sMhistorikk?.sykmelding != null) {
            infotrygdForesp.sMhistorikk.sykmelding
                    .any { infotrygdForespSykmelding ->
                        if (infotrygdForespSykmelding.periode?.arbufoerFOM != null && infotrygdForespSykmelding.periode?.arbufoerTOM != null) {
                        healthInformationPeriods.periodeFOMDato in infotrygdForespSykmelding.range() || healthInformationPeriods.periodeTOMDato in infotrygdForespSykmelding.range()
                        } else {
                            false
                        }
                        }
            } else {
                false
            }
        }
    }),

    @Description("Hvis sykmeldingen er forlengelse av registrert sykepengehistorikk fra annet kontor så medlingen gå til manuell behandling slik at saksbehandler kan registrere sykepengetilfellet på ny identdato og  send oppgave til Nav forvaltning for registrering av inntektsopplysninger")
    SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1(1515, Status.MANUAL_PROCESSING, { (infotrygdForesp, healthInformation) ->
        val infotrygdforespHistArbuforFom: LocalDate? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.arbufoerFOM
        val healthInformationPeriodeFomdato: LocalDate? = healthInformation.aktivitet?.periode?.firstOrNull()?.periodeFOMDato
        val infotrygdforespHistUtbetalingTOM: LocalDate? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.utbetTOM
        val infotrygdforespFriskKode: String? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.friskKode
        val infotrygdforespDiagnoseKode: String? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.hovedDiagnosekode

        if (infotrygdforespHistArbuforFom != null &&
                healthInformationPeriodeFomdato != null &&
                !infotrygdforespDiagnoseKode.isNullOrBlank() &&
                !infotrygdforespFriskKode.isNullOrBlank()) {
                    infotrygdforespHistArbuforFom.isBefore(healthInformationPeriodeFomdato) &&
                            infotrygdforespHistUtbetalingTOM?.isAfter(healthInformationPeriodeFomdato) ?: false &&
                            infotrygdforespDiagnoseKode != "000" &&
                            infotrygdforespFriskKode != "H"
        } else {
            false
        }
    }),

    @Description("Hvis sykmeldingen er forlengelse av registrert sykepengehistorikk fra annet kontor så medlingen gå til manuell behandling slik at  saksbehandler kan registrere sykepengetilfellet på ny identdato og  send oppgave til Nav forvaltning for registrering av inntektsopplysninger")
    SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2(1515, Status.MANUAL_PROCESSING, { (infotrygdForesp, healthInformation) ->
        val healthInformationPeriodeFomdato: LocalDate? = healthInformation.aktivitet?.periode?.firstOrNull()?.periodeFOMDato
        val infotrygdforespHistUtbetalingTOM: LocalDate? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.utbetTOM
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

    @Description("Hvis sykmeldingen er forlengelse av registrert sykepengehistorikk fra annet kontor så medlingen gå til manuell behandling slik at  saksbehandler kan registrere sykepengetilfellet på ny identdato og send oppgave til Nav forvaltning for registrering av inntektsopplysninger")
    SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3(1515, Status.MANUAL_PROCESSING, { (infotrygdForesp, healthInformation) ->
        val healthInformationPeriodeFomdato: LocalDate? = healthInformation.aktivitet?.periode?.firstOrNull()?.periodeFOMDato
        val infotrygdforespHistUtbetalingTOM: LocalDate? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.utbetTOM
        val infotrygdforespFriskKode: String? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.friskKode
        val infotrygdforespDiagnoseKode: String? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.hovedDiagnosekode

        if (healthInformationPeriodeFomdato != null &&
                infotrygdforespHistUtbetalingTOM != null &&
                !infotrygdforespFriskKode.isNullOrBlank() &&
                !infotrygdforespDiagnoseKode.isNullOrBlank()) {
                    infotrygdforespHistUtbetalingTOM.isBefore(healthInformationPeriodeFomdato) &&
                            infotrygdforespHistUtbetalingTOM.plusDays(3).isAfter(healthInformationPeriodeFomdato) &&
                            infotrygdforespHistUtbetalingTOM.dayOfWeek >= java.time.DayOfWeek.FRIDAY &&
                            healthInformationPeriodeFomdato.dayOfWeek in arrayOf(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY, java.time.DayOfWeek.MONDAY) &&
                            infotrygdforespDiagnoseKode != "000" &&
                            infotrygdforespFriskKode != "H"
        } else {
            false
        }
    }),

    @Description("Hvis ny friskmeldingsdato er mindre enn arbuforTOM registrert i Infotrygd")
    NEW_CLEAN_BILL_DATE_BEFORE_ARBUFORTOM(1516, Status.MANUAL_PROCESSING, { (infotrygdForesp, healthInformation) ->
        val friskmeldtDato: LocalDate? = healthInformation.aktivitet.periode.sortedTOMDate().lastOrNull()
        val sMhistorikkArbuforTom: LocalDate? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.arbufoerTOM

        if (healthInformation.prognose?.isArbeidsforEtterEndtPeriode != null && healthInformation.prognose.isArbeidsforEtterEndtPeriode && sMhistorikkArbuforTom != null && friskmeldtDato != null) {
            friskmeldtDato.isBefore(sMhistorikkArbuforTom)
        } else {
            false
        }
    }),

    @Description("Hvis ny friskmeldingsdato er mindre enn utbetalingTOM registrert i Infotrygd")
    NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT(1517, Status.MANUAL_PROCESSING, { (infotrygdForesp, healthInformation) ->
        val friskmeldtDato: LocalDate? = healthInformation.aktivitet.periode.sortedTOMDate().lastOrNull()
        val sMhistorikkutbetTOM: LocalDate? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.utbetTOM

        if (sMhistorikkutbetTOM != null && healthInformation.prognose?.isArbeidsforEtterEndtPeriode != null && healthInformation.prognose.isArbeidsforEtterEndtPeriode) {
            friskmeldtDato?.isBefore(sMhistorikkutbetTOM) ?: false
        } else {
            false
        }
    }),

    @Description("Hvis ny friskmeldingsdato er tidligere enn registrert friskmeldingsdato i Infotrygd")
    NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE(1518, Status.MANUAL_PROCESSING, { (infotrygdForesp, healthInformation) ->
        val friskmeldtDato: LocalDate? = healthInformation.aktivitet.periode.sortedTOMDate().lastOrNull()
        val newfriskmeldtDato: LocalDate? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.friskmeldtDato

        if (newfriskmeldtDato != null && healthInformation.prognose?.isArbeidsforEtterEndtPeriode != null && healthInformation.prognose.isArbeidsforEtterEndtPeriode && friskmeldtDato != null) {
            friskmeldtDato.isBefore(newfriskmeldtDato)
        } else {
            false
        }
    }),

    @Description("Hvis forlengelse utover registrert tiltak FA tiltak")
    EXTANION_OVER_FA(1544, Status.MANUAL_PROCESSING, { (infotrygdForesp, healthInformation) ->
        val sMhistorikkTilltakTypeFA: kotlin.Boolean? = infotrygdForesp.sMhistorikk?.sykmelding?.any { sykemelding ->
            sykemelding.historikk.firstOrNull()?.tilltak?.type == "FA"
        } ?: false

        val healthInformationPeriodeFomdato: LocalDate? = healthInformation.aktivitet.periode.firstOrNull()?.periodeFOMDato
        val sMhistorikkTilltakTypeFATomDato: LocalDate? = infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.historikk?.firstOrNull { historikk ->
            historikk?.tilltak?.type == "FA"
        }?.tilltak?.tom

        if (sMhistorikkTilltakTypeFA != null &&
                healthInformationPeriodeFomdato != null &&
                sMhistorikkTilltakTypeFATomDato != null) {
            sMhistorikkTilltakTypeFA && healthInformationPeriodeFomdato.isAfter(sMhistorikkTilltakTypeFATomDato)
        } else {
            false
        }
    }),

    @Description("Personen har flyttet ( stanskode FL i Infotrygd)")
    PERSON_MOVING_KODE_FL(1546, Status.MANUAL_PROCESSING, { (infotrygdForesp) ->
        infotrygdForesp.sMhistorikk?.sykmelding?.find {
            if (it.periode?.arbufoerFOM != null) {
                it.periode.arbufoerFOM == infotrygdForesp.sMhistorikk?.sykmelding?.sortedFOMDate()?.lastOrNull()
            } else {
                false
            }
            }?.periode?.stans == "FL"
        }),

    @Description("Hvis perioden er avsluttet (AA)")
    PERIOD_FOR_AA_ENDED(1549, Status.MANUAL_PROCESSING, { (infotrygdForesp) ->
        infotrygdForesp.sMhistorikk?.sykmelding?.find {
            if (it.periode?.arbufoerFOM != null) {
                it.periode.arbufoerFOM == infotrygdForesp.sMhistorikk?.sykmelding?.sortedFOMDate()?.lastOrNull()
            } else {
                false
            }
        }?.periode?.stans == "AA"
    }),

    @Description("Hvis perioden er avsluttet-frisk (AF)")
    PERIOD_IS_AF(1550, Status.MANUAL_PROCESSING, { (infotrygdForesp) ->
        infotrygdForesp.sMhistorikk?.sykmelding?.find {
            if (it.periode?.arbufoerFOM != null && infotrygdForesp.sMhistorikk?.sykmelding?.sortedFOMDate()?.last() != null) {
                it.periode.arbufoerFOM == infotrygdForesp.sMhistorikk?.sykmelding?.sortedFOMDate()?.last()
            } else {
                false
            }
        }?.periode?.stans == "AF"
    }),

    @Description("Hvis maks sykepenger er utbetalt")
    MAX_SICK_LEAVE_PAYOUT(1551, Status.MANUAL_PROCESSING, { (infotrygdForesp) ->
        infotrygdForesp.sMhistorikk?.sykmelding?.firstOrNull()?.periode?.stans == "MAX"
        // TODO korte ned sykmeldingen til maks dato??
    }),

    @Description("Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd")
    ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING(1591, Status.MANUAL_PROCESSING, { (infotrygdForesp) ->
        val hovedStatusKodemelding: Int? = infotrygdForesp.hovedStatus?.kodeMelding?.toIntOrNull()
        when (hovedStatusKodemelding) {
            null -> false
            else -> hovedStatusKodemelding > 4
        }
    }),

    @Description("Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd")
    ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING(1591, Status.MANUAL_PROCESSING, { (infotrygdForesp) ->
        val sMhistorikktStatusKodemelding: Int? = infotrygdForesp.sMhistorikk?.status?.kodeMelding?.toIntOrNull()

        when (sMhistorikktStatusKodemelding) {
            null -> false
            else -> sMhistorikktStatusKodemelding > 4
        }
    }),

    @Description("Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd")
    ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING(1591, Status.MANUAL_PROCESSING, { (infotrygdForesp) ->
        val parallelleYtelserStatusKodemelding: Int? = infotrygdForesp.parallelleYtelser?.status?.kodeMelding?.toIntOrNull()

        when (parallelleYtelserStatusKodemelding) {
            null -> false
            else -> parallelleYtelserStatusKodemelding > 4
        }
    }),

    @Description("Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd")
    ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING(1591, Status.MANUAL_PROCESSING, { (infotrygdForesp) ->
        val diagnoseOKUttrekkStatusKodemelding: Int? = infotrygdForesp.diagnosekodeOK?.status?.kodeMelding?.toIntOrNull()

        when (diagnoseOKUttrekkStatusKodemelding) {
            null -> false
            else -> diagnoseOKUttrekkStatusKodemelding > 4
        }
    }),

    @Description("Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd")
    ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING(1591, Status.MANUAL_PROCESSING, { (infotrygdForesp) ->
        val pasientUttrekkStatusKodemelding: Int? = infotrygdForesp.pasient?.status?.kodeMelding?.toIntOrNull()

        when (pasientUttrekkStatusKodemelding) {
            null -> false
            else -> pasientUttrekkStatusKodemelding > 4
        }
    })
}

fun TypeSMinfo.range(): ClosedRange<LocalDate> =
        periode.arbufoerFOM.rangeTo(periode.arbufoerTOM)

fun List<HelseOpplysningerArbeidsuforhet.Aktivitet.Periode>.sortedTOMDate(): List<LocalDate> =
        map { it.periodeTOMDato }.sorted()

fun List<TypeSMinfo>.sortedFOMDate(): List<LocalDate> =
        map { it.periode.arbufoerFOM }.sorted()
