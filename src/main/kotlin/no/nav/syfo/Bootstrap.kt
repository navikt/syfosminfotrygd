package no.nav.syfo

import io.ktor.application.Application
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.kith.xmlstds.msghead._2006_05_24.XMLMsgHead
import no.kith.xmlstds.msghead._2006_05_24.XMLOrganisation
import no.kith.xmlstds.msghead._2006_05_24.XMLRefDoc
import no.nav.helse.sm2013.EIFellesformat
import no.nav.helse.sm2013.KontrollSystemBlokk
import no.nav.helse.sm2013.KontrollsystemBlokkType
import no.nav.model.infotrygdSporing.InfotrygdForesp
import no.nav.model.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.model.Status
import no.nav.syfo.rules.postInfotrygdQueryChain
import no.trygdeetaten.xml.eiff._1.XMLMottakenhetBlokk
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.StringReader
import java.io.StringWriter
import java.time.Duration
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.concurrent.TimeUnit
import javax.jms.MessageProducer
import javax.jms.Session
import javax.jms.TemporaryQueue
import javax.jms.TextMessage
import javax.xml.bind.Marshaller

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.sminfotrygd")

fun main(args: Array<String>) = runBlocking {
    val env = Environment()
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    try {
        val listeners = (1..env.applicationThreads).map {
            launch {
                val consumerProperties = readConsumerConfig(env, valueDeserializer = StringDeserializer::class)
                val producerProperties = readProducerConfig(env, valueSerializer = StringSerializer::class)
                val kafkaconsumer = KafkaConsumer<String, String>(consumerProperties)
                kafkaconsumer.subscribe(listOf(env.sm2013AutomaticHandlingTopic, env.smPaperAutomaticHandlingTopic))
                val kafkaproducer = KafkaProducer<String, String>(producerProperties)

                connectionFactory(env).createConnection(env.mqUsername, env.mqPassword).use { connection ->
                    connection.start()

                    val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
                    val infotrygdOppdateringQueue = session.createQueue("queue:///${env.infotrygdOppdateringQueue}?targetClient=1")
                    val infotrygdSporringQueue = session.createQueue("queue:///${env.infotrygdSporringQueue}?targetClient=1")
                    val infotrygdOppdateringProducer = session.createProducer(infotrygdOppdateringQueue)
                    val infotrygdSporringProducer = session.createProducer(infotrygdSporringQueue)

                    blockingApplicationLogic(applicationState, kafkaconsumer, kafkaproducer, env, infotrygdOppdateringProducer, infotrygdSporringProducer, session)
                }
            }
        }.toList()

        applicationState.initialized = true

        Runtime.getRuntime().addShutdownHook(Thread {
            applicationServer.stop(10, 10, TimeUnit.SECONDS)
        })
        runBlocking { listeners.forEach { it.join() } }
    } finally {
        applicationState.running = false
    }
}

suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, String>,
    kafkaProducer: KafkaProducer<String, String>,
    env: Environment,
    infotrygdOppdateringProducer: MessageProducer,
    infotrygdSporringProducer: MessageProducer,
    session: Session
) {
    while (applicationState.running) {
        kafkaConsumer.poll(Duration.ofMillis(0)).forEach {
            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(it.value())) as EIFellesformat
            val msgHead: XMLMsgHead = fellesformat.get()
            val mottakEnhetBlokk: XMLMottakenhetBlokk = fellesformat.get()
            val healthInformation = extractHelseopplysninger(msgHead)
            val logValues = arrayOf(
                    keyValue("smId", mottakEnhetBlokk.ediLoggId),
                    keyValue("msgId", msgHead.msgInfo.msgId),
                    keyValue("orgNr", msgHead.msgInfo.sender.organisation.extractOrganizationNumber())
            )
            val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") { "{}" }
            log.info("Received a SM2013, going through rules and persisting in infotrygd $logKeys", *logValues)

            val infotrygdForespRequest = createInfotrygdForesp(healthInformation)
            val temporaryQueue = session.createTemporaryQueue()
            sendInfotrygdSporring(infotrygdSporringProducer, session, infotrygdForespRequest, temporaryQueue)
            val tmpConsumer = session.createConsumer(temporaryQueue)
            val consumedMessage = tmpConsumer.receive(15000)
            val inputMessageText = when (consumedMessage) {
                is TextMessage -> consumedMessage.text
                else -> throw RuntimeException("Incoming message needs to be a byte message or text message, JMS type:" + consumedMessage.jmsType)
            }
            log.info("Message is read, from tmp que")
            log.info("Message response from IT: + $inputMessageText")

            val infotrygdForespResponse = infotrygdSporringUnmarshaller.unmarshal(StringReader(inputMessageText)) as InfotrygdForesp

            log.info("Start rule flow")
            val results = listOf(
                    postInfotrygdQueryChain.executeFlow(InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation))
            ).flatMap { it }

            log.info("Finish rule flow")

            log.info("Outcomes: " + results.joinToString(", ", prefix = "\"", postfix = "\""))

            if (results.any { it.outcomeType.status == Status.MANUAL_PROCESSING }) {
                // TODO send to GSAK kafkaProducer.send(ProducerRecord(env.smInfotrygdManualHandlingTopic, fellesformatMarshaller.toString(fellesformat)))
            } else if (results.any { it.outcomeType.status == Status.INVALID }) {
                // TODO Send apprec to EPJ
            } else {
                log.info("SM2013 updated in Infotrygd")

                sendInfotrygdOppdatering(infotrygdOppdateringProducer, session, createInfotrygdInfo)
        }
        }
        delay(100)
    }
}

