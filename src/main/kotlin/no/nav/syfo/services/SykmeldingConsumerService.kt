package no.nav.syfo.services

import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.jms.Connection
import jakarta.jms.MessageProducer
import jakarta.jms.Session
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import no.nav.syfo.Environment
import no.nav.syfo.ServiceUser
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.getCurrentTime
import no.nav.syfo.log
import no.nav.syfo.model.sykmelding.ReceivedSykmelding
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.objectMapper
import no.nav.syfo.services.updateinfotrygd.exception.InfotrygdDownException
import no.nav.syfo.shouldRun
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.consumer.KafkaConsumer

class SykmeldingConsumerService(
    private val mottattSykmeldingService: MottattSykmeldingService,
    private val environment: Environment,
    private val serviceUser: ServiceUser,
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val applicationState: ApplicationState,
) {
    companion object {
        private const val DELAY_ON_ERROR_SECONDS = 60L
    }

    suspend fun startConsumer() =
        withContext(Dispatchers.IO) {
            while (applicationState.ready && isActive) {
                var mqConnection: Connection? = null
                var session: Session? = null
                var infotrygdOppdateringProducer: MessageProducer? = null
                var infotrygdSporringProducer: MessageProducer? = null
                var delayTime: Duration

                if (!shouldRun(getCurrentTime())) {
                    log.info("We are not running the consumer, waiting until next time")
                    delay(getDelay(getCurrentTime().toLocalDateTime()))
                }

                while (applicationState.ready && shouldRun(getCurrentTime())) {
                    try {
                        delayTime = 0.seconds
                        mqConnection =
                            connectionFactory(environment)
                                .createConnection(
                                    serviceUser.serviceuserUsername,
                                    serviceUser.serviceuserPassword
                                )
                        mqConnection.start()
                        session = mqConnection.createSession(false, Session.AUTO_ACKNOWLEDGE)
                        infotrygdOppdateringProducer =
                            session.producerForQueue(
                                "queue:///${environment.infotrygdOppdateringQueue}?targetClient=1",
                            )
                        infotrygdSporringProducer =
                            session.producerForQueue(
                                "queue:///${environment.infotrygdSporringQueue}?targetClient=1",
                            )
                        kafkaConsumer.subscribe(
                            listOf(environment.okSykmeldingTopic, environment.retryTopic)
                        )
                        runConsumer(
                            infotrygdSporringProducer,
                            infotrygdOppdateringProducer,
                            session
                        )
                    } catch (ex: InfotrygdDownException) {
                        val currentTime = getCurrentTime().toLocalDateTime()
                        delayTime = getDelay(currentTime)
                        log.warn(
                            "Infotrygd is down?, unsubscribing and dalaying for $delayTime",
                            ex
                        )
                    } catch (cancellationException: CancellationException) {
                        log.info(
                            "Coroutine was cancelled, cancelling mq connection and exitting loop",
                            cancellationException
                        )
                        throw cancellationException
                    } catch (ex: Exception) {
                        log.error(
                            "Error running consumer, unsubscribing and waiting 60 seconds for retry",
                            ex
                        )
                        delayTime = DELAY_ON_ERROR_SECONDS.seconds
                    } finally {
                        withContext(NonCancellable) {
                            try {
                                kafkaConsumer.unsubscribe()
                            } catch (_: Exception) {}
                            try {
                                infotrygdOppdateringProducer?.close()
                            } catch (_: Exception) {}
                            try {
                                infotrygdSporringProducer?.close()
                            } catch (_: Exception) {}
                            try {
                                session?.close()
                            } catch (_: Exception) {}
                            try {
                                mqConnection?.close()
                            } catch (_: Exception) {}
                        }
                        mqConnection = null
                        session = null
                        infotrygdOppdateringProducer = null
                        infotrygdSporringProducer = null
                    }
                    delay(delayTime)
                }
            }
        }

    private fun getDelay(currentTime: LocalDateTime): Duration {
        return if (currentTime.hour !in 5..18) {
                val today = currentTime.toLocalDate().atTime(5, 0)
                if (currentTime.isAfter(today)) {
                    log.info("Time is after 18, Delaying until 05:00 tomorrow")
                    ChronoUnit.SECONDS.between(currentTime, today.plusDays(1))
                } else {
                    log.info("Time is before 05, Delaying until 05:00 today")
                    ChronoUnit.SECONDS.between(currentTime, today)
                }
            } else {
                log.info("Time is between 05:00 and 18:00, Delaying for 60 seconds")
                60
            }
            .seconds
    }

    private suspend fun runConsumer(
        infotrygdSporringProducer: MessageProducer,
        infotrygdOppdateringProducer: MessageProducer,
        session: Session
    ) =
        withContext(Dispatchers.IO) {
            while (applicationState.ready && shouldRun(getCurrentTime())) {
                kafkaConsumer
                    .poll(10.seconds.toJavaDuration())
                    .mapNotNull { record -> record.value() }
                    .forEach { sykmelding ->
                        val receivedSykmelding: ReceivedSykmelding =
                            objectMapper.readValue(sykmelding)
                        val loggingMeta =
                            LoggingMeta(
                                mottakId = receivedSykmelding.navLogId,
                                orgNr = receivedSykmelding.legekontorOrgNr,
                                msgId = receivedSykmelding.msgId,
                                sykmeldingId = receivedSykmelding.sykmelding.id,
                            )
                        mottattSykmeldingService.handleMessage(
                            receivedSykmelding = receivedSykmelding,
                            infotrygdOppdateringProducer = infotrygdOppdateringProducer,
                            infotrygdSporringProducer = infotrygdSporringProducer,
                            session = session,
                            loggingMeta = loggingMeta,
                        )
                    }
            }
        }
}
