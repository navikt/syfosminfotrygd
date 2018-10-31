package no.nav.syfo

import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.ktor.application.Application
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.kith.xmlstds.msghead._2006_05_24.XMLMsgHead
import no.kith.xmlstds.msghead._2006_05_24.XMLOrganisation
import no.kith.xmlstds.msghead._2006_05_24.XMLRefDoc
import no.nav.helse.sm2013.EIFellesformat
import no.nav.helse.sm2013.KontrollSystemBlokk
import no.nav.helse.sm2013.KontrollsystemBlokkType
import no.nav.model.infotrygdSporing.InfotrygdForesp
import no.nav.model.infotrygdSporing.TypeSMinfo
import no.nav.model.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.model.Status
import no.nav.syfo.rules.postInfotrygdQueryChain
import no.nav.syfo.sak.avro.ProduceTask
import no.trygdeetaten.xml.eiff._1.XMLMottakenhetBlokk
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.StringReader
import java.io.StringWriter
import java.math.BigInteger
import java.time.Duration
import java.time.LocalDate
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
                val producerProperties = readProducerConfig(env, valueSerializer = KafkaAvroSerializer::class)
                val kafkaconsumer = KafkaConsumer<String, String>(consumerProperties)
                kafkaconsumer.subscribe(listOf(env.sm2013AutomaticHandlingTopic, env.smPaperAutomaticHandlingTopic))
                val kafkaproducer = KafkaProducer<String, ProduceTask>(producerProperties)

                connectionFactory(env).createConnection(env.mqUsername, env.mqPassword).use { connection ->
                    connection.start()
                    log.info("check that vault works:  ${env.srvsminfotrygdUsername}")

                    val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
                    val infotrygdOppdateringQueue = session.createQueue("queue:///${env.infotrygdOppdateringQueue}?targetClient=1")
                    val infotrygdSporringQueue = session.createQueue("queue:///${env.infotrygdSporringQueue}?targetClient=1")
                    val infotrygdOppdateringProducer = session.createProducer(infotrygdOppdateringQueue)
                    val infotrygdSporringProducer = session.createProducer(infotrygdSporringQueue)

                    blockingApplicationLogic(applicationState, kafkaconsumer, kafkaproducer, infotrygdOppdateringProducer, infotrygdSporringProducer, session)
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
    kafkaProducer: KafkaProducer<String, ProduceTask>,
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
            log.info("Query Infotrygd response: + $inputMessageText $logKeys", *logValues)

            val infotrygdForespResponse = infotrygdSporringUnmarshaller.unmarshal(StringReader(inputMessageText)) as InfotrygdForesp

            val results = listOf(
                    postInfotrygdQueryChain.executeFlow(InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation))
            ).flatMap { it }

            when (results.joinToString(", ", prefix = "\"", postfix = "\"")) {
                "" -> log.info("Zero outcomes")
                else -> log.info("Outcomes: " + results.joinToString(", ", prefix = "\"", postfix = "\""))
            }

            if (results.any { it.outcomeType.status == Status.MANUAL_PROCESSING }) {
                kafkaProducer.send(ProducerRecord("aapen-syfo-oppgave-produserOppgave", ProduceTask().apply {
                    setMessageId(msgHead.msgInfo.msgId)
                    setUserIdent(healthInformation.pasient.fodselsnummer.id)
                    setUserTypeCode("PERSON")
                    setTaskType("SYK")
                    setFieldCode("SYK")
                    setSubcategoryType("SYK_SYK")
                    setPriorityCode("NORM_SYK")
                    setDescription("Kunne ikkje oppdatere Infotrygd automatisk, på grunn av følgende: ${results.joinToString(", ", prefix = "\"", postfix = "\"")}")
                    setStartsInDays(0)
                    setEndsInDays(21)
                    setResponsibleUnit("") // TODO the rules should send the NAV office that is found on the person
                    setFollowUpText("") // TODO
                }))
            } else if (results.any { it.outcomeType.status == Status.INVALID }) {
                // TODO Send apprec to EPJ
            } else {
                sendInfotrygdOppdatering(infotrygdOppdateringProducer, session, createInfotrygdInfo(fellesformat, InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation)))
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
    log.info("Query Infotrygd request, text sendt to Infotrygd + $text")
    jmsReplyTo = temporaryQueue
})