data class InfotrygdForespAndHealthInformation(
    val infotrygdForesp: InfotrygdForesp,
    val healthInformation: HelseOpplysningerArbeidsuforhet
)

fun XMLOrganisation.extractOrganizationNumber(): String? = ident.find { it.typeId.v == "ENH" }?.id

inline fun <reified T> EIFellesformat.get(): T = any.find { it is T } as T

inline fun <reified T> XMLRefDoc.Content.get() = this.any.find { it is T } as T
fun extractHelseopplysninger(msgHead: XMLMsgHead) = msgHead.document[0].refDoc.content.get<HelseOpplysningerArbeidsuforhet>()

fun Application.initRouting(applicationState: ApplicationState) {
    routing {
        registerNaisApi(
                readynessCheck = {
                    applicationState.initialized
                },
                livenessCheck = {
                    applicationState.running
                }
        )
    }
}

fun Marshaller.toString(input: Any): String = StringWriter().use {
    marshal(input, it)
    it.toString()
}

fun sendInfotrygdSporring(
    producer: MessageProducer,
    session: Session,
    infotrygdForesp: InfotrygdForesp,
    temporaryQueue: TemporaryQueue
) = producer.send(session.createTextMessage().apply {
    val info = infotrygdForesp
    text = infotrygdSporringMarshaller.toString(info)
    log.info("text sendt to Infotrygd + $text")
    jmsReplyTo = temporaryQueue
})

fun sendInfotrygdOppdatering(
    producer: MessageProducer,
    session: Session,
    fellesformat: EIFellesformat
) = producer.send(session.createTextMessage().apply {
    val info = fellesformat
    text = fellesformatMarshaller.toString(info)
    log.info("text sendt to Infotrygd + $text")
})

fun createInfotrygdForesp(healthInformation: HelseOpplysningerArbeidsuforhet) = InfotrygdForesp().apply {
    val gregorianCalendarMinus1Year = GregorianCalendar()
    gregorianCalendarMinus1Year.add(Calendar.YEAR, -1)

    val gregorianCalendarMinus4Year = GregorianCalendar()
    gregorianCalendarMinus4Year.add(Calendar.YEAR, -4)

    fodselsnummer = healthInformation.pasient.fodselsnummer.id
    tkNrFraDato = newInstance.newXMLGregorianCalendar(gregorianCalendarMinus1Year)
    forespNr = 1.toBigInteger()
    forespTidsStempel = newInstance.newXMLGregorianCalendar(GregorianCalendar())
    fraDato = newInstance.newXMLGregorianCalendar(gregorianCalendarMinus1Year)
    eldsteFraDato = newInstance.newXMLGregorianCalendar(gregorianCalendarMinus4Year)
    hovedDiagnosekode = healthInformation.medisinskVurdering.hovedDiagnose.diagnosekode.v
    hovedDiagnosekodeverk = Diagnosekode.values().filter {
            it.kithCode == healthInformation.medisinskVurdering.hovedDiagnose.diagnosekode.s
        }.first().infotrygdCode
    fodselsnrBehandler = healthInformation.behandler.id.filter {
        it.typeId.v == "FNR"
    }.first().id // TODO maybe get the fnr from xmlmsg its mandatorie to be there
    if (healthInformation.medisinskVurdering.biDiagnoser?.diagnosekode?.firstOrNull()?.v != null &&
            healthInformation.medisinskVurdering.biDiagnoser?.diagnosekode?.firstOrNull()?.s != null) {
        biDiagnoseKode = healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.first().v
        biDiagnosekodeverk = Diagnosekode.values().filter {
            it.kithCode == healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.first().s
        }.first().infotrygdCode
    }
    tkNrFraDato = newInstance.newXMLGregorianCalendar(gregorianCalendarMinus1Year)
}

fun createInfotrygdInfo(fellesformat: EIFellesformat, it: InfotrygdForespAndHealthInformation) = fellesformat.apply { (KontrollSystemBlokk().apply {
    infotrygdBlokk.add(KontrollsystemBlokkType.InfotrygdBlokk().apply {
        fodselsnummer = it.healthInformation.pasient.fodselsnummer.id
        tkNummer = ""
        forsteFravaersDag = it.healthInformation.aktivitet.periode.firstOrNull()?.periodeFOMDato
        mottakerKode = it.infotrygdForesp.behandlerInfo.behandler.first().mottakerKode.value()
        operasjonstype = "3".toBigInteger()
        hovedDiagnose = it.infotrygdForesp.hovedDiagnosekode
        hovedDiagnoseGruppe = it.infotrygdForesp.hovedDiagnosekodeverk.toBigInteger()
        hovedDiagnoseTekst = it.healthInformation.medisinskVurdering.hovedDiagnose.diagnosekode.dn
        arbeidsufoerTOM = it.healthInformation.aktivitet.periode.first().periodeTOMDato
        ufoeregrad = when (it.healthInformation.aktivitet.periode.firstOrNull()?.gradertSykmelding) {
            null -> "100".toBigInteger()
            else -> it.healthInformation.aktivitet.periode.first().gradertSykmelding.sykmeldingsgrad.toBigInteger()
        }
    })
}) }