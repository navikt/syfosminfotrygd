package no.nav.syfo

import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.model.OpprettOppgaveKafkaMessage
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.services.BehandlingsutfallService
import no.nav.syfo.services.RedisService
import no.nav.syfo.services.UpdateInfotrygdService
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer

class ErrorFromInfotrygdSpek : FunSpec({
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

    context("Tester at vi fanger opp der infotrygd gir mye error") {
        test("Should set errorFromInfotrygd to true") {
            val rules = listOf(
                RuleInfo(
                    ruleName = "ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING",
                    messageForSender = "messageForSender",
                    messageForUser = "messageforUser ",
                    ruleStatus = Status.MANUAL_PROCESSING
                ),
                RuleInfo(
                    ruleName = "ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING",
                    messageForSender = "messageForSender",
                    messageForUser = "messageForUser",
                    ruleStatus = Status.MANUAL_PROCESSING
                )
            )

            updateInfotrygdService.errorFromInfotrygd(rules) shouldBeEqualTo true
        }

        test("Should set errorFromInfotrygd to false") {
            val rules = listOf(
                RuleInfo(
                    ruleName = "TRAVEL_SUBSIDY_SPECIFIED",
                    messageForSender = "messageForSender",
                    messageForUser = "messageForUser",
                    ruleStatus = Status.MANUAL_PROCESSING
                ),
                RuleInfo(
                    ruleName = "PERIOD_IS_AF",
                    messageForSender = "messageForSender",
                    messageForUser = "messageForUser",
                    ruleStatus = Status.MANUAL_PROCESSING
                )
            )

            updateInfotrygdService.errorFromInfotrygd(rules) shouldBeEqualTo false
        }

        test("Should set errorFromInfotrygd to true") {
            val rules = listOf(
                RuleInfo(
                    ruleName = "TRAVEL_SUBSIDY_SPECIFIED",
                    messageForSender = "messageForSender",
                    messageForUser = "messageForUser",
                    ruleStatus = Status.MANUAL_PROCESSING
                ),
                RuleInfo(
                    ruleName = "ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING",
                    messageForSender = "messageForSender",
                    messageForUser = "messageForUser",
                    ruleStatus = Status.MANUAL_PROCESSING
                )
            )

            updateInfotrygdService.errorFromInfotrygd(rules) shouldBeEqualTo true
        }
    }
})
