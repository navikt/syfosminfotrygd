package no.nav.syfo

import io.mockk.mockk
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.client.ManuellClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.model.OpprettOppgaveKafkaMessage
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.rules.ValidationRuleChain
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.services.BehandlingsutfallService
import no.nav.syfo.services.UpdateInfotrygdService
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ErrorFromInfotrygdSpek : Spek({
    val manuellClient = mockk<ManuellClient>()
    val norskHelsenettClient = mockk<NorskHelsenettClient>()
    val kafkaproducerCreateTask = mockk<KafkaProducer<String, ProduceTask>>()
    val kafkaproducerreceivedSykmelding = mockk<KafkaProducer<String, ReceivedSykmelding>>()
    val kafkaAivenProducerReceivedSykmelding = mockk<KafkaProducer<String, ReceivedSykmelding>>()
    val kafkaAivenProducerOppgave = mockk<KafkaProducer<String, OpprettOppgaveKafkaMessage>>()
    val behandlingsutfallService = mockk<BehandlingsutfallService>()
    val updateInfotrygdService = UpdateInfotrygdService(
        manuellClient,
        norskHelsenettClient,
        kafkaproducerCreateTask,
        kafkaproducerreceivedSykmelding,
        "retry",
        "oppgave",
        ApplicationState(alive = true, ready = true),
        kafkaAivenProducerReceivedSykmelding,
        kafkaAivenProducerOppgave,
        "retry",
        "oppgave",
        behandlingsutfallService
    )

    describe("Tester at vi fanger opp der infotrygd gir mye error") {
        it("Should set errorFromInfotrygd to true") {
            val rules = listOf(
                RuleInfo(
                    ruleName = ValidationRuleChain.ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING.name,
                    messageForSender = ValidationRuleChain.ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING.messageForSender,
                    messageForUser = ValidationRuleChain.ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING.messageForUser,
                    ruleStatus = Status.MANUAL_PROCESSING
                ),
                RuleInfo(
                    ruleName = ValidationRuleChain.ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING.name,
                    messageForSender = ValidationRuleChain.ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING.messageForSender,
                    messageForUser = ValidationRuleChain.ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING.messageForUser,
                    ruleStatus = Status.MANUAL_PROCESSING
                )
            )

            updateInfotrygdService.errorFromInfotrygd(rules) shouldBeEqualTo true
        }

        it("Should set errorFromInfotrygd to false") {
            val rules = listOf(
                RuleInfo(
                    ruleName = ValidationRuleChain.TRAVEL_SUBSIDY_SPECIFIED.name,
                    messageForSender = ValidationRuleChain.TRAVEL_SUBSIDY_SPECIFIED.messageForSender,
                    messageForUser = ValidationRuleChain.TRAVEL_SUBSIDY_SPECIFIED.messageForUser,
                    ruleStatus = Status.MANUAL_PROCESSING
                ),
                RuleInfo(
                    ruleName = ValidationRuleChain.PERIOD_IS_AF.name,
                    messageForSender = ValidationRuleChain.PERIOD_IS_AF.messageForSender,
                    messageForUser = ValidationRuleChain.PERIOD_IS_AF.messageForUser,
                    ruleStatus = Status.MANUAL_PROCESSING
                )
            )

            updateInfotrygdService.errorFromInfotrygd(rules) shouldBeEqualTo false
        }

        it("Should set errorFromInfotrygd to true") {
            val rules = listOf(
                RuleInfo(
                    ruleName = ValidationRuleChain.TRAVEL_SUBSIDY_SPECIFIED.name,
                    messageForSender = ValidationRuleChain.TRAVEL_SUBSIDY_SPECIFIED.messageForSender,
                    messageForUser = ValidationRuleChain.TRAVEL_SUBSIDY_SPECIFIED.messageForUser,
                    ruleStatus = Status.MANUAL_PROCESSING
                ),
                RuleInfo(
                    ruleName = ValidationRuleChain.ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING.name,
                    messageForSender = ValidationRuleChain.ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING.messageForSender,
                    messageForUser = ValidationRuleChain.ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING.messageForUser,
                    ruleStatus = Status.MANUAL_PROCESSING
                )
            )

            updateInfotrygdService.errorFromInfotrygd(rules) shouldBeEqualTo true
        }
    }
})
