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
import no.nav.syfo.helpers.retry
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.metrics.RULE_HIT_STATUS_COUNTER
import no.nav.syfo.model.Periode
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.rules.Rule
import no.nav.syfo.rules.ValidationRuleChain
import no.nav.syfo.rules.executeFlow
import no.nav.syfo.rules.sortedSMInfos
import no.nav.syfo.sak.avro.PrioritetType
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.util.JacksonKafkaSerializer
import no.nav.syfo.util.fellesformatMarshaller
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.infotrygdSporringMarshaller
import no.nav.syfo.util.infotrygdSporringUnmarshaller
import no.nav.syfo.ws.createPort
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
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter
import java.lang.IllegalStateException
import java.math.BigInteger
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
    val env = Environment()
    val credentials = objectMapper.readValue<VaultCredentials>(Paths.get("/var/run/secrets/nais.io/vault/credentials.json").toFile())
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    DefaultExports.initialize()

    connectionFactory(env).createConnection(credentials.mqUsername, credentials.mqPassword).use { connection ->
        connection.start()

        try {
            val listeners = (1..env.applicationThreads).map {
                launch {
                    val kafkaBaseConfig = loadBaseConfig(env, credentials)
                    val consumerProperties = kafkaBaseConfig.toConsumerConfig("${env.applicationName}-consumer", valueDeserializer = StringDeserializer::class)
                    val producerPropertiesCreateTask = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = KafkaAvroSerializer::class)

                    val producerPropertiesvalidationResult = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)

                    val kafkaconsumer = KafkaConsumer<String, String>(consumerProperties)
                    kafkaconsumer.subscribe(listOf(env.sm2013AutomaticHandlingTopic, env.smPaperAutomaticHandlingTopic))
                    val kafkaproducerCreateTask = KafkaProducer<String, ProduceTask>(producerPropertiesCreateTask)

                    val kafkaproducervalidationResult = KafkaProducer<String, ValidationResult>(producerPropertiesvalidationResult)

                    val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
                    val infotrygdOppdateringProducer = session.producerForQueue("queue:///${env.infotrygdOppdateringQueue}?targetClient=1")
                    val infotrygdSporringProducer = session.producerForQueue("queue:///${env.infotrygdSporringQueue}?targetClient=1")

                    val personV3 = createPort<PersonV3>(env.personV3EndpointURL) {
                        port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceUrl) }
                    }

                    val arbeidsfordelingV1 = createPort<ArbeidsfordelingV1>(env.arbeidsfordelingV1EndpointURL) {
                        port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceUrl) }
                    }

                    blockingApplicationLogic(applicationState, kafkaconsumer, kafkaproducerCreateTask, kafkaproducervalidationResult, infotrygdOppdateringProducer, infotrygdSporringProducer, session, personV3, arbeidsfordelingV1, env)
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
    kafkaproducerCreateTask: KafkaProducer<String, ProduceTask>,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    infotrygdOppdateringProducer: MessageProducer,
    infotrygdSporringProducer: MessageProducer,
    session: Session,
    personV3: PersonV3,
    arbeidsfordelingV1: ArbeidsfordelingV1,
    env: Environment
) {
    while (applicationState.running) {
            kafkaConsumer.poll(Duration.ofMillis(0)).forEach {
                val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(it.value())
                val logValues = arrayOf(
                        keyValue("mottakId", receivedSykmelding.navLogId),
                        keyValue("msgId", receivedSykmelding.msgId),
                        keyValue("orgNr", receivedSykmelding.legekontorOrgNr),
                        keyValue("sykmeldingId", receivedSykmelding.sykmelding.id)
                )
                val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") { "{}" }
                log.info("Received a SM2013 $logKeys", *logValues)

                val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(receivedSykmelding.fellesformat)) as XMLEIFellesformat
                val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)

                val infotrygdForespResponse = fetchInfotrygdForesp(receivedSykmelding, healthInformation, session, infotrygdSporringProducer)

                log.info("Going through rules $logKeys", *logValues)

                val validationRuleResults = ValidationRuleChain.values().executeFlow(receivedSykmelding.sykmelding, infotrygdForespResponse)

                val results = listOf(validationRuleResults).flatten()
                log.info("Rules hit {}, $logKeys", results.map { rule -> rule.name }, *logValues)

                val validationResult = validationResult(results)
                RULE_HIT_STATUS_COUNTER.labels(validationResult.status.name).inc()

                kafkaproducervalidationResult.send(ProducerRecord(env.sm2013BehandlingsUtfallToipic, receivedSykmelding.sykmelding.id, validationResult))
                log.info("Validation results send to kafka {} $logKeys", env.sm2013BehandlingsUtfallToipic, *logValues)

                when {
                    results.any { rule -> rule.status == Status.MANUAL_PROCESSING } ->
                        produceManualTask(kafkaproducerCreateTask, receivedSykmelding, validationResult, personV3, arbeidsfordelingV1, logKeys, logValues)
                    else -> sendInfotrygdOppdatering(infotrygdOppdateringProducer, session, createInfotrygdInfo(receivedSykmelding.fellesformat, InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation), receivedSykmelding.personNrPasient), logKeys, logValues)
                }
            }
        delay(100)
        }
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
    log.info("Message is automatic outcome $logKeys", *logValues)
    text = fellesformatMarshaller.toString(fellesformat)
    log.info("Message is sendt to infotrygd $logKeys", *logValues)
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

