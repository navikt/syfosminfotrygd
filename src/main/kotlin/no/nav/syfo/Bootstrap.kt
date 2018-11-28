package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.ktor.application.Application
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArgument
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.kith.xmlstds.msghead._2006_05_24.XMLMsgHead
import no.kith.xmlstds.msghead._2006_05_24.XMLOrganisation
import no.kith.xmlstds.msghead._2006_05_24.XMLRefDoc
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.helse.sm2013.EIFellesformat
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.KontrollSystemBlokk
import no.nav.helse.sm2013.KontrollsystemBlokkType
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.rules.RuleData
import no.nav.syfo.rules.ValidationRules
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.ws.configureSTSFor
import no.nav.tjeneste.virksomhet.organisasjonenhet.v2.binding.OrganisasjonEnhetV2
import no.nav.tjeneste.virksomhet.organisasjonenhet.v2.informasjon.Geografi
import no.nav.tjeneste.virksomhet.organisasjonenhet.v2.meldinger.FinnNAVKontorRequest
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Personidenter
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningRequest
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.jms.MessageProducer
import javax.jms.Session
import javax.jms.TemporaryQueue
import javax.jms.TextMessage
import javax.xml.bind.Marshaller
import javax.xml.stream.XMLInputFactory

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.sminfotrygd")
val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

