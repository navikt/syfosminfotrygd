package no.nav.syfo.services

import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import no.nav.syfo.generateSykmelding
import no.nav.syfo.model.OpprettOppgaveKafkaMessage
import no.nav.syfo.model.sykmelding.AktivitetIkkeMulig
import no.nav.syfo.model.sykmelding.MedisinskArsak
import no.nav.syfo.model.sykmelding.Periode
import no.nav.syfo.model.sykmelding.RuleInfo
import no.nav.syfo.model.sykmelding.Status
import no.nav.syfo.model.sykmelding.UtenlandskSykmelding
import no.nav.syfo.model.sykmelding.ValidationResult
import no.nav.syfo.receivedSykmelding
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer

class OppgaveServiceTest :
    FunSpec({
        val loggingMeta = LoggingMeta("", "", "", "")
        val kafkaAivenProducerOppgave = mockk<KafkaProducer<String, OpprettOppgaveKafkaMessage>>()

        val oppgaveService = OppgaveService(kafkaAivenProducerOppgave, "oppgave")

        context("Oppretter manuelle oppgaver med riktige parametre") {
            test("Behandlingstype er ae0256 hvis sykmelding har blitt behandlet av manuell") {
                val validationResults =
                    ValidationResult(
                        Status.MANUAL_PROCESSING,
                        listOf(
                            RuleInfo(
                                "TILBAKEDATERT_MER_ENN_8_DAGER_FORSTE_SYKMELDING_MED_BEGRUNNELSE",
                                "message for sender",
                                "message for user",
                                Status.MANUAL_PROCESSING
                            )
                        )
                    )
                val receivedSykmelding = receivedSykmelding(UUID.randomUUID().toString())

                val oppgave =
                    oppgaveService.opprettOpprettOppgaveKafkaMessage(
                        receivedSykmelding,
                        validationResults,
                        behandletAvManuell = true,
                        loggingMeta
                    )

                oppgave.behandlingstype shouldBeEqualTo "ae0256"
            }
            test("fristFerdigstillelse er i dag hvis sykmelding har blitt behandlet av manuell") {
                val validationResults =
                    ValidationResult(
                        Status.MANUAL_PROCESSING,
                        listOf(
                            RuleInfo(
                                "TILBAKEDATERT_MER_ENN_8_DAGER_FORSTE_SYKMELDING_MED_BEGRUNNELSE",
                                "message for sender",
                                "message for user",
                                Status.MANUAL_PROCESSING
                            )
                        )
                    )
                val receivedSykmelding = receivedSykmelding(UUID.randomUUID().toString())

                val oppgave =
                    oppgaveService.opprettOpprettOppgaveKafkaMessage(
                        receivedSykmelding,
                        validationResults,
                        behandletAvManuell = true,
                        loggingMeta
                    )

                oppgave.fristFerdigstillelse shouldBeEqualTo
                    DateTimeFormatter.ISO_DATE.format(LocalDate.now())
            }
            test(
                "Behandlingstype er ae0256 hvis sykmelding ikke har blitt behandlet av manuell, men er tilbakedatert"
            ) {
                val validationResults =
                    ValidationResult(
                        Status.MANUAL_PROCESSING,
                        listOf(
                            RuleInfo(
                                "ANNEN_REGEL",
                                "message for sender",
                                "message for user",
                                Status.MANUAL_PROCESSING
                            )
                        )
                    )
                val receivedSykmelding =
                    receivedSykmelding(
                        UUID.randomUUID().toString(),
                        sykmelding =
                            generateSykmelding(
                                behandletTidspunkt = LocalDateTime.now().minusDays(1),
                                perioder =
                                    listOf(
                                        Periode(
                                            fom = LocalDate.now().minusDays(10),
                                            tom = LocalDate.now().plusDays(2),
                                            aktivitetIkkeMulig =
                                                AktivitetIkkeMulig(
                                                    MedisinskArsak("", emptyList()),
                                                    null
                                                ),
                                            avventendeInnspillTilArbeidsgiver = null,
                                            behandlingsdager = null,
                                            gradert = null,
                                            reisetilskudd = false,
                                        ),
                                    ),
                            ),
                    )

                val oppgave =
                    oppgaveService.opprettOpprettOppgaveKafkaMessage(
                        receivedSykmelding,
                        validationResults,
                        behandletAvManuell = false,
                        loggingMeta
                    )

                oppgave.behandlingstype shouldBeEqualTo "ae0256"
            }
            test(
                "Behandlingstype er ANY hvis sykmelding ikke har blitt behandlet av manuell og ikke er tilbakedatert"
            ) {
                val validationResults =
                    ValidationResult(
                        Status.MANUAL_PROCESSING,
                        listOf(
                            RuleInfo(
                                "ANNEN_REGEL",
                                "message for sender",
                                "message for user",
                                Status.MANUAL_PROCESSING
                            )
                        )
                    )
                val receivedSykmelding = receivedSykmelding(UUID.randomUUID().toString())

                val oppgave =
                    oppgaveService.opprettOpprettOppgaveKafkaMessage(
                        receivedSykmelding,
                        validationResults,
                        behandletAvManuell = false,
                        loggingMeta
                    )

                oppgave.behandlingstype shouldBeEqualTo "ANY"
            }
            test("Behandlingstype er ae0106 hvis sykmeldingen er utenlandsk") {
                val validationResults =
                    ValidationResult(
                        Status.MANUAL_PROCESSING,
                        listOf(
                            RuleInfo(
                                "ANNEN_REGEL",
                                "message for sender",
                                "message for user",
                                Status.MANUAL_PROCESSING
                            )
                        )
                    )
                val receivedSykmelding =
                    receivedSykmelding(UUID.randomUUID().toString())
                        .copy(utenlandskSykmelding = UtenlandskSykmelding("GER", false, false))

                val oppgave =
                    oppgaveService.opprettOpprettOppgaveKafkaMessage(
                        receivedSykmelding,
                        validationResults,
                        behandletAvManuell = false,
                        loggingMeta
                    )

                oppgave.behandlingstype shouldBeEqualTo "ae0106"
            }
        }
    })
