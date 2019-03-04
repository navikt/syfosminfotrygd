package no.nav.syfo

import com.ctc.wstx.exc.WstxException
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
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArgument
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.kith.xmlstds.msghead._2006_05_24.XMLMsgHead
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.KontrollSystemBlokk
import no.nav.helse.sm2013.KontrollsystemBlokkType
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.rules.ValidationRuleChain
import no.nav.syfo.rules.sortedSMInfos
import no.nav.syfo.sak.avro.PrioritetType
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.ws.configureSTSFor
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.binding.ArbeidsfordelingV1
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.ArbeidsfordelingKriterier
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Tema
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.meldinger.FinnBehandlendeEnhetListeRequest
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.meldinger.FinnBehandlendeEnhetListeResponse
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.GeografiskTilknytning
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Personidenter
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningRequest
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningResponse
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter
import java.math.BigInteger
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
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

    DefaultExports.initialize()

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

                    val arbeidsfordelingV1 = JaxWsProxyFactoryBean().apply {
                        address = config.arbeidsfordelingV1EndpointURL
                        serviceClass = ArbeidsfordelingV1::class.java
                    }.create() as ArbeidsfordelingV1
                    configureSTSFor(arbeidsfordelingV1, credentials.serviceuserUsername,
                            credentials.serviceuserPassword, config.securityTokenServiceUrl)

                    blockingApplicationLogic(applicationState, kafkaconsumer, kafkaproducer, infotrygdOppdateringProducer, infotrygdSporringProducer, session, personV3, arbeidsfordelingV1)
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

suspend fun CoroutineScope.blockingApplicationLogic(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, String>,
    kafkaProducer: KafkaProducer<String, ProduceTask>,
    infotrygdOppdateringProducer: MessageProducer,
    infotrygdSporringProducer: MessageProducer,
    session: Session,
    personV3: PersonV3,
    arbeidsfordelingV1: ArbeidsfordelingV1
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
                log.info("Received a SM2013 $logKeys", *logValues)

                val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(receivedSykmelding.fellesformat)) as XMLEIFellesformat
                val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)

                val infotrygdForespResponse = fetchInfotrygdForesp(receivedSykmelding, healthInformation, session, infotrygdSporringProducer)

                log.info("Going through rules $logKeys", *logValues)

                val validationRuleResults = ValidationRuleChain.values().executeFlow(healthInformation, infotrygdForespResponse.await())

                val results = listOf(validationRuleResults).flatten()
                log.info("Rules hit {}, $logKeys", results.map { rule -> rule.name }, *logValues)

                when {
                    results.any { rule -> rule.status == Status.MANUAL_PROCESSING } ->
                        produceManualTask(kafkaProducer, receivedSykmelding, results, personV3, arbeidsfordelingV1, logKeys, logValues)
                    else -> sendInfotrygdOppdatering(infotrygdOppdateringProducer, session, createInfotrygdInfo(receivedSykmelding.fellesformat, InfotrygdForespAndHealthInformation(infotrygdForespResponse.await(), healthInformation)), logKeys, logValues)
                }
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
    log.info("Message is automatic outcome $logKeys", *logValues)
    RULE_HIT_STATUS_COUNTER.labels(Status.OK.name).inc()
})

fun createInfotrygdForesp(personNrPasient: String, healthInformation: HelseOpplysningerArbeidsuforhet, doctorFnr: String) = InfotrygdForesp().apply {
    val dateMinus1Year = LocalDate.now().minusYears(1)
    val dateMinus4Years = LocalDate.now().minusYears(4)

    fodselsnummer = personNrPasient
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
        mottakerKode = itfh.infotrygdForesp.behandlerInfo.behandler.firstOrNull()?.mottakerKode?.value() ?: ""
        operasjonstype = when (index) {
            0 -> findOprasjonstype(periode, itfh)
            else -> "2".toBigInteger()
        }
        hovedDiagnose = itfh.infotrygdForesp.hovedDiagnosekode
        hovedDiagnoseGruppe = itfh.infotrygdForesp.hovedDiagnosekodeverk.toBigInteger()
        hovedDiagnoseTekst = itfh.healthInformation.medisinskVurdering.hovedDiagnose.diagnosekode.dn
        arbeidsufoerTOM = periode.periodeTOMDato
        ufoeregrad = when {
            periode.gradertSykmelding != null -> periode.gradertSykmelding.sykmeldingsgrad.toBigInteger()
            periode.aktivitetIkkeMulig != null -> 100.toBigInteger()
            else -> 0.toBigInteger()
            }
        })
        }
    })
}

