package no.nav.syfo.rules

import java.time.DayOfWeek
import java.time.LocalDate
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.syfo.daysBetween
import no.nav.syfo.model.Periode
import no.nav.syfo.model.Status

enum class ValidationRuleChain(
    override val ruleId: Int?,
    override val status: Status,
    override val messageForUser: String,
    override val messageForSender: String,
    override val predicate: (RuleData<InfotrygdForesp>) -> Boolean
) : Rule<RuleData<InfotrygdForesp>> {

    @Description("Hvis gradert sykmelding og reisetilskudd er oppgitt for samme periode")
    GRADUAL_SYKMELDING_COMBINED_WITH_TRAVEL(
            1250,
            Status.MANUAL_PROCESSING,
            "Hvis gradert sykmelding og reisetilskudd er oppgitt for samme periode",
            "Hvis gradert sykmelding og reisetilskudd er oppgitt for samme periode",
            { (sykmelding, _) ->
        sykmelding.perioder.any { it.gradert != null && it.gradert?.reisetilskudd ?: false }
    }),

    @Description("Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.")
    NUMBER_OF_TREATMENT_DAYS_SET(
            1260,
            Status.MANUAL_PROCESSING,
            "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
            "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
            { (sykmelding, _) ->
        sykmelding.perioder.any { it.behandlingsdager != null }
    }),

    @Description("Hvis sykmeldingen angir og er gradert reisetilskudd går meldingen til manuell behandling.")
    GRADERT_TRAVEL_SUBSIDY_SPECIFIED(
            1270,
            Status.MANUAL_PROCESSING,
            "Hvis sykmeldingen angir reisetilskudd går meldingen til manuell behandling.",
            "Hvis sykmeldingen angir reisetilskudd går meldingen til manuell behandling.",
            { (sykmelding, _) ->
        sykmelding.perioder.any { it.gradert?.reisetilskudd ?: false }
    }),

    @Description("Hvis sykmeldingen angir reisetilskudd går meldingen til manuell behandling.")
    TRAVEL_SUBSIDY_SPECIFIED(
            1270,
            Status.MANUAL_PROCESSING,
            "Hvis sykmeldingen angir reisetilskudd går meldingen til manuell behandling.",
            "Hvis sykmeldingen angir reisetilskudd går meldingen til manuell behandling.",
            { (sykmelding, _) ->
                sykmelding.perioder.any { it.reisetilskudd }
            }),

    @Description("Hvis pasienten ikke finnes i infotrygd")
    PATIENT_NOT_IN_IP(
            1501,
            Status.MANUAL_PROCESSING,
            "Hvis pasienten ikke finnes i infotrygd",
            "Hvis pasienten ikke finnes i infotrygd",
            { (_, infotrygdForesp) ->
        infotrygdForesp.pasient?.isFinnes != null && !infotrygdForesp.pasient.isFinnes
    }),

    @Description("Hvis delvis sammenfallende sykmeldingsperiode er registrert i Infotrygd")
    PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE(
            1513,
            Status.MANUAL_PROCESSING,
            "Hvis delvis sammenfallende sykmeldingsperiode er registrert i Infotrygd",
            "Hvis delvis sammenfallende sykmeldingsperiode er registrert i Infotrygd",
            { (sykmelding, infotrygdForesp) ->
                infotrygdForesp.sMhistorikk?.sykmelding != null &&
                infotrygdForesp.sMhistorikk.sykmelding.sortedTOMDate().lastOrNull() != null &&
                sykmelding.perioder.sortedPeriodeTOMDate().lastOrNull() != null &&
                sykmelding.perioder.sortedPeriodeTOMDate().last().isBefore(infotrygdForesp.sMhistorikk.sykmelding.sortedTOMDate().last())
    }),

    @Description("Hvis sykmeldingen er forlengelse av registrert sykepengehistorikk fra annet kontor så medlingen gå til manuell behandling slik at saksbehandler kan registrere sykepengetilfellet på ny identdato og  send oppgave til Nav forvaltning for registrering av inntektsopplysninger")
    SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1(
            1515,
            Status.MANUAL_PROCESSING,
            "Hvis sykmeldingen er forlengelse av registrert sykepengehistorikk fra annet kontor så medlingen gå til manuell behandling slik at saksbehandler kan registrere sykepengetilfellet på ny identdato og  send oppgave til Nav forvaltning for registrering av inntektsopplysninger",
            "Hvis sykmeldingen er forlengelse av registrert sykepengehistorikk fra annet kontor så medlingen gå til manuell behandling slik at saksbehandler kan registrere sykepengetilfellet på ny identdato og  send oppgave til Nav forvaltning for registrering av inntektsopplysninger",
            { (sykmelding, infotrygdForesp) ->
        infotrygdForesp.sMhistorikk?.sykmelding?.sortedSMInfos()?.lastOrNull()?.periode?.arbufoerFOM != null &&
        sykmelding.perioder.sortedPeriodeFOMDate().lastOrNull() != null &&
        !infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode?.friskKode.isNullOrBlank() &&
        !infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode?.hovedDiagnosekode.isNullOrBlank() &&
        infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.arbufoerFOM.isBefore(
                sykmelding.perioder.sortedPeriodeFOMDate().last()
        ) && infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode?.utbetTOM != null &&
        infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.utbetTOM.isAfter(
                sykmelding.perioder.sortedPeriodeFOMDate().last()
        ) && infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.hovedDiagnosekode != "000" &&
                infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.friskKode != "H"
    }),

    @Description("Hvis sykmeldingen er forlengelse av registrert sykepengehistorikk fra annet kontor så medlingen gå til manuell behandling slik at  saksbehandler kan registrere sykepengetilfellet på ny identdato og  send oppgave til Nav forvaltning for registrering av inntektsopplysninger")
    SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2(
            1515,
            Status.MANUAL_PROCESSING,
            "Hvis sykmeldingen er forlengelse av registrert sykepengehistorikk fra annet kontor så medlingen gå til manuell behandling slik at  saksbehandler kan registrere sykepengetilfellet på ny identdato og  send oppgave til Nav forvaltning for registrering av inntektsopplysninger",
            "Hvis sykmeldingen er forlengelse av registrert sykepengehistorikk fra annet kontor så medlingen gå til manuell behandling slik at  saksbehandler kan registrere sykepengetilfellet på ny identdato og  send oppgave til Nav forvaltning for registrering av inntektsopplysninger",
            { (sykmelding, infotrygdForesp) ->
        infotrygdForesp.sMhistorikk?.sykmelding?.sortedSMInfos()?.lastOrNull() != null &&
        !infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode?.friskKode.isNullOrBlank() &&
        !infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode?.hovedDiagnosekode.isNullOrBlank() &&
        infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode?.utbetTOM != null &&
        infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.utbetTOM.plusDays(1) ==
        sykmelding.perioder.sortedPeriodeFOMDate().last() &&
        infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.hovedDiagnosekode != "000" &&
        infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.friskKode != "H"
    }),

    @Description("Hvis sykmeldingen er forlengelse av registrert sykepengehistorikk fra annet kontor så medlingen gå til manuell behandling slik at  saksbehandler kan registrere sykepengetilfellet på ny identdato og send oppgave til Nav forvaltning for registrering av inntektsopplysninger")
    SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3(
            1515,
            Status.MANUAL_PROCESSING,
            "Hvis sykmeldingen er forlengelse av registrert sykepengehistorikk fra annet kontor så medlingen gå til manuell behandling slik at  saksbehandler kan registrere sykepengetilfellet på ny identdato og send oppgave til Nav forvaltning for registrering av inntektsopplysninger",
            "Hvis sykmeldingen er forlengelse av registrert sykepengehistorikk fra annet kontor så medlingen gå til manuell behandling slik at  saksbehandler kan registrere sykepengetilfellet på ny identdato og send oppgave til Nav forvaltning for registrering av inntektsopplysninger",
            { (sykmelding, infotrygdForesp) ->
        sykmelding.perioder.sortedPeriodeFOMDate().lastOrNull() != null &&
        infotrygdForesp.sMhistorikk?.sykmelding?.sortedSMInfos()?.lastOrNull()?.periode?.utbetTOM != null &&
        !infotrygdForesp.sMhistorikk?.sykmelding?.sortedSMInfos()?.lastOrNull()?.periode?.friskKode.isNullOrBlank() &&
        !infotrygdForesp.sMhistorikk?.sykmelding?.sortedSMInfos()?.lastOrNull()?.periode?.hovedDiagnosekode.isNullOrBlank() &&
        infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.utbetTOM.isBefore(
                        sykmelding.perioder.sortedPeriodeFOMDate().last()) &&
        infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.utbetTOM.plusDays(3).isAfter(
                        sykmelding.perioder.sortedPeriodeFOMDate().last()) &&
        infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.utbetTOM.dayOfWeek >= java.time.DayOfWeek.FRIDAY &&
        sykmelding.perioder.sortedPeriodeFOMDate().last().dayOfWeek in arrayOf(
                java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY, java.time.DayOfWeek.MONDAY) &&
        infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.hovedDiagnosekode != "000" &&
        infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.friskKode != "H"
    }),
/* TODO fjerne regel viss den ikkje er nødvendig
    @Description("Hvis ny friskmeldingsdato er mindre enn arbuforTOM registrert i Infotrygd")
    NEW_CLEAN_BILL_DATE_BEFORE_ARBUFORTOM(
            1516,
            Status.MANUAL_PROCESSING,
            "Hvis ny friskmeldingsdato er mindre enn arbuforTOM registrert i Infotrygd",
            "Hvis ny friskmeldingsdato er mindre enn arbuforTOM registrert i Infotrygd",
            { (sykmelding, infotrygdForesp) ->
                sykmelding.prognose?.arbeidsforEtterPeriode != null &&
                sykmelding.prognose?.arbeidsforEtterPeriode ?: false &&
                infotrygdForesp.sMhistorikk?.sykmelding != null &&
                infotrygdForesp.sMhistorikk?.sykmelding?.sortedSMInfos()?.lastOrNull()?.periode?.arbufoerTOM != null &&
                sykmelding.perioder.sortedPeriodeTOMDate().lastOrNull() != null && (
                sykmelding.perioder.sortedPeriodeTOMDate().last() == (infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.arbufoerTOM) ||
                sykmelding.perioder.sortedPeriodeTOMDate().last().isBefore(infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.arbufoerTOM))
    }),
 */

    @Description("Hvis ny friskmeldingsdato er mindre enn utbetalingTOM registrert i Infotrygd")
    NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT(
            1517,
            Status.MANUAL_PROCESSING,
            "Hvis ny friskmeldingsdato er mindre enn utbetalingTOM registrert i Infotrygd",
            "Hvis ny friskmeldingsdato er mindre enn utbetalingTOM registrert i Infotrygd",
            { (sykmelding, infotrygdForesp) ->
                sykmelding.prognose?.arbeidsforEtterPeriode != null &&
                sykmelding.prognose?.arbeidsforEtterPeriode ?: false &&
                infotrygdForesp.sMhistorikk?.sykmelding != null &&
                sykmelding.perioder.sortedPeriodeTOMDate().lastOrNull() != null &&
                infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().lastOrNull()?.periode?.utbetTOM != null &&
                sykmelding.perioder.sortedPeriodeTOMDate().last().isBefore(
                infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.utbetTOM)
    }),

    @Description("Hvis ny friskmeldingsdato er tidligere enn registrert friskmeldingsdato i Infotrygd")
    NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE(
            1518,
            Status.MANUAL_PROCESSING,
            "Hvis ny friskmeldingsdato er tidligere enn registrert friskmeldingsdato i Infotrygd",
            "Hvis ny friskmeldingsdato er tidligere enn registrert friskmeldingsdato i Infotrygd",
            { (sykmelding, infotrygdForesp) ->
        infotrygdForesp.sMhistorikk?.sykmelding?.sortedSMInfos()?.lastOrNull()?.periode?.friskmeldtDato != null &&
                sykmelding.prognose?.arbeidsforEtterPeriode != null &&
                sykmelding.prognose?.arbeidsforEtterPeriode ?: false &&
                sykmelding.perioder.sortedPeriodeTOMDate().lastOrNull() != null &&
                infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().lastOrNull()?.periode?.friskmeldtDato != null &&
                sykmelding.perioder.sortedPeriodeTOMDate().last().isBefore(infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.friskmeldtDato)
    }),

    @Description("Hvis forlengelse utover registrert tiltak FA tiltak")
    EXTANION_OVER_FA(
            1544,
            Status.MANUAL_PROCESSING,
            "Hvis forlengelse utover registrert tiltak FA tiltak",
            "Hvis forlengelse utover registrert tiltak FA tiltak",
            { (sykmelding, infotrygdForesp) ->
        infotrygdForesp.sMhistorikk?.sykmelding != null &&
        sykmelding.perioder.sortedPeriodeFOMDate().lastOrNull() != null &&
        infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().lastOrNull()?.historikk?.sortedSMinfoHistorikk()?.lastOrNull()?.tilltak?.type != null &&
        infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().historikk.sortedSMinfoHistorikk().last().tilltak.type == "FA" &&
        infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().historikk.sortedSMinfoHistorikk().last().tilltak.tom != null &&
        sykmelding.perioder.any { periodA ->
            infotrygdForesp.sMhistorikk.sykmelding.any { sykmeldinger ->
                sykmeldinger.historikk.any { historikk ->
                    historikk?.tilltak != null && historikk.tilltak.type == "FA" && historikk.tilltak.fom in periodA.range() || historikk?.tilltak != null && historikk.tilltak.tom in periodA.range()
                }
            }
        }
    }),

    @Description("Personen har flyttet ( stanskode FL i Infotrygd)")
    PERSON_MOVING_KODE_FL(
            1546,
            Status.MANUAL_PROCESSING,
            "Personen har flyttet ( stanskode FL i Infotrygd)",
            "Personen har flyttet ( stanskode FL i Infotrygd)",
            { (_, infotrygdForesp) ->
        infotrygdForesp.sMhistorikk?.sykmelding?.find {
            it.periode?.arbufoerFOM != null &&
                it.periode.arbufoerFOM == infotrygdForesp.sMhistorikk?.sykmelding?.sortedFOMDate()?.lastOrNull()
            }?.periode?.stans == "FL"
        }),

    @Description("Hvis perioden er avsluttet (AA)")
    PERIOD_FOR_AA_ENDED(
            1549,
            Status.MANUAL_PROCESSING,
            "Hvis perioden er avsluttet (AA)",
            "Hvis perioden er avsluttet (AA)",
            { (sykmelding, infotrygdForesp) ->
        sykmelding.perioder.any {
                    !infotrygdForesp.sMhistorikk?.sykmelding?.sortedSMInfos()?.lastOrNull()?.periode?.stans.isNullOrBlank() &&
                    infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.arbufoerTOM != null &&
                    infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.stans == "AA" &&
                    it.fom.isBefore(infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.arbufoerTOM)
        }
    }),

    @Description("Hvis perioden er avsluttet-frisk (AF)")
    PERIOD_IS_AF(
            1550,
            Status.MANUAL_PROCESSING,
            "Hvis perioden er avsluttet-frisk (AF)",
            "Hvis perioden er avsluttet-frisk (AF)",
            { (sykmelding, infotrygdForesp) ->
        sykmelding.perioder.any {
            !infotrygdForesp.sMhistorikk?.sykmelding?.sortedSMInfos()?.lastOrNull()?.periode?.stans.isNullOrBlank() &&
                    infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.arbufoerTOM != null &&
                    infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.stans == "AF" &&
                    it.fom.isBefore(infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.arbufoerTOM)
        }
    }),

    @Description("Hvis maks sykepenger er utbetalt")
    MAX_SICK_LEAVE_PAYOUT(
            1551,
            Status.MANUAL_PROCESSING,
            "Hvis maks sykepenger er utbetalt",
            "Hvis maks sykepenger er utbetalt",
            { (sykmelding, infotrygdForesp) ->
                sykmelding.perioder.any {
                    !infotrygdForesp.sMhistorikk?.sykmelding?.sortedSMInfos()?.lastOrNull()?.periode?.stans.isNullOrBlank() &&
                        infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.stans == "MAX" &&
                        infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.arbufoerTOM != null &&
                        it.fom.isBefore(infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.arbufoerTOM.plusMonths(6))
                }
    }),

    @Description("Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd")
    ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING(
            1591,
            Status.MANUAL_PROCESSING,
            "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
            "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
            { (_, infotrygdForesp) ->
        infotrygdForesp.hovedStatus?.kodeMelding?.toIntOrNull() != null &&
                infotrygdForesp.hovedStatus.kodeMelding.toInt() > 4
    }),

    @Description("Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd")
    ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING(
            1591,
            Status.MANUAL_PROCESSING,
            "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
            "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
            { (_, infotrygdForesp) ->
        infotrygdForesp.sMhistorikk?.status?.kodeMelding?.toIntOrNull() != null &&
                infotrygdForesp.sMhistorikk.status.kodeMelding.toInt() > 4
    }),

    @Description("Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd")
    ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING(
            1591,
            Status.MANUAL_PROCESSING,
            "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
            "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
            { (_, infotrygdForesp) ->
        infotrygdForesp.parallelleYtelser?.status?.kodeMelding?.toIntOrNull() != null &&
                infotrygdForesp.parallelleYtelser.status.kodeMelding.toInt() > 4
    }),

    @Description("Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd")
    ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING(
            1591,
            Status.MANUAL_PROCESSING,
            "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
            "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
            { (_, infotrygdForesp) ->
        infotrygdForesp.diagnosekodeOK?.status?.kodeMelding?.toIntOrNull() != null &&
                infotrygdForesp.diagnosekodeOK.status.kodeMelding.toInt() > 4
    }),

    @Description("Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd")
    ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING(
            1591,
            Status.MANUAL_PROCESSING,
            "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
            "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
            { (_, infotrygdForesp) ->
        infotrygdForesp.pasient?.status?.kodeMelding?.toIntOrNull() != null &&
                infotrygdForesp.pasient.status.kodeMelding.toInt() > 4
    }),
    @Description("Infotrygd returnerte ikke arbufoerTOM dato på sykmeldings historikken, vi kan ikke automatisk oppdatere Infotrygd")
    ARBEIDUFORETOM_MANGLER(
            1591,
            Status.MANUAL_PROCESSING,
            "Infotrygd returnerte ikke arbufoerTOM dato på sykmeldings historikken, vi kan ikke automatisk oppdatere Infotrygd",
            "Infotrygd returnerte ikke arbufoerTOM dato på sykmeldings historikken, vi kan ikke automatisk oppdatere Infotrygd",
            { (_, infotrygdForesp) ->
                infotrygdForesp.sMhistorikk?.sykmelding != null &&
                infotrygdForesp.sMhistorikk.status.kodeMelding != "04" &&
                infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().lastOrNull()?.periode?.arbufoerTOM == null
            }),
}