fun main(args: Array<String>) = runBlocking(Executors.newFixedThreadPool(2).asCoroutineDispatcher()) {
    val env = Environment()
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    connectionFactory(env).createConnection(env.mqUsername, env.mqPassword).use { connection ->
        connection.start()

        try {
            val listeners = (1..env.applicationThreads).map {
                launch {
                    val consumerProperties = readConsumerConfig(env, valueDeserializer = StringDeserializer::class)
                    val producerProperties = readProducerConfig(env, valueSerializer = KafkaAvroSerializer::class)
                    val kafkaconsumer = KafkaConsumer<String, String>(consumerProperties)
                    kafkaconsumer.subscribe(listOf(env.sm2013AutomaticHandlingTopic, env.smPaperAutomaticHandlingTopic))
                    val kafkaproducer = KafkaProducer<String, ProduceTask>(producerProperties)
                    val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
                    val infotrygdOppdateringQueue = session.createQueue("queue:///${env.infotrygdOppdateringQueue}?targetClient=1")
                    val infotrygdSporringQueue = session.createQueue("queue:///${env.infotrygdSporringQueue}?targetClient=1")
                    val infotrygdOppdateringProducer = session.createProducer(infotrygdOppdateringQueue)
                    val infotrygdSporringProducer = session.createProducer(infotrygdSporringQueue)

                    val personV3 = JaxWsProxyFactoryBean().apply {
                        address = env.personV3EndpointURL
                        serviceClass = PersonV3::class.java
                    }.create() as PersonV3
                    configureSTSFor(personV3, env.srvsminfotrygdUsername,
                            env.srvsminfotrygdPassword, env.securityTokenServiceUrl)

                    val orgnaisasjonEnhet = JaxWsProxyFactoryBean().apply {
                        address = env.organisasjonEnhetV2EndpointURL
                        serviceClass = OrganisasjonEnhetV2::class.java
                    }.create() as OrganisasjonEnhetV2
                    configureSTSFor(orgnaisasjonEnhet, env.srvsminfotrygdUsername,
                            env.srvsminfotrygdPassword, env.securityTokenServiceUrl)

                    blockingApplicationLogic(applicationState, kafkaconsumer, kafkaproducer, infotrygdOppdateringProducer, infotrygdSporringProducer, session, personV3, orgnaisasjonEnhet)
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
}

suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, String>,
    kafkaProducer: KafkaProducer<String, ProduceTask>,
    infotrygdOppdateringProducer: MessageProducer,
    infotrygdSporringProducer: MessageProducer,
    session: Session,
    personV3: PersonV3,
    organisasjonEnhetV2: OrganisasjonEnhetV2
) {
    while (applicationState.running) {
        kafkaConsumer.poll(Duration.ofMillis(0)).forEach {
            val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(it.value())
            val logValues = arrayOf(
                    keyValue("smId", receivedSykmelding.navLogId),
                    keyValue("msgId", receivedSykmelding.msgId),
                    keyValue("orgNr", receivedSykmelding.legekontorOrgNr)
            )
            val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") { "{}" }
            log.info("Received a SM2013, going through rules and persisting in infotrygd $logKeys", *logValues)

            val infotrygdForespRequest = createInfotrygdForesp(receivedSykmelding.sykmelding)
            val temporaryQueue = session.createTemporaryQueue()
            sendInfotrygdSporring(infotrygdSporringProducer, session, infotrygdForespRequest, temporaryQueue)
            val tmpConsumer = session.createConsumer(temporaryQueue)
            val consumedMessage = tmpConsumer.receive(15000)
            val inputMessageText = when (consumedMessage) {
                is TextMessage -> consumedMessage.text
                else -> throw RuntimeException("Incoming message needs to be a byte message or text message, JMS type:" + consumedMessage.jmsType)
            }
            val infotrygdForespResponse = infotrygdSporringUnmarshaller.unmarshal(StringReader(inputMessageText)) as InfotrygdForesp

            log.info("Executing Infotrygd rules $logKeys", *logValues)
            val ruleData = RuleData(infotrygdForespResponse, receivedSykmelding.sykmelding)
            val results = listOf<List<Rule<RuleData>>>(
                    ValidationRules.values().toList()
            ).flatten().filter { rule -> rule.predicate(ruleData) }.onEach { RULE_HIT_COUNTER.labels(it.name).inc() }

            log.info("Outcomes: " + results.joinToString(", ", prefix = "\"", postfix = "\""))

            when {
                results.any { rule -> rule.status == Status.MANUAL_PROCESSING } ->
                    produceManualTask(kafkaProducer, receivedSykmelding.msgId, receivedSykmelding.sykmelding, results, personV3, organisasjonEnhetV2, logKeys, logValues)
                else -> sendInfotrygdOppdatering(infotrygdOppdateringProducer, session, createInfotrygdInfo(receivedSykmelding.fellesformat, InfotrygdForespAndHealthInformation(infotrygdForespResponse, receivedSykmelding.sykmelding)), logKeys, logValues)
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
    text = infotrygdSporringMarshaller.toString(infotrygdForesp)
    jmsReplyTo = temporaryQueue
})

fun sendInfotrygdOppdatering(
    producer: MessageProducer,
    session: Session,
    fellesformat: EIFellesformat,
    logKeys: String,
    logValues: Array<StructuredArgument>
) = producer.send(session.createTextMessage().apply {
    text = fellesformatMarshaller.toString(fellesformat)
    log.info("Message is automatic outcome $logKeys", *logValues)
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
    hovedDiagnosekodeverk = Diagnosekode.values().first {
        it.kithCode == healthInformation.medisinskVurdering.hovedDiagnose.diagnosekode.s
    }.infotrygdCode
    fodselsnrBehandler = healthInformation.behandler.id.first {
        it.typeId.v == "FNR"
    }.id // TODO maybe get the fnr from xmlmsg its mandatorie to be there
    if (healthInformation.medisinskVurdering.biDiagnoser?.diagnosekode?.firstOrNull()?.v != null &&
            healthInformation.medisinskVurdering.biDiagnoser?.diagnosekode?.firstOrNull()?.s != null) {
        biDiagnoseKode = healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.first().v
        biDiagnosekodeverk = Diagnosekode.values().first {
            it.kithCode == healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.first().s
        }.infotrygdCode
    }
    tkNrFraDato = newInstance.newXMLGregorianCalendar(gregorianCalendarMinus1Year)
}

val inputFactory = XMLInputFactory.newInstance()
inline fun <reified T> unmarshal(text: String): T = fellesformatUnmarshaller.unmarshal(inputFactory.createXMLEventReader(StringReader(text)), T::class.java).value

fun createInfotrygdInfo(marshalledFellesformat: String, itfh: InfotrygdForespAndHealthInformation) = unmarshal<EIFellesformat>(marshalledFellesformat).apply {
    any.add(KontrollSystemBlokk().apply {
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
    val syketilfelleStartDatoFraSykemelding = itfh.healthInformation.syketilfelleStartDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
    val healthInformationPeriodeTOMDato: LocalDate? = periode.periodeTOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()
    val healthInformationPeriodeFOMDato: LocalDate? = periode.periodeFOMDato?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDate()

    return if (!infotrygdforespSmHistFinnes && syketilfelleStartDatoFraSykemelding == healthInformationPeriodeFOMDato) {
        "1".toBigInteger()
    } else if (infotrygdforespSmHistFinnes && healthInformationPeriodeTOMDato == sMhistorikkArbuforFOM && healthInformationPeriodeTOMDato == sMhistorikkArbuforTOM) {
        "2".toBigInteger()
    } else if (infotrygdforespSmHistFinnes && sMhistorikkArbuforFOM != null && sMhistorikkArbuforTOM != null && sMhistorikkArbuforTOM == healthInformationPeriodeTOMDato || sMhistorikkArbuforTOM?.isBefore(healthInformationPeriodeTOMDato) != null) {
        "3".toBigInteger()
    } else {
        throw RuntimeException("Could not determined operasjonstype")
    }
}

fun produceManualTask(kafkaProducer: KafkaProducer<String, ProduceTask>, msgId: String, healthInformation: HelseOpplysningerArbeidsuforhet, results: List<Rule<RuleData>>, personV3: PersonV3, organisasjonEnhetV2: OrganisasjonEnhetV2, logKeys: String, logValues: Array<StructuredArgument>) {
    log.info("Message is manual outcome $logKeys", *logValues)
    val geografiskTilknytning = personV3.hentGeografiskTilknytning(HentGeografiskTilknytningRequest().withAktoer(PersonIdent().withIdent(
                NorskIdent()
                        .withIdent(healthInformation.pasient.fodselsnummer.id)
                        .withType(Personidenter().withValue(healthInformation.pasient.fodselsnummer.typeId.v))))).geografiskTilknytning

    val navKontor = organisasjonEnhetV2.finnNAVKontor(FinnNAVKontorRequest().apply {
            this.geografiskTilknytning = Geografi().apply {
                this.value = geografiskTilknytning?.geografiskTilknytning ?: "0"
            }
        }).navKontor

    kafkaProducer.send(ProducerRecord("aapen-syfo-oppgave-produserOppgave", ProduceTask().apply {
        setMessageId(msgId)
        setUserIdent(healthInformation.pasient.fodselsnummer.id)
        setUserTypeCode("PERSON")
        setTaskType("SYK")
        setFieldCode("SYK")
        setSubcategoryType("SYK_SYK")
        setPriorityCode("NORM_SYK")
        setDescription("Kunne ikkje oppdatere Infotrygd automatisk, på grunn av følgende: ${results.joinToString(", ", prefix = "\"", postfix = "\"")}")
        setStartsInDays(0)
        setEndsInDays(21)
        setResponsibleUnit(navKontor.enhetId)
        setFollowUpText("") // TODO
    }))
    log.info("Message sendt to topic: aapen-syfo-oppgave-produserOppgave $logKeys", *logValues)
}
