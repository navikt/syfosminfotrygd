package no.nav.syfo.rules

import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.syfo.model.Periode
import no.nav.syfo.model.Status
import java.time.LocalDate

enum class ValidationRuleChain(
    override val ruleId: Int?,
    override val status: Status,
    override val messageForUser: String,
    override val messageForSender: String,
    override val predicate: (RuleData<InfotrygdForesp>) -> Boolean
) : Rule<RuleData<InfotrygdForesp>> {

    @Description("Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.")
    NUMBER_OF_TREATMENT_DAYS_SET(
        1260,
        Status.MANUAL_PROCESSING,
        "Hvis behandlingsdager er angitt sendes sykmeldingen til manuell behandling.",
        "Hvis behandlingsdager er angitt sendes sykmeldingen til manuell behandling.",
        { (sykmelding, _) ->
            sykmelding.perioder.any { it.behandlingsdager != null }
        }
    ),

    @Description("Hvis sykmeldingen angir og er gradert reisetilskudd går meldingen til manuell behandling.")
    GRADERT_REISETILSKUDD_ER_OPPGITT(
        1270,
        Status.MANUAL_PROCESSING,
        "Hvis reisetilskudd er angitt sendes sykmeldingen til manuell behandling.",
        "Hvis reisetilskudd er angitt sendes sykmeldingen til manuell behandling.",
        { (sykmelding, _) ->
            sykmelding.perioder.any { it.gradert?.reisetilskudd ?: false }
        }
    ),

    @Description("Hvis sykmeldingen angir reisetilskudd går meldingen til manuell behandling.")
    TRAVEL_SUBSIDY_SPECIFIED(
        1270,
        Status.MANUAL_PROCESSING,
        "Hvis reisetilskudd er angitt sendes sykmeldingen til manuell behandling.",
        "Hvis reisetilskudd er angitt sendes sykmeldingen til manuell behandling.",
        { (sykmelding, _) ->
            sykmelding.perioder.any { it.reisetilskudd }
        }
    ),

    @Description("Hvis pasienten ikke finnes i infotrygd")
    PATIENT_NOT_IN_IP(
        1501,
        Status.MANUAL_PROCESSING,
        "Pasienten finnes ikke i Infotrygd",
        "Pasienten finnes ikke i Infotrygd",
        { (_, infotrygdForesp) ->
            infotrygdForesp.pasient?.isFinnes != null && !infotrygdForesp.pasient.isFinnes
        }
    ),

    @Description("Hvis delvis sammenfallende sykmeldingsperiode er registrert i Infotrygd")
    PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE(
        1513,
        Status.MANUAL_PROCESSING,
        "Delvis sammenfallende sykmeldingsperiode er registrert i Infotrygd",
        "Delvis sammenfallende sykmeldingsperiode er registrert i Infotrygd",
        { (sykmelding, infotrygdForesp) ->
            infotrygdForesp.sMhistorikk?.sykmelding != null &&
                infotrygdForesp.sMhistorikk.sykmelding.sortedTOMDate().lastOrNull() != null &&
                infotrygdForesp.sMhistorikk.sykmelding.sortedFOMDate().firstOrNull() != null &&
                sykmelding.perioder.sortedPeriodeFOMDate().firstOrNull() != null &&
                sykmelding.perioder.sortedPeriodeTOMDate().lastOrNull() != null &&
                (
                    sykmelding.perioder.sortedPeriodeFOMDate().first().isBefore(infotrygdForesp.sMhistorikk.sykmelding.sortedFOMDate().first()) ||
                        sykmelding.perioder.sortedPeriodeTOMDate().last().isBefore(infotrygdForesp.sMhistorikk.sykmelding.sortedTOMDate().last())
                    )
        }
    ),

    @Description("Hvis sykmeldingen er forlengelse av registrert sykepengehistorikk fra annet kontor så medlingen gå til manuell behandling slik at saksbehandler kan registrere sykepengetilfellet på ny identdato og send oppgave til Nav forvaltning for registrering av inntektsopplysninger")
    SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1(
        1515,
        Status.MANUAL_PROCESSING,
        "Sykmeldingen er en forlengelse av registrert sykepengehistorikk fra et annet NAV-kontor. Saksbehandler kan registrere sykepengetilfellet på ny identdato og sende oppgave til NAV Forvaltning for registrering av inntektsopplysninger",
        "Sykmeldingen er en forlengelse av registrert sykepengehistorikk fra et annet NAV-kontor. Saksbehandler kan registrere sykepengetilfellet på ny identdato og sende oppgave til NAV Forvaltning for registrering av inntektsopplysninger",
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
        }
    ),

    @Description("Hvis sykmeldingen er forlengelse av registrert sykepengehistorikk fra annet kontor så medlingen gå til manuell behandling slik at saksbehandler kan registrere sykepengetilfellet på ny identdato og send oppgave til Nav forvaltning for registrering av inntektsopplysninger")
    SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2(
        1515,
        Status.MANUAL_PROCESSING,
        "Sykmeldingen er en forlengelse av registrert sykepengehistorikk fra et annet NAV-kontor. Saksbehandler kan registrere sykepengetilfellet på ny identdato og sende oppgave til NAV Forvaltning for registrering av inntektsopplysninger",
        "Sykmeldingen er en forlengelse av registrert sykepengehistorikk fra et annet NAV-kontor. Saksbehandler kan registrere sykepengetilfellet på ny identdato og sende oppgave til NAV Forvaltning for registrering av inntektsopplysninger",
        { (sykmelding, infotrygdForesp) ->
            infotrygdForesp.sMhistorikk?.sykmelding?.sortedSMInfos()?.lastOrNull() != null &&
                !infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode?.friskKode.isNullOrBlank() &&
                !infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode?.hovedDiagnosekode.isNullOrBlank() &&
                infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode?.utbetTOM != null &&
                infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.utbetTOM.plusDays(1) ==
                sykmelding.perioder.sortedPeriodeFOMDate().last() &&
                infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.hovedDiagnosekode != "000" &&
                infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.friskKode != "H"
        }
    ),

    @Description("Hvis sykmeldingen er forlengelse av registrert sykepengehistorikk fra annet kontor så medlingen gå til manuell behandling slik at saksbehandler kan registrere sykepengetilfellet på ny identdato og send oppgave til Nav forvaltning for registrering av inntektsopplysninger")
    SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3(
        1515,
        Status.MANUAL_PROCESSING,
        "Sykmeldingen er en forlengelse av registrert sykepengehistorikk fra et annet NAV-kontor. Saksbehandler kan registrere sykepengetilfellet på ny identdato og sende oppgave til NAV Forvaltning for registrering av inntektsopplysninger",
        "Sykmeldingen er en forlengelse av registrert sykepengehistorikk fra et annet NAV-kontor. Saksbehandler kan registrere sykepengetilfellet på ny identdato og sende oppgave til NAV Forvaltning for registrering av inntektsopplysninger",
        { (sykmelding, infotrygdForesp) ->
            sykmelding.perioder.sortedPeriodeFOMDate().lastOrNull() != null &&
                infotrygdForesp.sMhistorikk?.sykmelding?.sortedSMInfos()?.lastOrNull()?.periode?.utbetTOM != null &&
                !infotrygdForesp.sMhistorikk?.sykmelding?.sortedSMInfos()?.lastOrNull()?.periode?.friskKode.isNullOrBlank() &&
                !infotrygdForesp.sMhistorikk?.sykmelding?.sortedSMInfos()?.lastOrNull()?.periode?.hovedDiagnosekode.isNullOrBlank() &&
                infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.utbetTOM.isBefore(
                    sykmelding.perioder.sortedPeriodeFOMDate().last()
                ) &&
                infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.utbetTOM.plusDays(3).isAfter(
                    sykmelding.perioder.sortedPeriodeFOMDate().last()
                ) &&
                infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.utbetTOM.dayOfWeek >= java.time.DayOfWeek.FRIDAY &&
                sykmelding.perioder.sortedPeriodeFOMDate().last().dayOfWeek in arrayOf(
                java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY, java.time.DayOfWeek.MONDAY
            ) &&
                infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.hovedDiagnosekode != "000" &&
                infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.friskKode != "H"
        }
    ),

    @Description("Hvis ny friskmeldingsdato er mindre enn utbetalingTOM registrert i Infotrygd")
    NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT(
        1517,
        Status.MANUAL_PROCESSING,
        "Friskmeldingsdato i sykmeldingen er tidligere enn utbetalingTOM registrert i Infotrygd",
        "Friskmeldingsdato i sykmeldingen er tidligere enn utbetalingTOM registrert i Infotrygd",
        { (sykmelding, infotrygdForesp) ->
            sykmelding.prognose?.arbeidsforEtterPeriode != null &&
                sykmelding.prognose?.arbeidsforEtterPeriode ?: false &&
                infotrygdForesp.sMhistorikk?.sykmelding != null &&
                sykmelding.perioder.sortedPeriodeTOMDate().lastOrNull() != null &&
                infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().lastOrNull()?.periode?.utbetTOM != null &&
                sykmelding.perioder.sortedPeriodeTOMDate().last().isBefore(
                    infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.utbetTOM
                )
        }
    ),

    @Description("Hvis ny friskmeldingsdato er tidligere enn registrert friskmeldingsdato i Infotrygd")
    NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE(
        1518,
        Status.MANUAL_PROCESSING,
        "Friskmeldingsdato i sykmeldingen er tidligere enn registrert friskmeldingsdato i Infotrygd",
        "Friskmeldingsdato i sykmeldingen er tidligere enn registrert friskmeldingsdato i Infotrygd",
        { (sykmelding, infotrygdForesp) ->
            infotrygdForesp.sMhistorikk?.sykmelding?.sortedSMInfos()?.lastOrNull()?.periode?.friskmeldtDato != null &&
                sykmelding.prognose?.arbeidsforEtterPeriode != null &&
                sykmelding.prognose?.arbeidsforEtterPeriode ?: false &&
                sykmelding.perioder.sortedPeriodeTOMDate().lastOrNull() != null &&
                infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().lastOrNull()?.periode?.friskmeldtDato != null &&
                sykmelding.perioder.sortedPeriodeTOMDate().last().plusDays(1).isBefore(infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.friskmeldtDato)
        }
    ),

    @Description("Hvis forlengelse utover registrert tiltak FA tiltak")
    EXTANION_OVER_FA(
        1544,
        Status.MANUAL_PROCESSING,
        "Den sykmeldte er friskmeldt til arbeidsformidling (tiltakstype FA), og sykmeldingen er en forlengelse som går forbi tiltaksperioden",
        "Den sykmeldte er friskmeldt til arbeidsformidling (tiltakstype FA), og sykmeldingen er en forlengelse som går forbi tiltaksperioden",
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
        }
    ),

    @Description("Personen har flyttet ( stanskode FL i Infotrygd)")
    PERSON_MOVING_KODE_FL(
        1546,
        Status.MANUAL_PROCESSING,
        "Personen har flyttet (stanskode FL i Infotrygd)",
        "Personen har flyttet (stanskode FL i Infotrygd)",
        { (_, infotrygdForesp) ->
            infotrygdForesp.sMhistorikk?.sykmelding?.find {
                it.periode?.arbufoerFOM != null &&
                    it.periode.arbufoerFOM == infotrygdForesp.sMhistorikk?.sykmelding?.sortedFOMDate()?.lastOrNull()
            }?.periode?.stans == "FL"
        }
    ),

    @Description("Hvis perioden er avsluttet (AA)")
    PERIOD_FOR_AA_ENDED(
        1549,
        Status.MANUAL_PROCESSING,
        "Syketilfellet er avsluttet (stanskode AA)",
        "Syketilellet er avsluttet (stanskode AA)",
        { (sykmelding, infotrygdForesp) ->
            sykmelding.perioder.any {
                !infotrygdForesp.sMhistorikk?.sykmelding?.sortedSMInfos()?.lastOrNull()?.periode?.stans.isNullOrBlank() &&
                    infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.arbufoerTOM != null &&
                    infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.stans == "AA" &&
                    it.fom.isBefore(infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.arbufoerTOM)
            }
        }
    ),

    @Description("Hvis perioden er avsluttet-frisk (AF)")
    PERIOD_IS_AF(
        1550,
        Status.MANUAL_PROCESSING,
        "Syketilfellet er avsluttet (stanskode AF - friskmelding)",
        "Syketilfellet er avsluttet (stanskode AF - friskmelding)",
        { (sykmelding, infotrygdForesp) ->
            sykmelding.perioder.any {
                !infotrygdForesp.sMhistorikk?.sykmelding?.sortedSMInfos()?.lastOrNull()?.periode?.stans.isNullOrBlank() &&
                    infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.arbufoerTOM != null &&
                    infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.stans == "AF" &&
                    it.fom.isBefore(infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.arbufoerTOM)
            }
        }
    ),

    @Description("Hvis maks sykepenger er utbetalt")
    MAX_SICK_LEAVE_PAYOUT(
        1551,
        Status.MANUAL_PROCESSING,
        "Syketilfellet er avsluttet fordi den sykmeldte har nådd maksdato",
        "Syketilfellet er avsluttet fordi den sykmeldte har nådd maksdato",
        { (sykmelding, infotrygdForesp) ->
            sykmelding.perioder.any {
                !infotrygdForesp.sMhistorikk?.sykmelding?.sortedSMInfos()?.lastOrNull()?.periode?.stans.isNullOrBlank() &&
                    infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.stans == "MAX" &&
                    infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.arbufoerTOM != null &&
                    it.fom.isBefore(infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.arbufoerTOM.plusMonths(6))
            }
        }
    ),

    @Description("Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd")
    ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING(
        1591,
        Status.MANUAL_PROCESSING,
        "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
        "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
        { (_, infotrygdForesp) ->
            infotrygdForesp.hovedStatus?.kodeMelding?.toIntOrNull() != null &&
                infotrygdForesp.hovedStatus.kodeMelding.toInt() > 4
        }
    ),

    @Description("Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd")
    ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING(
        1591,
        Status.MANUAL_PROCESSING,
        "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
        "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
        { (_, infotrygdForesp) ->
            infotrygdForesp.sMhistorikk?.status?.kodeMelding?.toIntOrNull() != null &&
                infotrygdForesp.sMhistorikk.status.kodeMelding.toInt() > 4
        }
    ),

    @Description("Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd")
    ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING(
        1591,
        Status.MANUAL_PROCESSING,
        "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
        "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
        { (_, infotrygdForesp) ->
            infotrygdForesp.parallelleYtelser?.status?.kodeMelding?.toIntOrNull() != null &&
                infotrygdForesp.parallelleYtelser.status.kodeMelding.toInt() > 4
        }
    ),

    @Description("Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd")
    ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING(
        1591,
        Status.MANUAL_PROCESSING,
        "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
        "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
        { (_, infotrygdForesp) ->
            infotrygdForesp.diagnosekodeOK?.status?.kodeMelding?.toIntOrNull() != null &&
                infotrygdForesp.diagnosekodeOK.status.kodeMelding.toInt() > 4
        }
    ),

    @Description("Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd")
    ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING(
        1591,
        Status.MANUAL_PROCESSING,
        "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
        "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
        { (_, infotrygdForesp) ->
            infotrygdForesp.pasient?.status?.kodeMelding?.toIntOrNull() != null &&
                infotrygdForesp.pasient.status.kodeMelding.toInt() > 4
        }
    ),

    @Description("Infotrygd returnerte ikke arbufoerTOM dato på sykmeldings historikken, vi kan ikke automatisk oppdatere Infotrygd")
    ARBEIDUFORETOM_MANGLER(
        1591,
        Status.MANUAL_PROCESSING,
        "Fant ikke arbufoerTOM-dato for sykmeldingshistorikken i Infotrygd. Vi kan derfor ikke oppdatere Infotrygd automatisk.",
        "Fant ikke arbufoerTOM-dato for sykmeldingshistorikken i Infotrygd. Vi kan derfor ikke oppdatere Infotrygd automatisk.",
        { (_, infotrygdForesp) ->
            infotrygdForesp.sMhistorikk?.sykmelding != null &&
                infotrygdForesp.sMhistorikk.status.kodeMelding != "04" &&
                infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().lastOrNull()?.periode?.arbufoerTOM == null
        }
    )
}

fun List<Periode>.sortedPeriodeTOM(): List<Periode> =
    sortedBy { it.tom }

fun List<Periode>.sortedPeriodeTOMDate(): List<LocalDate> =
    map { it.tom }.sorted()

fun List<Periode>.sortedPeriodeFOMDate(): List<LocalDate> =
    map { it.fom }.sorted()

fun List<TypeSMinfo>.sortedSMInfos(): List<TypeSMinfo> =
    sortedBy { it.periode.arbufoerTOM }

fun List<TypeSMinfo>.sortedFOMDate(): List<LocalDate> =
    map { it.periode.arbufoerFOM }.filterNotNull().sorted()

fun List<TypeSMinfo>.sortedTOMDate(): List<LocalDate> =
    map { it.periode.arbufoerTOM }.filterNotNull().sorted()

fun List<TypeSMinfo.Historikk>.sortedSMinfoHistorikk(): List<TypeSMinfo.Historikk> =
    sortedBy { it.endringsDato }

fun Periode.range(): ClosedRange<LocalDate> = fom.rangeTo(tom)

fun TypeSMinfo.Periode.range(): ClosedRange<LocalDate> = arbufoerFOM.rangeTo(arbufoerTOM)