private fun List<Periode>.toRange(): ClosedRange<LocalDate> =
        map { it.fom }.sorted().first().rangeTo(map { it.tom }.sorted().first())

private fun List<TypeSMinfo>.findOverlapping(smRange: ClosedRange<LocalDate>): TypeSMinfo? =
        firstOrNull { // Whenever the start of the period is the same
            smRange.start == it.periode.arbufoerFOM
        } ?: firstOrNull { // Whenever it starts before and overlaps the period
            it.periode.arbufoerFOM < smRange.start && it.periode.arbufoerTOM != null && it.periode.arbufoerTOM >= smRange.start
        } ?: firstOrNull { // Whenever the period is within the range
            it.periode.arbufoerFOM > smRange.start && it.periode.arbufoerTOM != null && it.periode.arbufoerTOM <= smRange.endInclusive
        } ?: firstOrNull { // Whenever the period is within the range
            it.periode.arbufoerFOM > smRange.start
        } ?: sortedBy { it.periode.arbufoerFOM }.firstOrNull { // Find the first period that is an extension from the next day
            it.periode.arbufoerTOM != null && it.periode.arbufoerTOM.plusDays(1) == smRange.start
        } ?: firstOrNull { // Whenever its an extension from the next day over a weekend
            it.periode.arbufoerFOM != null && it.periode.arbufoerTOM != null && it.periode.arbufoerFOM.dayOfWeek in arrayOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) &&
                    smRange.start.dayOfWeek in arrayOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY) &&
                    it.periode.arbufoerTOM.plusDays(3) >= smRange.start
        }

