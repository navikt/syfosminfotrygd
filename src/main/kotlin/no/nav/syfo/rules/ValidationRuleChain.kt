package no.nav.syfo.rules

import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.syfo.model.Periode
import no.nav.syfo.model.Status
import no.nav.syfo.model.Sykmelding
import java.time.LocalDate

class ValidationRuleChain(
    private val sykmelding: Sykmelding,
    private val infotrygdForesp: InfotrygdForesp
) : RuleChain {

    override val rules: List<Rule<*>> = listOf(

        // Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.")
        Rule(
            name = "NUMBER_OF_TREATMENT_DAYS_SET",
            ruleId = 1260,
            status = Status.MANUAL_PROCESSING,
            messageForUser = "Hvis behandlingsdager er angitt sendes sykmeldingen til manuell behandling.",
            messageForSender = "Hvis behandlingsdager er angitt sendes sykmeldingen til manuell behandling.",
            input = object {
                val perioder = sykmelding.perioder
            },
            predicate = { input ->
                input.perioder.any { it.behandlingsdager != null }
            }
        ),

        // Hvis sykmeldingen angir og er gradert reisetilskudd går meldingen til manuell behandling.")
        Rule(
            name = "GRADERT_REISETILSKUDD_ER_OPPGITT",
            ruleId = 1270,
            status = Status.MANUAL_PROCESSING,
            messageForUser = "Hvis reisetilskudd er angitt sendes sykmeldingen til manuell behandling.",
            messageForSender = "Hvis reisetilskudd er angitt sendes sykmeldingen til manuell behandling.",
            input = object {
                val perioder = sykmelding.perioder
            },
            predicate = { input ->
                input.perioder.any { it.gradert?.reisetilskudd ?: false }
            }

        ),

        // Hvis sykmeldingen angir reisetilskudd går meldingen til manuell behandling.")
        Rule(
            name = "TRAVEL_SUBSIDY_SPECIFIED",
            ruleId = 1270,
            status = Status.MANUAL_PROCESSING,
            messageForUser = "Hvis reisetilskudd er angitt sendes sykmeldingen til manuell behandling.",
            messageForSender = "Hvis reisetilskudd er angitt sendes sykmeldingen til manuell behandling.",
            input = object {
                val perioder = sykmelding.perioder
            },
            predicate = { input ->
                input.perioder.any { it.reisetilskudd }
            }
        ),

        // Hvis pasienten ikke finnes i infotrygd")
        Rule(
            name = "PATIENT_NOT_IN_IP",
            ruleId = 1501,
            status = Status.MANUAL_PROCESSING,
            messageForUser = "Pasienten finnes ikke i Infotrygd",
            messageForSender = "Pasienten finnes ikke i Infotrygd",
            input = object {
                val pasient = infotrygdForesp.pasient
            },
            predicate = { input ->
                input.pasient?.isFinnes != null && !input.pasient.isFinnes
            }
        ),

        // Hvis delvis sammenfallende sykmeldingsperiode er registrert i Infotrygd")
        Rule(
            name = "PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE",
            ruleId = 1513,
            status = Status.MANUAL_PROCESSING,
            messageForUser = "Delvis sammenfallende sykmeldingsperiode er registrert i Infotrygd",
            messageForSender = "Delvis sammenfallende sykmeldingsperiode er registrert i Infotrygd",
            input = object {
                val infotrygdSykmelding = infotrygdForesp.sMhistorikk?.sykmelding
                val sykmeldingPerioder = sykmelding.perioder
            },
            predicate = { input ->
                input.infotrygdSykmelding != null &&
                    input.infotrygdSykmelding.sortedTOMDate().lastOrNull() != null &&
                    input.infotrygdSykmelding.sortedFOMDate().firstOrNull() != null &&
                    input.sykmeldingPerioder.sortedPeriodeFOMDate().firstOrNull() != null &&
                    input.sykmeldingPerioder.sortedPeriodeTOMDate().lastOrNull() != null &&
                    (
                        input.sykmeldingPerioder.sortedPeriodeFOMDate().first()
                            .isBefore(input.infotrygdSykmelding.sortedFOMDate().first()) ||
                            input.sykmeldingPerioder.sortedPeriodeTOMDate().last()
                                .isBefore(input.infotrygdSykmelding.sortedTOMDate().last())
                        )
            }
        ),

        // Hvis sykmeldingen er forlengelse av registrert sykepengehistorikk fra annet kontor så medlingen gå til manuell behandling slik at saksbehandler kan registrere sykepengetilfellet på ny identdato og send oppgave til Nav forvaltning for registrering av inntektsopplysninger")
        Rule(
            name = "SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1",
            ruleId = 1515,
            status = Status.MANUAL_PROCESSING,
            messageForUser = "Sykmeldingen er en forlengelse av registrert sykepengehistorikk fra et annet NAV-kontor. Saksbehandler kan registrere sykepengetilfellet på ny identdato og sende oppgave til NAV Forvaltning for registrering av inntektsopplysninger",
            messageForSender = "Sykmeldingen er en forlengelse av registrert sykepengehistorikk fra et annet NAV-kontor. Saksbehandler kan registrere sykepengetilfellet på ny identdato og sende oppgave til NAV Forvaltning for registrering av inntektsopplysninger",
            input = object {
                val infotrygdSykmelding = infotrygdForesp.sMhistorikk?.sykmelding
                val sykmeldingPerioder = sykmelding.perioder
            },
            predicate = { input ->
                input.infotrygdSykmelding?.sortedSMInfos()?.lastOrNull()?.periode?.arbufoerFOM != null &&
                    input.sykmeldingPerioder.sortedPeriodeFOMDate().lastOrNull() != null &&
                    !input.infotrygdSykmelding.sortedSMInfos()
                        .last().periode?.friskKode.isNullOrBlank() &&
                    !input.infotrygdSykmelding.sortedSMInfos()
                        .last().periode?.hovedDiagnosekode.isNullOrBlank() &&
                    input.infotrygdSykmelding.sortedSMInfos().last().periode.arbufoerFOM.isBefore(
                        input.sykmeldingPerioder.sortedPeriodeFOMDate().last()
                    ) && input.infotrygdSykmelding.sortedSMInfos().last().periode?.utbetTOM != null &&
                    input.infotrygdSykmelding.sortedSMInfos().last().periode.utbetTOM.isAfter(
                        input.sykmeldingPerioder.sortedPeriodeFOMDate().last()
                    ) && input.infotrygdSykmelding.sortedSMInfos()
                    .last().periode.hovedDiagnosekode != "000" &&
                    input.infotrygdSykmelding.sortedSMInfos().last().periode.friskKode != "H"
            }
        ),

        // Hvis sykmeldingen er forlengelse av registrert sykepengehistorikk fra annet kontor så medlingen gå til manuell behandling slik at saksbehandler kan registrere sykepengetilfellet på ny identdato og send oppgave til Nav forvaltning for registrering av inntektsopplysninger")
        Rule(
            name = "SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2",
            ruleId = 1515,
            status = Status.MANUAL_PROCESSING,
            messageForUser = "Sykmeldingen er en forlengelse av registrert sykepengehistorikk fra et annet NAV-kontor. Saksbehandler kan registrere sykepengetilfellet på ny identdato og sende oppgave til NAV Forvaltning for registrering av inntektsopplysninger",
            messageForSender = "Sykmeldingen er en forlengelse av registrert sykepengehistorikk fra et annet NAV-kontor. Saksbehandler kan registrere sykepengetilfellet på ny identdato og sende oppgave til NAV Forvaltning for registrering av inntektsopplysninger",
            input = object {
                val infotrygdSykmelding = infotrygdForesp.sMhistorikk?.sykmelding
                val sykmeldingPerioder = sykmelding.perioder
            },
            predicate = { input ->
                input.infotrygdSykmelding?.sortedSMInfos()?.lastOrNull() != null &&
                    !input.infotrygdSykmelding.sortedSMInfos()
                        .last().periode?.friskKode.isNullOrBlank() &&
                    !input.infotrygdSykmelding.sortedSMInfos()
                        .last().periode?.hovedDiagnosekode.isNullOrBlank() &&
                    input.infotrygdSykmelding.sortedSMInfos().last().periode?.utbetTOM != null &&
                    input.infotrygdSykmelding.sortedSMInfos().last().periode.utbetTOM.plusDays(1) ==
                    input.sykmeldingPerioder.sortedPeriodeFOMDate().last() &&
                    input.infotrygdSykmelding.sortedSMInfos()
                    .last().periode.hovedDiagnosekode != "000" &&
                    input.infotrygdSykmelding.sortedSMInfos().last().periode.friskKode != "H"
            }
        ),

        // Hvis sykmeldingen er forlengelse av registrert sykepengehistorikk fra annet kontor så medlingen gå til manuell behandling slik at saksbehandler kan registrere sykepengetilfellet på ny identdato og send oppgave til Nav forvaltning for registrering av inntektsopplysninger")
        Rule(
            name = "SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3",
            ruleId = 1515,
            status = Status.MANUAL_PROCESSING,
            messageForUser = "Sykmeldingen er en forlengelse av registrert sykepengehistorikk fra et annet NAV-kontor. Saksbehandler kan registrere sykepengetilfellet på ny identdato og sende oppgave til NAV Forvaltning for registrering av inntektsopplysninger",
            messageForSender = "Sykmeldingen er en forlengelse av registrert sykepengehistorikk fra et annet NAV-kontor. Saksbehandler kan registrere sykepengetilfellet på ny identdato og sende oppgave til NAV Forvaltning for registrering av inntektsopplysninger",
            input = object {
                val infotrygdSykmelding = infotrygdForesp.sMhistorikk?.sykmelding
                val sykmeldingPerioder = sykmelding.perioder
            },
            predicate = { input ->
                input.sykmeldingPerioder.sortedPeriodeFOMDate().lastOrNull() != null &&
                    input.infotrygdSykmelding?.sortedSMInfos()
                    ?.lastOrNull()?.periode?.utbetTOM != null &&
                    !input.infotrygdSykmelding.sortedSMInfos()
                        .lastOrNull()?.periode?.friskKode.isNullOrBlank() &&
                    !input.infotrygdSykmelding.sortedSMInfos()
                        .lastOrNull()?.periode?.hovedDiagnosekode.isNullOrBlank() &&
                    input.infotrygdSykmelding.sortedSMInfos().last().periode.utbetTOM.isBefore(
                        input.sykmeldingPerioder.sortedPeriodeFOMDate().last()
                    ) &&
                    input.infotrygdSykmelding.sortedSMInfos().last().periode.utbetTOM.plusDays(3)
                        .isAfter(
                            input.sykmeldingPerioder.sortedPeriodeFOMDate().last()
                        ) &&
                    input.infotrygdSykmelding.sortedSMInfos()
                    .last().periode.utbetTOM.dayOfWeek >= java.time.DayOfWeek.FRIDAY &&
                    input.sykmeldingPerioder.sortedPeriodeFOMDate().last().dayOfWeek in arrayOf(
                    java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY, java.time.DayOfWeek.MONDAY
                ) &&
                    input.infotrygdSykmelding.sortedSMInfos()
                    .last().periode.hovedDiagnosekode != "000" &&
                    input.infotrygdSykmelding.sortedSMInfos().last().periode.friskKode != "H"
            }
        ),

        // Hvis ny friskmeldingsdato er mindre enn utbetalingTOM registrert i Infotrygd")
        Rule(
            name = "NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT",
            ruleId = 1517,
            status = Status.MANUAL_PROCESSING,
            messageForUser = "Friskmeldingsdato i sykmeldingen er tidligere enn utbetalingTOM registrert i Infotrygd",
            messageForSender = "Friskmeldingsdato i sykmeldingen er tidligere enn utbetalingTOM registrert i Infotrygd",
            input = object {
                val infotrygdSykmelding = infotrygdForesp.sMhistorikk?.sykmelding
                val sykmeldingPerioder = sykmelding.perioder
                val sykmeldingPrognose = sykmelding.prognose
            },
            predicate = { input ->
                input.sykmeldingPrognose?.arbeidsforEtterPeriode != null &&
                    input.sykmeldingPrognose.arbeidsforEtterPeriode &&
                    input.infotrygdSykmelding != null &&
                    input.sykmeldingPerioder.sortedPeriodeTOMDate().lastOrNull() != null &&
                    input.infotrygdSykmelding.sortedSMInfos()
                    .lastOrNull()?.periode?.utbetTOM != null &&
                    input.sykmeldingPerioder.sortedPeriodeTOMDate().last().isBefore(
                        input.infotrygdSykmelding.sortedSMInfos().last().periode.utbetTOM
                    )
            }
        ),

        // Hvis ny friskmeldingsdato er tidligere enn registrert friskmeldingsdato i Infotrygd")
        Rule(
            name = "NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE",
            ruleId = 1518,
            status = Status.MANUAL_PROCESSING,
            messageForUser = "Friskmeldingsdato i sykmeldingen er tidligere enn registrert friskmeldingsdato i Infotrygd",
            messageForSender = "Friskmeldingsdato i sykmeldingen er tidligere enn registrert friskmeldingsdato i Infotrygd",
            input = object {
                val infotrygdSykmelding = infotrygdForesp.sMhistorikk?.sykmelding
                val sykmeldingPerioder = sykmelding.perioder
                val sykmeldingPrognose = sykmelding.prognose
            },
            predicate = { input ->
                input.infotrygdSykmelding?.sortedSMInfos()
                    ?.lastOrNull()?.periode?.friskmeldtDato != null &&
                    input.sykmeldingPrognose?.arbeidsforEtterPeriode != null &&
                    input.sykmeldingPrognose.arbeidsforEtterPeriode &&
                    input.sykmeldingPerioder.sortedPeriodeTOMDate().lastOrNull() != null &&
                    input.infotrygdSykmelding.sortedSMInfos()
                    .lastOrNull()?.periode?.friskmeldtDato != null &&
                    input.sykmeldingPerioder.sortedPeriodeTOMDate().last().plusDays(1).isBefore(
                        input.infotrygdSykmelding.sortedSMInfos().last().periode.friskmeldtDato
                    )
            }
        ),

        // Hvis forlengelse utover registrert tiltak FA tiltak")
        Rule(
            name = "EXTANION_OVER_FA",
            ruleId = 1544,
            status = Status.MANUAL_PROCESSING,
            messageForUser = "Den sykmeldte er friskmeldt til arbeidsformidling (tiltakstype FA), og sykmeldingen er en forlengelse som går forbi tiltaksperioden",
            messageForSender = "Den sykmeldte er friskmeldt til arbeidsformidling (tiltakstype FA), og sykmeldingen er en forlengelse som går forbi tiltaksperioden",
            input = object {
                val infotrygdSykmelding = infotrygdForesp.sMhistorikk?.sykmelding
                val sykmeldingPerioder = sykmelding.perioder
            },
            predicate = { input ->
                input.infotrygdSykmelding != null &&
                    input.sykmeldingPerioder.sortedPeriodeFOMDate().lastOrNull() != null &&
                    input.infotrygdSykmelding.sortedSMInfos()
                    .lastOrNull()?.historikk?.sortedSMinfoHistorikk()?.lastOrNull()?.tilltak?.type != null &&
                    input.infotrygdSykmelding.sortedSMInfos().last().historikk.sortedSMinfoHistorikk()
                    .last().tilltak.type == "FA" &&
                    input.infotrygdSykmelding.sortedSMInfos().last().historikk.sortedSMinfoHistorikk()
                    .last().tilltak.tom != null &&
                    input.sykmeldingPerioder.any { periodA ->
                        input.infotrygdSykmelding.any { sykmeldinger ->
                            sykmeldinger.historikk.any { historikk ->
                                historikk?.tilltak != null && historikk.tilltak.type == "FA" && historikk.tilltak.fom in periodA.range() || historikk?.tilltak != null && historikk.tilltak.tom in periodA.range()
                            }
                        }
                    }
            }
        ),

        // Personen har flyttet ( stanskode FL i Infotrygd)")
        Rule(
            name = "PERSON_MOVING_KODE_FL",
            ruleId = 1546,
            status = Status.MANUAL_PROCESSING,
            messageForUser = "Personen har flyttet (stanskode FL i Infotrygd)",
            messageForSender = "Personen har flyttet (stanskode FL i Infotrygd)",
            input = object {
                val infotrygdSykmelding = infotrygdForesp.sMhistorikk?.sykmelding
            },
            predicate = { input ->
                input.infotrygdSykmelding?.find {
                    it.periode?.arbufoerFOM != null &&
                        it.periode.arbufoerFOM == input.infotrygdSykmelding.sortedFOMDate().lastOrNull()
                }?.periode?.stans == "FL"
            }
        ),

        // Hvis perioden er avsluttet (AA)")
        Rule(
            name = "PERIOD_FOR_AA_ENDED",
            ruleId = 1549,
            status = Status.MANUAL_PROCESSING,
            messageForUser = "Syketilfellet er avsluttet (stanskode AA)",
            messageForSender = "Syketilellet er avsluttet (stanskode AA)",
            input = object {
                val infotrygdSykmelding = infotrygdForesp.sMhistorikk?.sykmelding
                val sykmeldingPerioder = sykmelding.perioder
                val sykmeldingPrognose = sykmelding.prognose
            },
            predicate = { input ->
                input.sykmeldingPerioder.any {
                    !input.infotrygdSykmelding?.sortedSMInfos()
                        ?.lastOrNull()?.periode?.stans.isNullOrBlank() &&
                        infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.arbufoerTOM != null &&
                        infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.stans == "AA" &&
                        it.fom.isBefore(
                            infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.arbufoerTOM
                        )
                }
            }
        ),

        // Hvis perioden er avsluttet-frisk (AF)")
        Rule(
            name = "PERIOD_IS_AF",
            ruleId = 1550,
            status = Status.MANUAL_PROCESSING,
            messageForUser = "Syketilfellet er avsluttet (stanskode AF - friskmelding)",
            messageForSender = "Syketilfellet er avsluttet (stanskode AF - friskmelding)",
            input = object {},
            predicate = { _ ->
                sykmelding.perioder.any {
                    !infotrygdForesp.sMhistorikk?.sykmelding?.sortedSMInfos()
                        ?.lastOrNull()?.periode?.stans.isNullOrBlank() &&
                        infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.arbufoerTOM != null &&
                        infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.stans == "AF" &&
                        it.fom.isBefore(
                            infotrygdForesp.sMhistorikk.sykmelding.sortedSMInfos().last().periode.arbufoerTOM
                        )
                }
            }
        ),

        // Hvis maks sykepenger er utbetalt")
        Rule(
            name = "MAX_SICK_LEAVE_PAYOUT",
            ruleId = 1551,
            status = Status.MANUAL_PROCESSING,
            messageForUser = "Syketilfellet er avsluttet fordi den sykmeldte har nådd maksdato",
            messageForSender = "Syketilfellet er avsluttet fordi den sykmeldte har nådd maksdato",
            input = object {
                val sykmeldingPerioder = sykmelding.perioder
                val infotrygdSykmelding = infotrygdForesp.sMhistorikk?.sykmelding
            },
            predicate = { input ->
                sykmelding.perioder.any {
                    !input.infotrygdSykmelding?.sortedSMInfos()
                        ?.lastOrNull()?.periode?.stans.isNullOrBlank() &&
                        input.infotrygdSykmelding?.sortedSMInfos()?.last()?.periode?.stans == "MAX" &&
                        input.infotrygdSykmelding.sortedSMInfos().last().periode.arbufoerTOM != null &&
                        it.fom.isBefore(
                            input.infotrygdSykmelding.sortedSMInfos()
                                .last().periode.arbufoerTOM.plusMonths(6)
                        )
                }
            },
        ),

        // Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd")
        Rule(
            name = "ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING",
            ruleId = 1591,
            status = Status.MANUAL_PROCESSING,
            messageForUser = "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
            messageForSender = "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
            input = object {
                val hovedStatusKodeMelding = infotrygdForesp.hovedStatus?.kodeMelding
            },
            predicate = { input ->
                input.hovedStatusKodeMelding?.toIntOrNull() != null && input.hovedStatusKodeMelding.toInt() > 4
            }
        ),

        // Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd")
        Rule(
            name = "ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING",
            ruleId = 1591,
            status = Status.MANUAL_PROCESSING,
            messageForUser = "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
            messageForSender = "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
            input = object {
                val smHistorikkKodeMelding = infotrygdForesp.sMhistorikk?.status?.kodeMelding
            },
            predicate = { input ->
                input.smHistorikkKodeMelding?.toIntOrNull() != null && input.smHistorikkKodeMelding.toInt() > 4
            }
        ),

        // Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd")
        Rule(
            name = "ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING",
            ruleId = 1591,
            status = Status.MANUAL_PROCESSING,
            messageForUser = "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
            messageForSender = "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
            input = object {
                val parallelleYtelsesKodeMelding = infotrygdForesp.parallelleYtelser?.status?.kodeMelding
            },
            predicate = { input ->
                input.parallelleYtelsesKodeMelding?.toIntOrNull() != null && input.parallelleYtelsesKodeMelding.toInt() > 4
            }
        ),

        // Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd")
        Rule(
            name = "ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING",
            ruleId = 1591,
            status = Status.MANUAL_PROCESSING,
            messageForUser = "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
            messageForSender = "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
            input = object {
                val diagnoseKodeKodeMelding = infotrygdForesp.diagnosekodeOK?.status?.kodeMelding
            },
            predicate = { input ->
                input.diagnoseKodeKodeMelding?.toIntOrNull() != null && input.diagnoseKodeKodeMelding.toInt() > 4
            }
        ),

        // Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd")
        Rule(
            name = "ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING",
            ruleId = 1591,
            status = Status.MANUAL_PROCESSING,
            messageForUser = "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
            messageForSender = "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
            input = object {
                val pasientStatusKodeMelding = infotrygdForesp.pasient?.status?.kodeMelding
            },
            predicate = { input ->
                input.pasientStatusKodeMelding?.toIntOrNull() != null && input.pasientStatusKodeMelding.toInt() > 4
            }
        ),

        // Infotrygd returnerte ikke arbufoerTOM dato på sykmeldings historikken, vi kan ikke automatisk oppdatere Infotrygd")
        Rule(
            name = "ARBEIDUFORETOM_MANGLER",
            ruleId = 1591,
            status = Status.MANUAL_PROCESSING,
            messageForUser = "Fant ikke arbufoerTOM-dato for sykmeldingshistorikken i Infotrygd. Vi kan derfor ikke oppdatere Infotrygd automatisk.",
            messageForSender = "Fant ikke arbufoerTOM-dato for sykmeldingshistorikken i Infotrygd. Vi kan derfor ikke oppdatere Infotrygd automatisk.",
            input = object {
                val sykmelding = infotrygdForesp.sMhistorikk?.sykmelding
                val status = infotrygdForesp.sMhistorikk?.status
            },
            predicate = { input ->
                input.sykmelding != null &&
                    input.status?.kodeMelding != "04" &&
                    input.sykmelding.sortedSMInfos().lastOrNull()?.periode?.arbufoerTOM == null
            }
        )
    )
}

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