fun findOprasjonstype(periode: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode, itfh: InfotrygdForespAndHealthInformation): BigInteger {
    // FORSTEGANGS = 1, PAFOLGENDE = 2, ENDRING = 3
    val typeSMinfo = itfh.infotrygdForesp.sMhistorikk?.sykmelding?.sortedSMInfos()?.lastOrNull()
        ?: return 1.toBigInteger()

    return if (itfh.infotrygdForesp.sMhistorikk.status.kodeMelding == "04" ||
            (typeSMinfo.periode.arbufoerFOM..periode.periodeFOMDato).daysBetween() > 280) {
        "1".toBigInteger()
    } else if ((typeSMinfo.periode.arbufoerFOM..periode.periodeFOMDato).daysBetween() < 280 &&
                    periode.periodeFOMDato.isAfter(typeSMinfo.periode.arbufoerFOM) ||
            (typeSMinfo.periode.arbufoerTOM != null &&
            (periode.periodeFOMDato.isAfter(typeSMinfo.periode.arbufoerTOM) ||
                    periode.periodeFOMDato.isAfter(typeSMinfo.periode.arbufoerFOM)))) {
        "2".toBigInteger()
    } else if (typeSMinfo.periode.arbufoerTOM != null &&
            (typeSMinfo.periode.arbufoerFOM == periode.periodeFOMDato ||
                            typeSMinfo.periode.arbufoerTOM.isBefore(periode.periodeTOMDato))) {
        "3".toBigInteger()
    } else {
        throw RuntimeException("Could not determined operasjonstype")
    }
}

suspend fun CoroutineScope.produceManualTask(kafkaProducer: KafkaProducer<String, ProduceTask>, receivedSykmelding: ReceivedSykmelding, results: List<Rule<Any>>, personV3: PersonV3, arbeidsfordelingV1: ArbeidsfordelingV1, logKeys: String, logValues: Array<StructuredArgument>) {
    log.info("Message is manual outcome $logKeys", *logValues)
    RULE_HIT_STATUS_COUNTER.labels(Status.MANUAL_PROCESSING.name).inc()

    val geografiskTilknytning = fetchGeografiskTilknytning(personV3, receivedSykmelding)
    val finnBehandlendeEnhetListeResponse = fetchBehandlendeEnhet(arbeidsfordelingV1, geografiskTilknytning.await().geografiskTilknytning)

    when (geografiskTilknytning.await().diskresjonskode?.kodeverksRef) {
        "SPSF" -> createTask(kafkaProducer, receivedSykmelding, results, "2106", logKeys, logValues)
        else -> createTask(kafkaProducer, receivedSykmelding, results, findNavOffice(finnBehandlendeEnhetListeResponse.await()), logKeys, logValues)
    }
}

fun createTask(kafkaProducer: KafkaProducer<String, ProduceTask>, receivedSykmelding: ReceivedSykmelding, results: List<Rule<Any>>, navKontor: String, logKeys: String, logValues: Array<StructuredArgument>) {
    kafkaProducer.send(ProducerRecord("aapen-syfo-oppgave-produserOppgave", ProduceTask(
            receivedSykmelding.msgId, receivedSykmelding.sykmelding.pasientAktoerId,
            navKontor, "", "GOSYS", "",
            receivedSykmelding.legekontorOrgNr ?: "", "Manuell behandling Sykmelding: ${results.joinToString(", ", prefix = "\"", postfix = "\"")}", "",
            "SYM", "", "BEH_EL_SYM", "",
            1, LocalDate.now().toString(), LocalDate.now().plusDays(10).toString(),
            PrioritetType.NORM, mapOf<String, String>()

    )))
    log.info("Message sendt to topic: aapen-syfo-oppgave-produserOppgave $logKeys", *logValues)
}

