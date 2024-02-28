package no.nav.syfo.services

import io.kotest.core.spec.style.FunSpec
import java.time.LocalDate
import no.nav.syfo.generatePeriode
import no.nav.syfo.generateSykmelding
import no.nav.syfo.model.sykmelding.RuleInfo
import no.nav.syfo.model.sykmelding.Status
import no.nav.syfo.model.sykmelding.ValidationResult
import no.nav.syfo.receivedSykmelding
import org.amshove.kluent.shouldBeEqualTo

class SkalIkkeProdusereManuellOppgaveTest :
    FunSpec({
        context("Testing av metoden skalIkkeProdusereManuellOppgave") {
            test(
                "Skal ikke oppdatere infotrygd når sykmelding er delvis overlappende med tidligere periode og sammenlagt periode for ny sykmelding er tre dager eller mindre"
            ) {
                val validationResult =
                    ValidationResult(
                        status = Status.MANUAL_PROCESSING,
                        ruleHits =
                            listOf(
                                RuleInfo(
                                    ruleName =
                                        "PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE",
                                    messageForUser = "messageForSender",
                                    messageForSender = "messageForUser",
                                    ruleStatus = Status.MANUAL_PROCESSING,
                                ),
                                RuleInfo(
                                    ruleName = "NUMBER_OF_TREATMENT_DAYS_SET",
                                    messageForUser =
                                        "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
                                    messageForSender =
                                        "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
                                    ruleStatus = Status.MANUAL_PROCESSING,
                                ),
                            ),
                    )
                val receivedSykmelding =
                    receivedSykmelding(
                        "1",
                        generateSykmelding(
                            perioder =
                                listOf(
                                    generatePeriode(
                                        fom = LocalDate.of(2019, 1, 1),
                                        tom = LocalDate.of(2019, 1, 4),
                                    ),
                                ),
                        ),
                    )

                skalIkkeProdusereManuellOppgave(
                    receivedSykmelding,
                    validationResult
                ) shouldBeEqualTo true
            }

            test(
                "Skal oppdatere infotrygd når sykmelding ikke overlapper med tidligere periode og sammenlagt periode for ny sykmelding er mer enn tre dager"
            ) {
                val validationResult =
                    ValidationResult(
                        status = Status.MANUAL_PROCESSING,
                        ruleHits =
                            listOf(
                                RuleInfo(
                                    ruleName = "PERIOD_IS_AF",
                                    messageForUser = "messageForSender",
                                    messageForSender = "messageForUser",
                                    ruleStatus = Status.MANUAL_PROCESSING,
                                ),
                                RuleInfo(
                                    ruleName = "TRAVEL_SUBSIDY_SPECIFIED",
                                    messageForUser = "messageForSender",
                                    messageForSender = "messageForUser",
                                    ruleStatus = Status.MANUAL_PROCESSING,
                                ),
                            ),
                    )
                val receivedSykmelding =
                    receivedSykmelding(
                        "1",
                        generateSykmelding(
                            perioder =
                                listOf(
                                    generatePeriode(
                                        fom = LocalDate.of(2019, 1, 1),
                                        tom = LocalDate.of(2019, 1, 5),
                                    ),
                                ),
                        ),
                    )

                skalIkkeProdusereManuellOppgave(
                    receivedSykmelding,
                    validationResult
                ) shouldBeEqualTo false
            }

            test(
                "Skal oppdatere infotrygd når sykmelding er delvis overlappende med tidligere periode og sammenlagt periode for ny sykmelding er mer enn tre dager"
            ) {
                val validationResult =
                    ValidationResult(
                        status = Status.MANUAL_PROCESSING,
                        ruleHits =
                            listOf(
                                RuleInfo(
                                    ruleName =
                                        "PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE",
                                    messageForUser = "messageForSender",
                                    messageForSender = "messageForUser",
                                    ruleStatus = Status.MANUAL_PROCESSING,
                                ),
                                RuleInfo(
                                    ruleName = "TRAVEL_SUBSIDY_SPECIFIED",
                                    messageForUser = "messageForSender",
                                    messageForSender = "messageForUser",
                                    ruleStatus = Status.MANUAL_PROCESSING,
                                ),
                            ),
                    )
                val receivedSykmelding =
                    receivedSykmelding(
                        "1",
                        generateSykmelding(
                            perioder =
                                listOf(
                                    generatePeriode(
                                        fom = LocalDate.of(2019, 1, 1),
                                        tom = LocalDate.of(2019, 1, 5),
                                    ),
                                ),
                        ),
                    )

                skalIkkeProdusereManuellOppgave(
                    receivedSykmelding,
                    validationResult
                ) shouldBeEqualTo false
            }
        }
    })