fun List<Periode>.sortedPeriodeTOMDate(): List<LocalDate> =
        map { it.tom }.sorted()

fun List<Periode>.sortedPeriodeFOMDate(): List<LocalDate> =
        map { it.fom }.sorted()

fun List<Periode>.sortedSykmeldingPeriodeFOMDate(): List<Periode> =
        sortedBy { it.fom }

fun List<TypeSMinfo>.sortedSMInfos(): List<TypeSMinfo> =
        sortedBy { it.periode.arbufoerTOM }

fun List<TypeSMinfo>.sortedFOMDate(): List<LocalDate> =
        map { it.periode.arbufoerFOM }.filterNotNull().sorted()

fun List<TypeSMinfo>.sortedTOMDate(): List<LocalDate> =
        map { it.periode.arbufoerTOM }.filterNotNull().sorted()

fun List<TypeSMinfo.Historikk>.sortedSMinfoHistorikk(): List<TypeSMinfo.Historikk> =
        sortedBy { it.endringsDato }

fun Periode.range(): ClosedRange<LocalDate> = fom.rangeTo(tom)

fun TypeSMinfo.Historikk.Tilltak.range(): ClosedRange<LocalDate> = fom.rangeTo(tom)

fun Periode.findGrad(): Int =
        if (gradert?.grad != null) {
            gradert!!.grad
        } else {
            100
        }

fun forstegangsSykmelding(infotrygdForesp: InfotrygdForesp, periode: Periode): Boolean {
    val typeSMinfo = infotrygdForesp.sMhistorikk?.sykmelding
            ?.sortedSMInfos()
            ?.lastOrNull()
            ?: return true

    return (infotrygdForesp.sMhistorikk.status.kodeMelding == "04" ||
            (typeSMinfo.periode.arbufoerTOM != null && (typeSMinfo.periode.arbufoerTOM..periode.fom).daysBetween() > 1))
}

fun sammePeriode(infotrygdPeriode: TypeSMinfo.Periode, sykemldingsPeriode: Periode): Boolean {
    return infotrygdPeriode.arbufoerFOM == sykemldingsPeriode.fom && infotrygdPeriode.arbufoerTOM == sykemldingsPeriode.tom
}