fun createInfotrygdInfo(marshalledFellesformat: String, itfh: InfotrygdForespAndHealthInformation, personNrPasient: String) = unmarshal<XMLEIFellesformat>(marshalledFellesformat).apply {
    any.add(KontrollSystemBlokk().apply {
    itfh.healthInformation.aktivitet.periode.forEachIndexed { index, periode ->
    infotrygdBlokk.add(KontrollsystemBlokkType.InfotrygdBlokk().apply {
        fodselsnummer = personNrPasient
        tkNummer = ""
        forsteFravaersDag = periode.periodeFOMDato
        mottakerKode = itfh.infotrygdForesp.behandlerInfo.behandler.firstOrNull()?.mottakerKode?.value() ?: ""
        operasjonstype = when (index) {
            0 -> findOprasjonstype(periode, itfh)
            else -> "2".toBigInteger()
        }
        if (operasjonstype.equals("1".toBigInteger()) &&
                index == 0 &&
                itfh.healthInformation.kontaktMedPasient?.kontaktDato != null &&
                itfh.healthInformation.kontaktMedPasient?.behandletDato != null) {
            behandlingsDato = listOf(itfh.healthInformation.kontaktMedPasient.kontaktDato,
                    itfh.healthInformation.kontaktMedPasient.behandletDato.toLocalDate()).sorted().first()
        }
        hovedDiagnose = itfh.infotrygdForesp.hovedDiagnosekode
        hovedDiagnoseGruppe = itfh.infotrygdForesp.hovedDiagnosekodeverk.toBigInteger()
        hovedDiagnoseTekst = itfh.healthInformation.medisinskVurdering.hovedDiagnose.diagnosekode.dn
        if (itfh.infotrygdForesp.biDiagnosekodeverk != null &&
                itfh.healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.firstOrNull()?.dn != null) {
            biDiagnose = itfh.infotrygdForesp.biDiagnoseKode
            biDiagnoseGruppe = itfh.infotrygdForesp.biDiagnosekodeverk.toBigInteger()
            biDiagnoseTekst = itfh.healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.firstOrNull()?.dn
        }

        if (index == 0) {
            arbeidsKategori = findarbeidsKategori(itfh)
            gruppe = "96"
            saksbehandler = "Auto"
        }

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

fun findarbeidsKategori(itfh: InfotrygdForespAndHealthInformation): String {
    return if (!itfh.healthInformation.arbeidsgiver?.navnArbeidsgiver.isNullOrBlank()) {
        "01"
    } else {
        "030"
    }
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

suspend fun CoroutineScope.produceManualTask(kafkaProducer: KafkaProducer<String, ProduceTask>, receivedSykmelding: ReceivedSykmelding, validationResult: ValidationResult, personV3: PersonV3, arbeidsfordelingV1: ArbeidsfordelingV1, logKeys: String, logValues: Array<StructuredArgument>) {
    log.info("Message is manual outcome $logKeys", *logValues)

    val geografiskTilknytning = fetchGeografiskTilknytningAsync(personV3, receivedSykmelding)
    val finnBehandlendeEnhetListeResponse = fetchBehandlendeEnhetAsync(arbeidsfordelingV1, geografiskTilknytning.geografiskTilknytning)

    when (geografiskTilknytning.diskresjonskode?.kodeverksRef) {
        "SPSF" -> createTask(kafkaProducer, receivedSykmelding, validationResult, "2106", logKeys, logValues)
        else -> createTask(kafkaProducer, receivedSykmelding, validationResult, findNavOffice(finnBehandlendeEnhetListeResponse), logKeys, logValues)
    }
}

fun createTask(kafkaProducer: KafkaProducer<String, ProduceTask>, receivedSykmelding: ReceivedSykmelding, validationResult: ValidationResult, navKontor: String, logKeys: String, logValues: Array<StructuredArgument>) {
    kafkaProducer.send(ProducerRecord("aapen-syfo-oppgave-produserOppgave", receivedSykmelding.sykmelding.id,
            ProduceTask().apply {
                messageId = receivedSykmelding.msgId
                aktoerId = receivedSykmelding.sykmelding.pasientAktoerId
                tildeltEnhetsnr = navKontor
                opprettetAvEnhetsnr = "9999"
                behandlesAvApplikasjon = "FS22" // Gosys
                orgnr = receivedSykmelding.legekontorOrgNr ?: ""
                beskrivelse = "Manuell behandling av sykmelding grunnet følgende regler: ${validationResult.ruleHits.joinToString(", ", "(", ")") { it.messageForSender }}"
                temagruppe = "ANY"
                tema = "SYM"
                behandlingstema = "ANY"
                oppgavetype = "BEH_EL_SYM"
                behandlingstype = "ANY"
                mappeId = 1
                aktivDato = DateTimeFormatter.ISO_DATE.format(LocalDate.now())
                fristFerdigstillelse = DateTimeFormatter.ISO_DATE.format(LocalDate.now())
                prioritet = PrioritetType.NORM
                metadata = mapOf()
            }))
    log.info("Message sendt to topic: aapen-syfo-oppgave-produserOppgave $logKeys", *logValues)
}

suspend fun fetchGeografiskTilknytningAsync(
    personV3: PersonV3,
    receivedSykmelding: ReceivedSykmelding
): HentGeografiskTilknytningResponse =
        retry("tps_hent_geografisktilknytning", arrayOf(500L, 1000L, 3000L, 5000L, 10000L), IOException::class, WstxException::class) {
            personV3.hentGeografiskTilknytning(HentGeografiskTilknytningRequest().withAktoer(PersonIdent().withIdent(
                    NorskIdent()
                            .withIdent(receivedSykmelding.personNrPasient)
                            .withType(Personidenter().withValue("FNR")))))
        }

suspend fun fetchBehandlendeEnhetAsync(
    arbeidsfordelingV1: ArbeidsfordelingV1,
    geografiskTilknytning: GeografiskTilknytning?
): FinnBehandlendeEnhetListeResponse? =
        retry("finn_nav_kontor", arrayOf(500L, 1000L, 3000L, 5000L, 10000L), IOException::class, WstxException::class) {
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
        "0393" // NAV Oppfølging utland
    } else {
        finnBehandlendeEnhetListeResponse.behandlendeEnhetListe.first().enhetId
    }

inline fun <reified T> XMLEIFellesformat.get() = this.any.find { it is T } as T

fun extractHelseOpplysningerArbeidsuforhet(fellesformat: XMLEIFellesformat): HelseOpplysningerArbeidsuforhet =
        fellesformat.get<XMLMsgHead>().document[0].refDoc.content.any[0] as HelseOpplysningerArbeidsuforhet

fun ClosedRange<LocalDate>.daysBetween(): Long = ChronoUnit.DAYS.between(start, endInclusive)

suspend fun fetchInfotrygdForesp(
    receivedSykmelding: ReceivedSykmelding,
    healthInformation: HelseOpplysningerArbeidsuforhet,
    session: Session,
    infotrygdSporringProducer: MessageProducer
): InfotrygdForesp =
        retry("it_hent_infotrygdForesp", arrayOf(500L, 1000L, 3000L, 5000L, 10000L, 3600000L, 14400000L), IOException::class, WstxException::class, IllegalStateException::class) {
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

fun validationResult(results: List<Rule<Any>>): ValidationResult =
        ValidationResult(
                status = results
                        .map { status -> status.status }.let {
                            it.firstOrNull { status -> status == Status.MANUAL_PROCESSING }
                                    ?: Status.OK
                        },
                ruleHits = results.map { rule -> RuleInfo(rule.name, rule.messageForUser!!, rule.messageForSender!!) }
        )