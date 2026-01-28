package no.nav.syfo.rules.validation

import no.nav.syfo.model.sykmelding.Status
import no.nav.syfo.rules.common.RuleHit

enum class ValidationRuleHit(
    val ruleHit: RuleHit,
) {
    NUMBER_OF_TREATMENT_DAYS_SET(
        ruleHit =
            RuleHit(
                rule = "NUMBER_OF_TREATMENT_DAYS_SET",
                status = Status.MANUAL_PROCESSING,
                messageForSender =
                    "Hvis behandlingsdager er angitt sendes sykmeldingen til manuell behandling.",
                messageForUser =
                    "Hvis behandlingsdager er angitt sendes sykmeldingen til manuell behandling.",
            ),
    ),
    GRADERT_REISETILSKUDD_ER_OPPGITT(
        ruleHit =
            RuleHit(
                rule = "GRADERT_REISETILSKUDD_ER_OPPGITT",
                status = Status.MANUAL_PROCESSING,
                messageForSender =
                    "Hvis reisetilskudd er angitt sendes sykmeldingen til manuell behandling.",
                messageForUser =
                    "Hvis reisetilskudd er angitt sendes sykmeldingen til manuell behandling.",
            ),
    ),
    TRAVEL_SUBSIDY_SPECIFIED(
        ruleHit =
            RuleHit(
                rule = "TRAVEL_SUBSIDY_SPECIFIED",
                status = Status.MANUAL_PROCESSING,
                messageForSender =
                    "Hvis reisetilskudd er angitt sendes sykmeldingen til manuell behandling.",
                messageForUser =
                    "Hvis reisetilskudd er angitt sendes sykmeldingen til manuell behandling.",
            ),
    ),
    PATIENT_NOT_IN_IP(
        ruleHit =
            RuleHit(
                rule = "PATIENT_NOT_IN_IP",
                status = Status.MANUAL_PROCESSING,
                messageForSender = "Pasienten finnes ikke i Infotrygd",
                messageForUser = "Pasienten finnes ikke i Infotrygd",
            ),
    ),
    PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE(
        ruleHit =
            RuleHit(
                rule =
                    "PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE",
                status = Status.MANUAL_PROCESSING,
                messageForSender =
                    "Delvis sammenfallende sykmeldingsperiode er registrert i Infotrygd",
                messageForUser =
                    "Delvis sammenfallende sykmeldingsperiode er registrert i Infotrygd",
            ),
    ),
    SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1(
        ruleHit =
            RuleHit(
                rule = "SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1",
                status = Status.MANUAL_PROCESSING,
                messageForSender =
                    "Sykmeldingen er en forlengelse av registrert sykepengehistorikk fra et annet NAV-kontor. Saksbehandler kan registrere sykepengetilfellet på ny identdato og sende oppgave til NAV Forvaltning for registrering av inntektsopplysninger",
                messageForUser =
                    "Sykmeldingen er en forlengelse av registrert sykepengehistorikk fra et annet NAV-kontor. Saksbehandler kan registrere sykepengetilfellet på ny identdato og sende oppgave til NAV Forvaltning for registrering av inntektsopplysninger",
            ),
    ),
    SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2(
        ruleHit =
            RuleHit(
                rule = "SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2",
                status = Status.MANUAL_PROCESSING,
                messageForSender =
                    "Sykmeldingen er en forlengelse av registrert sykepengehistorikk fra et annet NAV-kontor. Saksbehandler kan registrere sykepengetilfellet på ny identdato og sende oppgave til NAV Forvaltning for registrering av inntektsopplysninger",
                messageForUser =
                    "Sykmeldingen er en forlengelse av registrert sykepengehistorikk fra et annet NAV-kontor. Saksbehandler kan registrere sykepengetilfellet på ny identdato og sende oppgave til NAV Forvaltning for registrering av inntektsopplysninger",
            ),
    ),
    SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3(
        ruleHit =
            RuleHit(
                rule = "SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3",
                status = Status.MANUAL_PROCESSING,
                messageForSender =
                    "Sykmeldingen er en forlengelse av registrert sykepengehistorikk fra et annet NAV-kontor. Saksbehandler kan registrere sykepengetilfellet på ny identdato og sende oppgave til NAV Forvaltning for registrering av inntektsopplysninger",
                messageForUser =
                    "Sykmeldingen er en forlengelse av registrert sykepengehistorikk fra et annet NAV-kontor. Saksbehandler kan registrere sykepengetilfellet på ny identdato og sende oppgave til NAV Forvaltning for registrering av inntektsopplysninger",
            ),
    ),
    NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT(
        ruleHit =
            RuleHit(
                rule = "NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT",
                status = Status.MANUAL_PROCESSING,
                messageForSender =
                    "Friskmeldingsdato i sykmeldingen er tidligere enn utbetalingTOM registrert i Infotrygd",
                messageForUser =
                    "Friskmeldingsdato i sykmeldingen er tidligere enn utbetalingTOM registrert i Infotrygd",
            ),
    ),
    NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE(
        ruleHit =
            RuleHit(
                rule = "NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE",
                status = Status.MANUAL_PROCESSING,
                messageForSender =
                    "Friskmeldingsdato i sykmeldingen er tidligere enn registrert friskmeldingsdato i Infotrygd",
                messageForUser =
                    "Friskmeldingsdato i sykmeldingen er tidligere enn registrert friskmeldingsdato i Infotrygd",
            ),
    ),
    EXTANION_OVER_FA(
        ruleHit =
            RuleHit(
                rule = "EXTANION_OVER_FA",
                status = Status.MANUAL_PROCESSING,
                messageForSender =
                    "Den sykmeldte er friskmeldt til arbeidsformidling (tiltakstype FA), og sykmeldingen er en forlengelse som går forbi tiltaksperioden",
                messageForUser =
                    "Den sykmeldte er friskmeldt til arbeidsformidling (tiltakstype FA), og sykmeldingen er en forlengelse som går forbi tiltaksperioden",
            ),
    ),
    PERSON_MOVING_KODE_FL(
        ruleHit =
            RuleHit(
                rule = "PERSON_MOVING_KODE_FL",
                status = Status.MANUAL_PROCESSING,
                messageForSender = "Personen har flyttet (stanskode FL i Infotrygd)",
                messageForUser = "Personen har flyttet (stanskode FL i Infotrygd)",
            ),
    ),
    PERIOD_FOR_AA_ENDED(
        ruleHit =
            RuleHit(
                rule = "PERIOD_FOR_AA_ENDED",
                status = Status.MANUAL_PROCESSING,
                messageForSender = "Syketilfellet er avsluttet (stanskode AA)",
                messageForUser = "Syketilfellet er avsluttet (stanskode AA)",
            ),
    ),
    PERIOD_IS_AF(
        ruleHit =
            RuleHit(
                rule = "PERIOD_IS_AF",
                status = Status.MANUAL_PROCESSING,
                messageForSender = "Syketilfellet er avsluttet (stanskode AF - friskmelding)",
                messageForUser = "Syketilfellet er avsluttet (stanskode AF - friskmelding)",
            ),
    ),
    MAX_SICK_LEAVE_PAYOUT(
        ruleHit =
            RuleHit(
                rule = "MAX_SICK_LEAVE_PAYOUT",
                status = Status.MANUAL_PROCESSING,
                messageForSender =
                    "Syketilfellet er avsluttet fordi den sykmeldte har nådd maksdato",
                messageForUser = "Syketilfellet er avsluttet fordi den sykmeldte har nådd maksdato",
            ),
    ),
    ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING(
        ruleHit =
            RuleHit(
                rule = "ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING",
                status = Status.MANUAL_PROCESSING,
                messageForSender =
                    "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
                messageForUser =
                    "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
            ),
    ),
    ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING(
        ruleHit =
            RuleHit(
                rule = "ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING",
                status = Status.MANUAL_PROCESSING,
                messageForSender =
                    "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
                messageForUser =
                    "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
            ),
    ),
    ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING(
        ruleHit =
            RuleHit(
                rule = "ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING",
                status = Status.MANUAL_PROCESSING,
                messageForSender =
                    "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
                messageForUser =
                    "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
            ),
    ),
    ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING(
        ruleHit =
            RuleHit(
                rule = "ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING",
                status = Status.MANUAL_PROCESSING,
                messageForSender =
                    "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
                messageForUser =
                    "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
            ),
    ),
    ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING(
        ruleHit =
            RuleHit(
                rule = "ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING",
                status = Status.MANUAL_PROCESSING,
                messageForSender =
                    "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
                messageForUser =
                    "Infotrygd returnerte en feil, vi kan ikke automatisk oppdatere Infotrygd",
            ),
    ),
    ARBEIDUFORETOM_MANGLER(
        ruleHit =
            RuleHit(
                rule = "ARBEIDUFORETOM_MANGLER",
                status = Status.MANUAL_PROCESSING,
                messageForSender =
                    "Fant ikke arbufoerTOM-dato for sykmeldingshistorikken i Infotrygd. Vi kan derfor ikke oppdatere Infotrygd automatisk.",
                messageForUser =
                    "Fant ikke arbufoerTOM-dato for sykmeldingshistorikken i Infotrygd. Vi kan derfor ikke oppdatere Infotrygd automatisk.",
            ),
    ),
    MISSING_OR_INCORRECT_HOVEDDIAGNOSE(
        ruleHit =
            RuleHit(
                rule = "MISSING_OR_INCORRECT_HOVEDDIAGNOSE",
                status = Status.MANUAL_PROCESSING,
                messageForSender = "Hoveddiagnose mangler eller er ikke riktig.",
                messageForUser = "Hoveddiagnose mangler eller er ikke riktig",
            ),
    ),
}