fun CoroutineScope.fetchGeografiskTilknytning(personV3: PersonV3, receivedSykmelding: ReceivedSykmelding): Deferred<HentGeografiskTilknytningResponse> =
        retryAsync("tps_hent_geografisktilknytning", IOException::class, WstxException::class) {
            personV3.hentGeografiskTilknytning(HentGeografiskTilknytningRequest().withAktoer(PersonIdent().withIdent(
                    NorskIdent()
                            .withIdent(receivedSykmelding.personNrPasient)
                            .withType(Personidenter().withValue("FNR")))))
        }

fun CoroutineScope.fetchBehandlendeEnhet(arbeidsfordelingV1: ArbeidsfordelingV1, geografiskTilknytning: GeografiskTilknytning?): Deferred<FinnBehandlendeEnhetListeResponse?> =
        retryAsync("finn_nav_kontor", IOException::class, WstxException::class) {
            arbeidsfordelingV1.finnBehandlendeEnhetListe(FinnBehandlendeEnhetListeRequest().apply {
                val afk = ArbeidsfordelingKriterier()
                if (geografiskTilknytning?.geografiskTilknytning != null) {
                    afk.geografiskTilknytning = no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Geografi().apply {
                        value = geografiskTilknytning.geografiskTilknytning
                    }
                }
                afk.tema = Tema().apply {
                    value = "SYM"
                }
                arbeidsfordelingKriterier = afk
            })
        }

fun findNavOffice(finnBehandlendeEnhetListeResponse: FinnBehandlendeEnhetListeResponse?): String =
    if (finnBehandlendeEnhetListeResponse?.behandlendeEnhetListe?.firstOrNull()?.enhetId == null) {
        "0393"
    } else {
        finnBehandlendeEnhetListeResponse.behandlendeEnhetListe.first().enhetId
    }

inline fun <reified T> XMLEIFellesformat.get() = this.any.find { it is T } as T

fun extractHelseOpplysningerArbeidsuforhet(fellesformat: XMLEIFellesformat): HelseOpplysningerArbeidsuforhet =
        fellesformat.get<XMLMsgHead>().document[0].refDoc.content.any[0] as HelseOpplysningerArbeidsuforhet

fun ClosedRange<LocalDate>.daysBetween(): Long = ChronoUnit.DAYS.between(start, endInclusive)

fun CoroutineScope.fetchInfotrygdForesp(receivedSykmelding: ReceivedSykmelding, healthInformation: HelseOpplysningerArbeidsuforhet, session: Session, infotrygdSporringProducer: MessageProducer): Deferred<InfotrygdForesp> =
        retryAsync("it_hent_infotrygdForesp", IOException::class, WstxException::class) {
            val infotrygdForespRequest = createInfotrygdForesp(receivedSykmelding.personNrPasient, healthInformation, receivedSykmelding.personNrLege)
            val temporaryQueue = session.createTemporaryQueue()
            try {
                sendInfotrygdSporring(infotrygdSporringProducer, session, infotrygdForespRequest, temporaryQueue)
                session.createConsumer(temporaryQueue).use { tmpConsumer ->
                    val consumedMessage = tmpConsumer.receive(20000)
                    val inputMessageText = when (consumedMessage) {
                        is TextMessage -> consumedMessage.text
                        else -> throw RuntimeException("Incoming message needs to be a byte message or text message, JMS type:" + consumedMessage.jmsType)
                    }

                    infotrygdSporringUnmarshaller.unmarshal(StringReader(inputMessageText)) as InfotrygdForesp
                }
            } finally {
                temporaryQueue.delete()
            }
        }