fun sendInfotrygdOppdatering(
    producer: MessageProducer,
    session: Session,
    fellesformat: EIFellesformat
) = producer.send(session.createTextMessage().apply {
    val info = fellesformat
    text = fellesformatMarshaller.toString(info)
    log.info("Updateing Infotrygd, text sendt to Infotrygd + $text")
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

fun createInfotrygdInfo(fellesformat: EIFellesformat, itfh: InfotrygdForespAndHealthInformation) = fellesformat.apply { any.add(KontrollSystemBlokk().apply {
    itfh.healthInformation.aktivitet.periode.forEachIndexed { index, periode ->
    infotrygdBlokk.add(KontrollsystemBlokkType.InfotrygdBlokk().apply {
        fodselsnummer = itfh.healthInformation.pasient.fodselsnummer.id
        tkNummer = ""
        forsteFravaersDag = periode.periodeFOMDato
        mottakerKode = itfh.infotrygdForesp.behandlerInfo.behandler.first().mottakerKode.value()
        operasjonstype = when (index) {
            0 -> findOprasjonstype(periode, itfh, itfh.infotrygdForesp.sMhistorikk.sykmelding.first())
            else -> "2".toBigInteger()
        }
        hovedDiagnose = itfh.infotrygdForesp.hovedDiagnosekode
        hovedDiagnoseGruppe = itfh.infotrygdForesp.hovedDiagnosekodeverk.toBigInteger()
        hovedDiagnoseTekst = itfh.healthInformation.medisinskVurdering.hovedDiagnose.diagnosekode.dn
        arbeidsufoerTOM = periode.periodeTOMDato
        ufoeregrad = when (periode.aktivitetIkkeMulig) {
            null -> periode.gradertSykmelding.sykmeldingsgrad.toBigInteger()
            else -> "100".toBigInteger()
            }
        })
        }
    })
}

fun findOprasjonstype(periode: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode, itfh: InfotrygdForespAndHealthInformation, typeSMinfo: TypeSMinfo): BigInteger {
    val infotrygdforespSmHistFinnes: Boolean = itfh.infotrygdForesp.sMhistorikk.status.kodeMelding != "04"
    val sMhistorikkArbuforFOM: LocalDate? = typeSMinfo.periode?.arbufoerFOM?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
    val sMhistorikkArbuforTOM: LocalDate? = typeSMinfo.periode?.arbufoerTOM?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
    val syketilfelleStartDatoFraSykemelding = itfh.healthInformation.syketilfelleStartDato
    val healthInformationPeriodeTOMDato: LocalDate? = periode.periodeTOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()

    if (!infotrygdforespSmHistFinnes && syketilfelleStartDatoFraSykemelding.equals(periode.periodeFOMDato)) {
        return "1".toBigInteger()
    } else if (infotrygdforespSmHistFinnes && periode.periodeFOMDato.equals(sMhistorikkArbuforFOM) && periode.periodeTOMDato.equals(sMhistorikkArbuforTOM)) {
        return "2".toBigInteger()
    } else if (infotrygdforespSmHistFinnes && sMhistorikkArbuforFOM != null && sMhistorikkArbuforTOM != null && sMhistorikkArbuforTOM.equals(periode.periodeTOMDato) || sMhistorikkArbuforTOM?.isBefore(healthInformationPeriodeTOMDato) != null) {
        return "3".toBigInteger()
    } else {
        throw RuntimeException("Could not determined operasjonstype")
    }
}
