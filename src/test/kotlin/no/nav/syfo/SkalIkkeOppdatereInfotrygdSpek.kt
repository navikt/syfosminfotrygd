package no.nav.syfo

import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.model.Gradert
import no.nav.syfo.model.Merknad
import no.nav.syfo.model.OpprettOppgaveKafkaMessage
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.services.BehandlingsutfallService
import no.nav.syfo.services.RedisService
import no.nav.syfo.services.UpdateInfotrygdService
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer
import java.time.LocalDate

class SkalIkkeOppdatereInfotrygdSpek : FunSpec({
    context("Skal ikke oppdatere infotrygd") {
        val sm = mockk<ReceivedSykmelding>()
        test("Skal oppdater infotrygd ved ingen merknader") {
            every { sm.merknader } returns emptyList()
            every { sm.sykmelding.perioder } returns emptyList()
            skalOppdatereInfotrygd(sm) shouldBeEqualTo true
        }
        test("Skal oppdater infotrygd ved ingen merknader") {
            every { sm.merknader } returns null
            every { sm.sykmelding.perioder } returns emptyList()
            skalOppdatereInfotrygd(sm) shouldBeEqualTo true
        }
        test("Skal ikke oppdatere infotrygd ved merknader UGYLDIG_TILBAKEDATERING") {
            every { sm.merknader } returns listOf(Merknad("UGYLDIG_TILBAKEDATERING", null))
            every { sm.sykmelding.perioder } returns emptyList()
            skalOppdatereInfotrygd(sm) shouldBeEqualTo false
        }
        test("Skal ikke oppdatere infotrygd ved merknader TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER") {
            every { sm.merknader } returns listOf(Merknad("TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER", null))
            every { sm.sykmelding.perioder } returns emptyList()
            skalOppdatereInfotrygd(sm) shouldBeEqualTo false
        }
        test("Skal ikke oppdatere infotrygd ved merknader TILBAKEDATERT_PAPIRSYKMELDING") {
            every { sm.merknader } returns listOf(Merknad("TILBAKEDATERT_PAPIRSYKMELDING", null))
            every { sm.sykmelding.perioder } returns emptyList()
            skalOppdatereInfotrygd(sm) shouldBeEqualTo false
        }
        test("Skal ikke oppdatere infotrygd ved merknader UNDER_BEHANDLING") {
            every { sm.merknader } returns listOf(Merknad("UNDER_BEHANDLING", null))
            every { sm.sykmelding.perioder } returns emptyList()
            skalOppdatereInfotrygd(sm) shouldBeEqualTo false
        }
        test("Skal oppdatere infotrygd ved annen merknader ANNEN_MERKNAD") {
            every { sm.merknader } returns listOf(Merknad("ANNEN_MERKNAD", null))
            every { sm.sykmelding.perioder } returns emptyList()
            skalOppdatereInfotrygd(sm) shouldBeEqualTo true
        }

        test("Skal ikke oppdatere infotrygd hvis sykmeldingen inneholder reisetilskudd") {
            every { sm.merknader } returns emptyList()
            every { sm.sykmelding } returns generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        reisetilskudd = true
                    )
                )
            )
            skalOppdatereInfotrygd(sm) shouldBeEqualTo false
        }

        test("Skal ikke oppdatere infotrygd hvis sykmeldingen inneholder gradert sykmelding med reisetilskudd") {
            every { sm.merknader } returns emptyList()
            every { sm.sykmelding } returns generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        reisetilskudd = false,
                        gradert = Gradert(true, 50)
                    )
                )
            )
            skalOppdatereInfotrygd(sm) shouldBeEqualTo false
        }
    }
    context("Testing av metoden skalIkkeOppdatereInfotrygd") {
        val norskHelsenettClient = mockk<NorskHelsenettClient>()
        val kafkaAivenProducerReceivedSykmelding = mockk<KafkaProducer<String, ReceivedSykmelding>>()
        val kafkaAivenProducerOppgave = mockk<KafkaProducer<String, OpprettOppgaveKafkaMessage>>()
        val behandlingsutfallService = mockk<BehandlingsutfallService>()
        val redisService = mockk<RedisService>()
        val updateInfotrygdService = UpdateInfotrygdService(
            norskHelsenettClient,
            ApplicationState(alive = true, ready = true),
            kafkaAivenProducerReceivedSykmelding,
            kafkaAivenProducerOppgave,
            "retry",
            "oppgave",
            behandlingsutfallService,
            redisService
        )

        test("Skal ikkje oppdatere infotrygd, pga lik eller under 3 dager i sykmeldings peridene totalt") {
            val validationResult = ValidationResult(
                status = Status.MANUAL_PROCESSING,
                ruleHits = listOf(
                    RuleInfo(
                        ruleName = "PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE",
                        messageForUser = "messageForSender",
                        messageForSender = "messageForUser",
                        ruleStatus = Status.MANUAL_PROCESSING
                    ),
                    RuleInfo(
                        ruleName = "NUMBER_OF_TREATMENT_DAYS_SET",
                        messageForUser = "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
                        messageForSender = "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
                        ruleStatus = Status.MANUAL_PROCESSING
                    )
                )
            )

            val receivedSykmelding = receivedSykmelding(
                "1",
                generateSykmelding(
                    perioder = listOf(
                        generatePeriode(
                            fom = LocalDate.of(2019, 1, 1),
                            tom = LocalDate.of(2019, 1, 4)
                        )
                    )
                )
            )

            updateInfotrygdService.skalIkkeProdusereManuellOppgave(receivedSykmelding, validationResult) shouldBeEqualTo true
        }

        test("Skal oppdatere infotrygd, pga større enn 3 dager i sykmeldings peridene totalt") {

            val validationResult = ValidationResult(
                status = Status.MANUAL_PROCESSING,
                ruleHits = listOf(
                    RuleInfo(
                        ruleName = "PERIOD_IS_AF",
                        messageForUser = "messageForSender",
                        messageForSender = "messageForUser",
                        ruleStatus = Status.MANUAL_PROCESSING
                    ),
                    RuleInfo(
                        ruleName = "TRAVEL_SUBSIDY_SPECIFIED",
                        messageForUser = "messageForSender",
                        messageForSender = "messageForUser",
                        ruleStatus = Status.MANUAL_PROCESSING
                    )
                )
            )

            val receivedSykmelding = receivedSykmelding(
                "1",
                generateSykmelding(
                    perioder = listOf(
                        generatePeriode(
                            fom = LocalDate.of(2019, 1, 1),
                            tom = LocalDate.of(2019, 1, 5)
                        )
                    )
                )
            )

            updateInfotrygdService.skalIkkeProdusereManuellOppgave(receivedSykmelding, validationResult) shouldBeEqualTo false
        }

        test("Skal oppdatere infotrygd, pga større enn 3 dager i sykmeldings peridene totalt") {

            val validationResult = ValidationResult(
                status = Status.MANUAL_PROCESSING,
                ruleHits = listOf(
                    RuleInfo(
                        ruleName = "PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE",
                        messageForUser = "messageForSender",
                        messageForSender = "messageForUser",
                        ruleStatus = Status.MANUAL_PROCESSING
                    ),
                    RuleInfo(
                        ruleName = "TRAVEL_SUBSIDY_SPECIFIED",
                        messageForUser = "messageForSender",
                        messageForSender = "messageForUser",
                        ruleStatus = Status.MANUAL_PROCESSING
                    )
                )
            )

            val receivedSykmelding = receivedSykmelding(
                "1",
                generateSykmelding(
                    perioder = listOf(
                        generatePeriode(
                            fom = LocalDate.of(2019, 1, 1),
                            tom = LocalDate.of(2019, 1, 5)
                        )
                    )
                )
            )

            updateInfotrygdService.skalIkkeProdusereManuellOppgave(receivedSykmelding, validationResult) shouldBeEqualTo false
        }
    }
})
