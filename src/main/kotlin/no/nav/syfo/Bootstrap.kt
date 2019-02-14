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
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.KontrollSystemBlokk
import no.nav.helse.sm2013.KontrollsystemBlokkType
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.rules.RuleData
import no.nav.syfo.rules.ValidationRuleChain
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
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.math.BigInteger
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
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
    val config: ApplicationConfig = objectMapper.readValue(File(System.getenv("CONFIG_FILE")))
    val credentials: VaultCredentials = objectMapper.readValue(vaultApplicationPropertiesPath.toFile())
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, config.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    connectionFactory(config).createConnection(credentials.mqUsername, credentials.mqPassword).use { connection ->
        connection.start()

        try {
            val listeners = (1..config.applicationThreads).map {
                launch {
                    val consumerProperties = readConsumerConfig(config, credentials, valueDeserializer = StringDeserializer::class)
                    val producerProperties = readProducerConfig(config, credentials, valueSerializer = KafkaAvroSerializer::class)
                    val kafkaconsumer = KafkaConsumer<String, String>(consumerProperties)
                    kafkaconsumer.subscribe(listOf(config.sm2013AutomaticHandlingTopic, config.smPaperAutomaticHandlingTopic))
                    val kafkaproducer = KafkaProducer<String, ProduceTask>(producerProperties)
                    val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
                    val infotrygdOppdateringQueue = session.createQueue("queue:///${config.infotrygdOppdateringQueue}?targetClient=1")
                    val infotrygdSporringQueue = session.createQueue("queue:///${config.infotrygdSporringQueue}?targetClient=1")
                    val infotrygdOppdateringProducer = session.createProducer(infotrygdOppdateringQueue)
                    val infotrygdSporringProducer = session.createProducer(infotrygdSporringQueue)

                    val personV3 = JaxWsProxyFactoryBean().apply {
                        address = config.personV3EndpointURL
                        serviceClass = PersonV3::class.java
                    }.create() as PersonV3
                    configureSTSFor(personV3, credentials.serviceuserUsername,
                            credentials.serviceuserPassword, config.securityTokenServiceUrl)

                    val orgnaisasjonEnhet = JaxWsProxyFactoryBean().apply {
                        address = config.organisasjonEnhetV2EndpointURL
                        serviceClass = OrganisasjonEnhetV2::class.java
                    }.create() as OrganisasjonEnhetV2
                    configureSTSFor(orgnaisasjonEnhet, credentials.serviceuserUsername,
                            credentials.serviceuserPassword, config.securityTokenServiceUrl)

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
        try {
            kafkaConsumer.poll(Duration.ofMillis(0)).forEach {
                val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(it.value())
                val logValues = arrayOf(
                        keyValue("smId", receivedSykmelding.navLogId),
                        keyValue("msgId", receivedSykmelding.msgId),
                        keyValue("orgNr", receivedSykmelding.legekontorOrgNr)
                )
                val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") { "{}" }
                log.info("Received a SM2013 $logKeys", *logValues)

                val infotrygdForespRequest = createInfotrygdForesp(receivedSykmelding.sykmelding, receivedSykmelding.personNrLege)
                val temporaryQueue = session.createTemporaryQueue()
                sendInfotrygdSporring(infotrygdSporringProducer, session, infotrygdForespRequest, temporaryQueue)
                val tmpConsumer = session.createConsumer(temporaryQueue)
                val consumedMessage = tmpConsumer.receive(15000)
                val inputMessageText = when (consumedMessage) {
                    is TextMessage -> consumedMessage.text
                    else -> throw RuntimeException("Incoming message needs to be a byte message or text message, JMS type:" + consumedMessage.jmsType)
                }
                val infotrygdForespResponse = infotrygdSporringUnmarshaller.unmarshal(StringReader(inputMessageText)) as InfotrygdForesp

                log.info("Going through rulesrules $logKeys", *logValues)
                val ruleData = RuleData(infotrygdForespResponse, receivedSykmelding.sykmelding)
                val results = listOf<List<Rule<RuleData>>>(
                        ValidationRuleChain.values().toList()
                ).flatten().filter { rule -> rule.predicate(ruleData) }

                log.info("Rules hit {}, $logKeys", results.map { it.name }, *logValues)

                when {
                    results.any { rule -> rule.status == Status.MANUAL_PROCESSING } ->
                        produceManualTask(kafkaProducer, receivedSykmelding.msgId, receivedSykmelding.sykmelding, results, personV3, organisasjonEnhetV2, logKeys, logValues)
                    else -> sendInfotrygdOppdatering(infotrygdOppdateringProducer, session, createInfotrygdInfo(receivedSykmelding.fellesformat, InfotrygdForespAndHealthInformation(infotrygdForespResponse, receivedSykmelding.sykmelding)), logKeys, logValues)
                }
            }
        } catch (e: Exception) {
            log.error("Exception caught while handling message", e)
        }
        }
        delay(100)
    }

data class InfotrygdForespAndHealthInformation(
    val infotrygdForesp: InfotrygdForesp,
    val healthInformation: HelseOpplysningerArbeidsuforhet
)

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
    fellesformat: XMLEIFellesformat,
    logKeys: String,
    logValues: Array<StructuredArgument>
) = producer.send(session.createTextMessage().apply {
    text = fellesformatMarshaller.toString(fellesformat)
    RULE_HIT_STATUS_COUNTER.labels(Status.OK.name)
    log.info("Message is automatic outcome $logKeys", *logValues)
})

fun createInfotrygdForesp(healthInformation: HelseOpplysningerArbeidsuforhet, doctorFnr: String) = InfotrygdForesp().apply {
    val dateMinus1Year = LocalDate.now().minusYears(1)
    val dateMinus4Years = LocalDate.now().minusYears(4)

    fodselsnummer = healthInformation.pasient.fodselsnummer.id
    tkNrFraDato = dateMinus1Year
    forespNr = 1.toBigInteger()
    forespTidsStempel = LocalDateTime.now()
    fraDato = dateMinus1Year
    eldsteFraDato = dateMinus4Years
    hovedDiagnosekode = healthInformation.medisinskVurdering.hovedDiagnose.diagnosekode.v
    hovedDiagnosekodeverk = Diagnosekode.values().first {
        it.kithCode == healthInformation.medisinskVurdering.hovedDiagnose.diagnosekode.s
    }.infotrygdCode
    fodselsnrBehandler = doctorFnr
    if (healthInformation.medisinskVurdering.biDiagnoser?.diagnosekode?.firstOrNull()?.v != null &&
            healthInformation.medisinskVurdering.biDiagnoser?.diagnosekode?.firstOrNull()?.s != null) {
        biDiagnoseKode = healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.first().v
        biDiagnosekodeverk = Diagnosekode.values().first {
            it.kithCode == healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.first().s
        }.infotrygdCode
    }
    tkNrFraDato = dateMinus1Year
}

val inputFactory = XMLInputFactory.newInstance()
inline fun <reified T> unmarshal(text: String): T = fellesformatUnmarshaller.unmarshal(inputFactory.createXMLEventReader(StringReader(text)), T::class.java).value

fun createInfotrygdInfo(marshalledFellesformat: String, itfh: InfotrygdForespAndHealthInformation) = unmarshal<XMLEIFellesformat>(marshalledFellesformat).apply {
    any.add(KontrollSystemBlokk().apply {
    itfh.healthInformation.aktivitet.periode.forEachIndexed { index, periode ->
    infotrygdBlokk.add(KontrollsystemBlokkType.InfotrygdBlokk().apply {
        fodselsnummer = itfh.healthInformation.pasient.fodselsnummer.id
        tkNummer = ""
        forsteFravaersDag = periode.periodeFOMDato
        mottakerKode = itfh.infotrygdForesp.behandlerInfo.behandler.first().mottakerKode.value()
        operasjonstype = when (index) {
            0 -> "1".toBigInteger() // TODO findOprasjonstype(periode, itfh)
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

fun findOprasjonstype(periode: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode, itfh: InfotrygdForespAndHealthInformation): BigInteger {
    // FORSTEGANGS = 1, PAFOLGENDE = 2, ENDRING = 3
    val typeSMinfo = itfh.infotrygdForesp.sMhistorikk.sykmelding.firstOrNull()

    // TODO fixed this to implementet corretly
    return if (itfh.infotrygdForesp.sMhistorikk.status.kodeMelding == "04" &&
            itfh.healthInformation.syketilfelleStartDato == periode.periodeFOMDato) {
        "1".toBigInteger()
    } else if (itfh.infotrygdForesp.sMhistorikk.status.kodeMelding != "04" && typeSMinfo?.periode?.arbufoerFOM != null &&
            typeSMinfo.periode?.arbufoerTOM != null &&
            typeSMinfo.periode.arbufoerFOM.isBefore(periode.periodeFOMDato) &&
            typeSMinfo.periode.arbufoerTOM.isBefore(periode.periodeTOMDato)) {
        "2".toBigInteger()
    } else if (itfh.infotrygdForesp.sMhistorikk.status.kodeMelding != "04" &&
            typeSMinfo?.periode?.arbufoerFOM != null && typeSMinfo.periode?.arbufoerTOM != null &&
            typeSMinfo.periode?.arbufoerTOM == periode.periodeTOMDato ||
            typeSMinfo?.periode?.arbufoerTOM?.isBefore(periode.periodeTOMDato) != null) {
        "3".toBigInteger()
    } else {
        throw RuntimeException("Could not determined operasjonstype")
    }
}

fun produceManualTask(kafkaProducer: KafkaProducer<String, ProduceTask>, msgId: String, healthInformation: HelseOpplysningerArbeidsuforhet, results: List<Rule<RuleData>>, personV3: PersonV3, organisasjonEnhetV2: OrganisasjonEnhetV2, logKeys: String, logValues: Array<StructuredArgument>) {
    log.info("Message is manual outcome $logKeys", *logValues)
    RULE_HIT_STATUS_COUNTER.labels(Status.MANUAL_PROCESSING.name)
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
