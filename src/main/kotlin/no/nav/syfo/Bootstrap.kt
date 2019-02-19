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
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.KontrollSystemBlokk
import no.nav.helse.sm2013.KontrollsystemBlokkType
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.rules.ValidationRuleChain
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.ws.configureSTSFor
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.binding.ArbeidsfordelingV1
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.ArbeidsfordelingKriterier
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Tema
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.meldinger.FinnBehandlendeEnhetListeRequest
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.meldinger.FinnBehandlendeEnhetListeResponse
import no.nav.tjeneste.virksomhet.organisasjonenhet.v2.binding.OrganisasjonEnhetV2
import no.nav.tjeneste.virksomhet.organisasjonenhet.v2.informasjon.Geografi
import no.nav.tjeneste.virksomhet.organisasjonenhet.v2.informasjon.Organisasjonsenhet
import no.nav.tjeneste.virksomhet.organisasjonenhet.v2.meldinger.FinnNAVKontorRequest
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.GeografiskTilknytning
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Personidenter
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningRequest
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import org.apache.cxf.ext.logging.LoggingFeature
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
                        features.add(LoggingFeature())
                        serviceClass = PersonV3::class.java
                    }.create() as PersonV3
                    configureSTSFor(personV3, credentials.serviceuserUsername,
                            credentials.serviceuserPassword, config.securityTokenServiceUrl)

                    val arbeidsfordelingV1 = JaxWsProxyFactoryBean().apply {
                        address = config.arbeidsfordelingV1EndpointURL
                        features.add(LoggingFeature())
                        serviceClass = ArbeidsfordelingV1::class.java
                    }.create() as ArbeidsfordelingV1
                    configureSTSFor(arbeidsfordelingV1, credentials.serviceuserUsername,
                            credentials.serviceuserPassword, config.securityTokenServiceUrl)

                    val orgnaisasjonEnhet = JaxWsProxyFactoryBean().apply {
                        address = config.organisasjonEnhetV2EndpointURL
                        features.add(LoggingFeature())
                        serviceClass = OrganisasjonEnhetV2::class.java
                    }.create() as OrganisasjonEnhetV2
                    configureSTSFor(orgnaisasjonEnhet, credentials.serviceuserUsername,
                            credentials.serviceuserPassword, config.securityTokenServiceUrl)

                    blockingApplicationLogic(applicationState, kafkaconsumer, kafkaproducer, infotrygdOppdateringProducer, infotrygdSporringProducer, session, personV3, orgnaisasjonEnhet, arbeidsfordelingV1)
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
    organisasjonEnhetV2: OrganisasjonEnhetV2,
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

                // TODO add retry to tempQueue
                val infotrygdForespRequest = createInfotrygdForesp(receivedSykmelding.personNrPasient, receivedSykmelding.sykmelding, receivedSykmelding.personNrLege)
                val temporaryQueue = session.createTemporaryQueue()
                sendInfotrygdSporring(infotrygdSporringProducer, session, infotrygdForespRequest, temporaryQueue)
                val tmpConsumer = session.createConsumer(temporaryQueue)
                val consumedMessage = tmpConsumer.receive(20000)
                val inputMessageText = when (consumedMessage) {
                    is TextMessage -> consumedMessage.text
                    else -> throw RuntimeException("Incoming message needs to be a byte message or text message, JMS type:" + consumedMessage.jmsType)
                }

                val infotrygdForespResponse = infotrygdSporringUnmarshaller.unmarshal(StringReader(inputMessageText)) as InfotrygdForesp

                log.info("Going through rules $logKeys", *logValues)

                val validationRuleResults = ValidationRuleChain.values().executeFlow(receivedSykmelding.sykmelding, infotrygdForespResponse)

                val results = listOf(validationRuleResults).flatten()
                log.info("Rules hit {}, $logKeys", results.map { it.name }, *logValues)

                when {
                    results.any { rule -> rule.status == Status.MANUAL_PROCESSING } ->
                        produceManualTask(kafkaProducer, receivedSykmelding, results, personV3, organisasjonEnhetV2, arbeidsfordelingV1, logKeys, logValues)
                    else -> sendInfotrygdOppdatering(infotrygdOppdateringProducer, session, createInfotrygdInfo(receivedSykmelding.fellesformat, InfotrygdForespAndHealthInformation(infotrygdForespResponse, receivedSykmelding.sykmelding)), logKeys, logValues)
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
    RULE_HIT_STATUS_COUNTER.labels(Status.OK.name).inc()
    log.info("Message is automatic outcome $logKeys", *logValues)
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
    val typeSMinfo = itfh.infotrygdForesp.sMhistorikk.sykmelding.firstOrNull()

    // TODO fixed this to implementet corretly
    return if (itfh.infotrygdForesp.sMhistorikk.status.kodeMelding == "04") {
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

suspend fun CoroutineScope.produceManualTask(kafkaProducer: KafkaProducer<String, ProduceTask>, receivedSykmelding: ReceivedSykmelding, results: List<Rule<Any>>, personV3: PersonV3, organisasjonEnhetV2: OrganisasjonEnhetV2, arbeidsfordelingV1: ArbeidsfordelingV1, logKeys: String, logValues: Array<StructuredArgument>) {
    log.info("Message is manual outcome $logKeys", *logValues)
    RULE_HIT_STATUS_COUNTER.labels(Status.MANUAL_PROCESSING.name).inc()
    // TODO what if geografiskTilknytning is null??
    val geografiskTilknytning = fetchGeografiskTilknytning(personV3, receivedSykmelding)

    val finnBehandlendeEnhetListeResponse = fetchBehandlendeEnhet(arbeidsfordelingV1, geografiskTilknytning.await())
    if (finnBehandlendeEnhetListeResponse.await()?.behandlendeEnhetListe?.firstOrNull()?.enhetId == null) {
        log.error("finnBehandlendeEnhetListeResponse is null, where to send the task to??? $logKeys", *logValues)
    }
    // TODO remove and use finnBehandlendeEnhetListeResponse
    val navKontor = fetchNAVKontor(organisasjonEnhetV2, geografiskTilknytning.await()).await()

    if (navKontor?.enhetId != null) {
        createTask(kafkaProducer, receivedSykmelding, results, navKontor, logKeys, logValues)
    } else {
        log.error("Nav kontor is null, where to send the task to??? $logKeys", *logValues) // TODO
    }
}

fun createTask(kafkaProducer: KafkaProducer<String, ProduceTask>, receivedSykmelding: ReceivedSykmelding, results: List<Rule<Any>>, navKontor: Organisasjonsenhet, logKeys: String, logValues: Array<StructuredArgument>) {
    kafkaProducer.send(ProducerRecord("aapen-syfo-oppgave-produserOppgave", ProduceTask().apply {
        setMessageId(receivedSykmelding.msgId)
        setUserIdent(receivedSykmelding.personNrPasient)
        setUserTypeCode("PERSON")
        setTaskType("SYM")
        setFieldCode("SYM")
        setSubcategoryType("SYK_SYK")
        setPriorityCode("NORM_SYK")
        setDescription("Kunne ikkje oppdatere Infotrygd automatisk, på grunn av følgende: ${results.joinToString(", ", prefix = "\"", postfix = "\"")}")
        setStartsInDays(0)
        setEndsInDays(21)
        setResponsibleUnit(navKontor.enhetId)
        setFollowUpText("Se beskrivelse")
    }))
    log.info("Message sendt to topic: aapen-syfo-oppgave-produserOppgave $logKeys", *logValues)
}

fun CoroutineScope.fetchGeografiskTilknytning(personV3: PersonV3, receivedSykmelding: ReceivedSykmelding): Deferred<GeografiskTilknytning> =
        retryAsync("tps_hent_geografisktilknytning", IOException::class, WstxException::class) {
            personV3.hentGeografiskTilknytning(HentGeografiskTilknytningRequest().withAktoer(PersonIdent().withIdent(
                    NorskIdent()
                            .withIdent(receivedSykmelding.sykmelding.pasient.fodselsnummer.id)
                            .withType(Personidenter().withValue(receivedSykmelding.sykmelding.pasient.fodselsnummer.typeId.v))))).geografiskTilknytning
        }

fun CoroutineScope.fetchNAVKontor(organisasjonEnhetV2: OrganisasjonEnhetV2, geografiskTilknytning: GeografiskTilknytning?): Deferred<Organisasjonsenhet?> =
        retryAsync("finn_nav_kontor", IOException::class, WstxException::class) {
            organisasjonEnhetV2.finnNAVKontor(FinnNAVKontorRequest().apply {
                this.geografiskTilknytning = Geografi().apply {
                    this.value = geografiskTilknytning?.geografiskTilknytning ?: "0"
                }
            }).navKontor
        }

fun CoroutineScope.fetchBehandlendeEnhet(arbeidsfordelingV1: ArbeidsfordelingV1, geografiskTilknytning: GeografiskTilknytning?): Deferred<FinnBehandlendeEnhetListeResponse?> =
        retryAsync("finn_nav_kontor", IOException::class, WstxException::class) {
            arbeidsfordelingV1.finnBehandlendeEnhetListe(FinnBehandlendeEnhetListeRequest().apply {
                val afk = ArbeidsfordelingKriterier()
                if (geografiskTilknytning?.geografiskTilknytning != null) {
                    afk.geografiskTilknytning = no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Geografi().apply {
                        kodeverksRef = geografiskTilknytning.geografiskTilknytning
                    }
                }
                afk.tema = Tema().apply {
                    kodeverksRef = "SYM"
                }
                arbeidsfordelingKriterier = afk
            })
        }