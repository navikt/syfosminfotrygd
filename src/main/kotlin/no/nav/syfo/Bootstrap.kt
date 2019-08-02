package no.nav.syfo

import com.ctc.wstx.exc.WstxException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.ibm.mq.MQC
import com.ibm.mq.MQEnvironment
import com.ibm.mq.MQQueue
import com.ibm.mq.MQQueueManager
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.ktor.application.Application
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter
import java.lang.IllegalStateException
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.GregorianCalendar
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.jms.MessageProducer
import javax.jms.Session
import javax.jms.TemporaryQueue
import javax.jms.TextMessage
import javax.xml.bind.Marshaller
import javax.xml.datatype.DatatypeFactory
import javax.xml.stream.XMLInputFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArgument
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.KontrollSystemBlokk
import no.nav.helse.sm2013.KontrollsystemBlokkType
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.helpers.retry
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.metrics.MESSAGES_ON_INFOTRYGD_SMIKKEOK_QUEUE_COUNTER
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.metrics.RULE_HIT_STATUS_COUNTER
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.RuleMetadata
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.rules.Rule
import no.nav.syfo.rules.TssRuleChain
import no.nav.syfo.rules.ValidationRuleChain
import no.nav.syfo.rules.executeFlow
import no.nav.syfo.rules.sortedSMInfos
import no.nav.syfo.sak.avro.PrioritetType
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.util.JacksonKafkaSerializer
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.infotrygdSporringMarshaller
import no.nav.syfo.util.infotrygdSporringUnmarshaller
import no.nav.syfo.util.xmlObjectWriter
import no.nav.syfo.ws.createPort
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.binding.ArbeidsfordelingV1
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.ArbeidsfordelingKriterier
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Diskresjonskoder
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Geografi
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Oppgavetyper
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
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonRequest
import no.nhn.schemas.reg.hprv2.IHPR2Service
import no.nhn.schemas.reg.hprv2.IHPR2ServiceHentPersonMedPersonnummerGenericFaultFaultFaultMessage
import no.nhn.schemas.reg.hprv2.Person as HPRPerson
import org.apache.cxf.binding.soap.SoapMessage
import org.apache.cxf.binding.soap.interceptor.AbstractSoapInterceptor
import org.apache.cxf.message.Message
import org.apache.cxf.phase.Phase
import org.apache.cxf.ws.addressing.WSAddressingFeature
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.sminfotrygd")
val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

val coroutineContext = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

val datatypeFactory: DatatypeFactory = DatatypeFactory.newInstance()

const val NAV_OPPFOLGING_UTLAND_KONTOR_NR = "0393"

fun main() = runBlocking(coroutineContext) {
    val env = Environment()
    val credentials = objectMapper.readValue<VaultCredentials>(Paths.get("/var/run/secrets/nais.io/vault/credentials.json").toFile())
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    DefaultExports.initialize()

    connectionFactory(env).createConnection(credentials.mqUsername, credentials.mqPassword).use { connection ->
        connection.start()

        val kafkaBaseConfig = loadBaseConfig(env, credentials)
        val consumerProperties = kafkaBaseConfig.toConsumerConfig("${env.applicationName}-consumer", valueDeserializer = StringDeserializer::class)
        val producerPropertiesCreateTask = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = KafkaAvroSerializer::class)

        val producerPropertiesvalidationResult = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)

        val kafkaproducerCreateTask = KafkaProducer<String, ProduceTask>(producerPropertiesCreateTask)

        val kafkaproducervalidationResult = KafkaProducer<String, ValidationResult>(producerPropertiesvalidationResult)

        val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
        val infotrygdOppdateringProducer = session.producerForQueue("queue:///${env.infotrygdOppdateringQueue}?targetClient=1")
        val infotrygdSporringProducer = session.producerForQueue("queue:///${env.infotrygdSporringQueue}?targetClient=1")

        MQEnvironment.channel = env.mqChannelName
        MQEnvironment.port = env.mqPort
        MQEnvironment.hostname = env.mqHostname
        MQEnvironment.userID = credentials.mqUsername
        MQEnvironment.password = credentials.mqPassword
        val mqQueueManager = MQQueueManager(env.mqGatewayName)
        val openOptions = MQC.MQOO_INQUIRE + MQC.MQOO_BROWSE + MQC.MQOO_FAIL_IF_QUIESCING + MQC.MQOO_INPUT_SHARED
        val smIkkeOkQueue = mqQueueManager.accessQueue(env.infotrygdSmIkkeOKQueue, openOptions)

        val personV3 = createPort<PersonV3>(env.personV3EndpointURL) {
            port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceUrl) }
        }

        val arbeidsfordelingV1 = createPort<ArbeidsfordelingV1>(env.arbeidsfordelingV1EndpointURL) {
            port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceUrl) }
        }

        val helsepersonellV1 = createPort<IHPR2Service>(env.helsepersonellv1EndpointURL) {
            proxy {
                // TODO: Contact someone about this hacky workaround
                // talk to HDIR about HPR about they claim to send a ISO-8859-1 but its really UTF-8 payload
                val interceptor = object : AbstractSoapInterceptor(Phase.RECEIVE) {
                    override fun handleMessage(message: SoapMessage?) {
                        if (message != null)
                            message[Message.ENCODING] = "utf-8"
                    }
                }

                inInterceptors.add(interceptor)
                inFaultInterceptors.add(interceptor)
                features.add(WSAddressingFeature())
            }

            port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceUrl) }
        }

        launchListeners(applicationState, kafkaproducerCreateTask, kafkaproducervalidationResult, infotrygdOppdateringProducer, infotrygdSporringProducer, session, personV3, arbeidsfordelingV1, env, helsepersonellV1, consumerProperties, smIkkeOkQueue)

        Runtime.getRuntime().addShutdownHook(Thread {
            smIkkeOkQueue.close()
            mqQueueManager.disconnect()
            applicationServer.stop(10, 10, TimeUnit.SECONDS)
        })
    }
}

fun CoroutineScope.createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
        launch {
            try {
                action()
            } catch (e: TrackableException) {
                log.error("En uhaandtert feil oppstod, applikasjonen restartes. ${e.loggingMeta}",
                        *e.loggingMeta.logValues,
                        e.cause)
            } finally {
                applicationState.running = false
            }
        }

@KtorExperimentalAPI
suspend fun CoroutineScope.launchListeners(
    applicationState: ApplicationState,
    kafkaproducerCreateTask: KafkaProducer<String, ProduceTask>,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    infotrygdOppdateringProducer: MessageProducer,
    infotrygdSporringProducer: MessageProducer,
    session: Session,
    personV3: PersonV3,
    arbeidsfordelingV1: ArbeidsfordelingV1,
    env: Environment,
    helsepersonellv1: IHPR2Service,
    consumerProperties: Properties,
    smIkkeOkQueue: MQQueue
) {

    val recievedSykmeldingListeners = 0.until(env.applicationThreads).map {
        val kafkaconsumerRecievedSykmelding = KafkaConsumer<String, String>(consumerProperties)

        kafkaconsumerRecievedSykmelding.subscribe(
                listOf(env.sm2013AutomaticHandlingTopic, env.smPaperAutomaticHandlingTopic)
        )
        createListener(applicationState) {
            blockingApplicationLogic(applicationState, kafkaconsumerRecievedSykmelding, kafkaproducerCreateTask,
                    kafkaproducervalidationResult, infotrygdOppdateringProducer, infotrygdSporringProducer,
                    session, personV3, arbeidsfordelingV1, env, helsepersonellv1, smIkkeOkQueue)
        }
    }.toList()

    applicationState.initialized = true
    recievedSykmeldingListeners.forEach { it.join() }
}

@KtorExperimentalAPI
suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, String>,
    kafkaproducerCreateTask: KafkaProducer<String, ProduceTask>,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    infotrygdOppdateringProducer: MessageProducer,
    infotrygdSporringProducer: MessageProducer,
    session: Session,
    personV3: PersonV3,
    arbeidsfordelingV1: ArbeidsfordelingV1,
    env: Environment,
    helsepersonellv1: IHPR2Service,
    smIkkeOkQueue: MQQueue
) {
    while (applicationState.running) {
        kafkaConsumer.poll(Duration.ofMillis(0)).forEach { consumerRecord ->
            val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(consumerRecord.value())
            onUpdateInfotrygd(receivedSykmelding, kafkaproducerCreateTask, kafkaproducervalidationResult,
                    infotrygdOppdateringProducer, infotrygdSporringProducer,
                    session, personV3, arbeidsfordelingV1, env, helsepersonellv1, smIkkeOkQueue)
        }
        delay(100)
    }
}

@KtorExperimentalAPI
suspend fun onUpdateInfotrygd(
    receivedSykmelding: ReceivedSykmelding,
    kafkaproducerCreateTask: KafkaProducer<String, ProduceTask>,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    infotrygdOppdateringProducer: MessageProducer,
    infotrygdSporringProducer: MessageProducer,
    session: Session,
    personV3: PersonV3,
    arbeidsfordelingV1: ArbeidsfordelingV1,
    env: Environment,
    helsepersonellv1: IHPR2Service,
    smIkkeOkQueue: MQQueue
) = coroutineScope {
    val logValues = arrayOf(
            keyValue("msgId", receivedSykmelding.msgId),
            keyValue("mottakId", receivedSykmelding.navLogId),
            keyValue("sykmeldingId", receivedSykmelding.sykmelding.id),
            keyValue("orgNr", receivedSykmelding.legekontorOrgNr)
    )

    val loggingMeta = LoggingMeta(logValues)
    wrapExceptions(loggingMeta) {

        log.info("Received a SM2013 $loggingMeta", loggingMeta.logValues)

        val smIkkeOkCurrentDepth = smIkkeOkQueue.currentDepth.toDouble()
        MESSAGES_ON_INFOTRYGD_SMIKKEOK_QUEUE_COUNTER.inc(smIkkeOkCurrentDepth)

        val requestLatency = REQUEST_TIME.startTimer()

        val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(receivedSykmelding.fellesformat)) as XMLEIFellesformat
        val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)

        val infotrygdForespResponse = fetchInfotrygdForesp(receivedSykmelding, healthInformation, session, infotrygdSporringProducer)

        log.info("Going through rules $loggingMeta", loggingMeta.logValues)

        val validationRuleResults = ValidationRuleChain.values().executeFlow(
                receivedSykmelding.sykmelding,
                infotrygdForespResponse)

        val tssRuleResults = TssRuleChain.values().executeFlow(
                receivedSykmelding.sykmelding,
                RuleMetadata(
                        receivedDate = receivedSykmelding.mottattDato,
                        signatureDate = receivedSykmelding.sykmelding.signaturDato,
                        patientPersonNumber = receivedSykmelding.personNrPasient,
                        rulesetVersion = receivedSykmelding.rulesetVersion,
                        legekontorOrgnr = receivedSykmelding.legekontorOrgNr,
                        tssid = receivedSykmelding.tssid
                ))

        val results = listOf(validationRuleResults, tssRuleResults).flatten()
        log.info("Rules hit {}, $loggingMeta", results.map { rule -> rule.name }, loggingMeta.logValues)

        val validationResult = validationResult(results)
        RULE_HIT_STATUS_COUNTER.labels(validationResult.status.name).inc()

        kafkaproducervalidationResult.send(ProducerRecord(env.sm2013BehandlingsUtfallToipic, receivedSykmelding.sykmelding.id, validationResult))
        log.info("Validation results send to kafka {} $loggingMeta", env.sm2013BehandlingsUtfallToipic, *loggingMeta.logValues)

        val navKontorNr = findNavkontorNr(receivedSykmelding, personV3, arbeidsfordelingV1, loggingMeta)

        try {
            val doctor = fetchDoctor(helsepersonellv1, receivedSykmelding.personNrLege)

            val helsepersonellKategoriVerdi = finnAktivHelsepersonellAutorisasjons(doctor)

            when {
                results.any { rule -> rule.status == Status.MANUAL_PROCESSING } ->
                    produceManualTask(kafkaproducerCreateTask, receivedSykmelding, validationResult, navKontorNr, loggingMeta)
                else -> sendInfotrygdOppdatering(
                        infotrygdOppdateringProducer,
                        session,
                        loggingMeta,
                        receivedSykmelding.fellesformat,
                        InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation),
                        receivedSykmelding.personNrPasient,
                        receivedSykmelding.sykmelding.signaturDato.toLocalDate(),
                        helsepersonellKategoriVerdi,
                        receivedSykmelding.tssid,
                        navKontorNr)
            }
            val currentRequestLatency = requestLatency.observeDuration()

            log.info("Message($loggingMeta) got outcome {}, {}, processing took {}s",
                    loggingMeta.logValues,
                    keyValue("status", validationResult.status),
                    keyValue("ruleHits", validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName }),
                    keyValue("latency", currentRequestLatency))
        } catch (e: IHPR2ServiceHentPersonMedPersonnummerGenericFaultFaultFaultMessage) {
            val validationResultBehandler = ValidationResult(
                    status = Status.MANUAL_PROCESSING,
                    ruleHits = listOf(RuleInfo(
                            ruleName = "BEHANDLER_NOT_IN_HPR",
                            messageForSender = "Den som har skrevet sykmeldingen din har ikke autorisasjon til dette.",
                            messageForUser = "Behandler er ikke register i HPR"))
            )
            RULE_HIT_STATUS_COUNTER.labels(validationResultBehandler.status.name).inc()
            log.warn("Behandler er ikke register i HPR")
            produceManualTask(kafkaproducerCreateTask, receivedSykmelding, validationResultBehandler, navKontorNr, loggingMeta)
        }
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
    loggingMeta: LoggingMeta,
    marshalledFellesformat: String,
    itfh: InfotrygdForespAndHealthInformation,
    personNrPasient: String,
    signaturDato: LocalDate,
    behandlerKode: String,
    tssid: String?,
    navKontorNr: String
) {
    val perioder = itfh.healthInformation.aktivitet.periode.sortedBy { it.periodeFOMDato }
    sendInfotrygdOppdateringMq(producer, session, createInfotrygdBlokk(marshalledFellesformat, itfh, perioder.first(), personNrPasient, signaturDato, behandlerKode, tssid, loggingMeta, navKontorNr), loggingMeta)
    perioder.drop(1).forEach { periode ->
        sendInfotrygdOppdateringMq(producer, session, createInfotrygdBlokk(marshalledFellesformat, itfh, periode, personNrPasient, signaturDato, behandlerKode, tssid, loggingMeta, navKontorNr, 2), loggingMeta)
    }
}

fun sendInfotrygdOppdateringMq(
    producer: MessageProducer,
    session: Session,
    fellesformat: XMLEIFellesformat,
    loggingMeta: LoggingMeta
) = producer.send(session.createTextMessage().apply {
    log.info("Message has oprasjonstype: {}  $loggingMeta", fellesformat.get<KontrollsystemBlokkType>().infotrygdBlokk.first().operasjonstype, *loggingMeta.logValues)
    text = xmlObjectWriter.writeValueAsString(fellesformat)
    log.info("Message is sendt to infotrygd $loggingMeta", *loggingMeta.logValues)
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

val inputFactory = XMLInputFactory.newInstance()!!
inline fun <reified T> unmarshal(text: String): T = fellesformatUnmarshaller.unmarshal(inputFactory.createXMLEventReader(StringReader(text)), T::class.java).value

fun findarbeidsKategori(itfh: InfotrygdForespAndHealthInformation): String {
    return if (!itfh.healthInformation.arbeidsgiver?.navnArbeidsgiver.isNullOrBlank()) {
        "01"
    } else {
        "030"
    }
}

fun findOperasjonstype(
    periode: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode,
    itfh: InfotrygdForespAndHealthInformation,
    loggingMeta: LoggingMeta
): Int {
    // FORSTEGANGS = 1, PAFOLGENDE = 2, ENDRING = 3
    val typeSMinfo = itfh.infotrygdForesp.sMhistorikk?.sykmelding
            ?.sortedSMInfos()
            ?.lastOrNull()
            ?: return 1

    return if (itfh.infotrygdForesp.sMhistorikk.status.kodeMelding == "04" ||
            (typeSMinfo.periode.arbufoerTOM != null && (typeSMinfo.periode.arbufoerTOM..periode.periodeFOMDato).daysBetween() > 1)) {
        1
    } else if (typeSMinfo.periode.arbufoerTOM != null && periode.periodeFOMDato.isAfter(typeSMinfo.periode.arbufoerTOM) ||
            (typeSMinfo.periode.arbufoerTOM != null && periode.periodeFOMDato.isEqual(typeSMinfo.periode.arbufoerTOM)) ||
            (typeSMinfo.periode.arbufoerTOM == null && (typeSMinfo.periode.arbufoerFOM..periode.periodeFOMDato).daysBetween() > 1)) {
        2
    } else if (typeSMinfo.periode.arbufoerFOM == periode.periodeFOMDato || (typeSMinfo.periode.arbufoerFOM != null && typeSMinfo.periode.arbufoerFOM.isBefore(periode.periodeFOMDato))) {
        3
    } else {
        log.error("Could not determined operasjonstype $loggingMeta", *loggingMeta.logValues)
        throw RuntimeException("Could not determined operasjonstype")
    }
}

fun produceManualTask(
    kafkaProducer: KafkaProducer<String,
    ProduceTask>,
    receivedSykmelding: ReceivedSykmelding,
    validationResult: ValidationResult,
    navKontorNr: String,
    loggingMeta: LoggingMeta
) {
    createTask(kafkaProducer, receivedSykmelding, validationResult, navKontorNr, loggingMeta)
}

suspend fun findNavkontorNr(
    receivedSykmelding: ReceivedSykmelding,
    personV3: PersonV3,
    arbeidsfordelingV1: ArbeidsfordelingV1,
    loggingMeta: LoggingMeta
): String {
    val geografiskTilknytning = fetchGeografiskTilknytningAsync(personV3, receivedSykmelding)
    val patientDiskresjonsKode = fetchDiskresjonsKode(personV3, receivedSykmelding)
    val finnBehandlendeEnhetListeResponse = fetchBehandlendeEnhet(arbeidsfordelingV1, geografiskTilknytning.geografiskTilknytning, patientDiskresjonsKode)
    if (finnBehandlendeEnhetListeResponse?.behandlendeEnhetListe?.firstOrNull()?.enhetId == null) {
        log.error("arbeidsfordeling fant ingen nav-enheter $loggingMeta", loggingMeta.logValues)
    }
    return finnBehandlendeEnhetListeResponse?.behandlendeEnhetListe?.firstOrNull()?.enhetId ?: NAV_OPPFOLGING_UTLAND_KONTOR_NR
}

fun createTask(
    kafkaProducer: KafkaProducer<String,
    ProduceTask>,
    receivedSykmelding: ReceivedSykmelding,
    validationResult: ValidationResult,
    navKontorNr: String,
    loggingMeta: LoggingMeta
) {
    kafkaProducer.send(ProducerRecord("aapen-syfo-oppgave-produserOppgave", receivedSykmelding.sykmelding.id,
            ProduceTask().apply {
                messageId = receivedSykmelding.msgId
                aktoerId = receivedSykmelding.sykmelding.pasientAktoerId
                tildeltEnhetsnr = navKontorNr
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
    log.info("Message sendt to topic: aapen-syfo-oppgave-produserOppgave $loggingMeta", *loggingMeta.logValues)
}

suspend fun fetchGeografiskTilknytningAsync(
    personV3: PersonV3,
    receivedSykmelding: ReceivedSykmelding
): HentGeografiskTilknytningResponse =
        retry(callName = "tps_hent_geografisktilknytning",
                retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
                legalExceptions = *arrayOf(IOException::class, WstxException::class, IllegalStateException::class)) {
            personV3.hentGeografiskTilknytning(HentGeografiskTilknytningRequest().withAktoer(PersonIdent().withIdent(
                    NorskIdent()
                            .withIdent(receivedSykmelding.personNrPasient)
                            .withType(Personidenter().withValue("FNR")))))
        }

suspend fun fetchBehandlendeEnhet(arbeidsfordelingV1: ArbeidsfordelingV1, geografiskTilknytning: GeografiskTilknytning?, patientDiskresjonsKode: String?): FinnBehandlendeEnhetListeResponse? =
        retry(callName = "finn_nav_kontor",
                retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
                legalExceptions = *arrayOf(IOException::class, WstxException::class)) {
            arbeidsfordelingV1.finnBehandlendeEnhetListe(FinnBehandlendeEnhetListeRequest().apply {
                val afk = ArbeidsfordelingKriterier()
                if (geografiskTilknytning?.geografiskTilknytning != null) {
                    afk.geografiskTilknytning = Geografi().apply {
                        value = geografiskTilknytning.geografiskTilknytning
                    }
                }
                afk.tema = Tema().apply {
                    value = "SYM"
                }

                afk.oppgavetype = Oppgavetyper().apply {
                    value = "BEH_EL_SYM"
                }

                if (!patientDiskresjonsKode.isNullOrBlank()) {
                    afk.diskresjonskode = Diskresjonskoder().apply {
                        value = patientDiskresjonsKode
                    }
                }

                arbeidsfordelingKriterier = afk
            })
        }

suspend fun fetchDiskresjonsKode(personV3: PersonV3, receivedSykmelding: ReceivedSykmelding): String? =
        retry(callName = "tps_hent_person",
                retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
                legalExceptions = *arrayOf(IOException::class, WstxException::class)) {
            personV3.hentPerson(HentPersonRequest()
                    .withAktoer(PersonIdent().withIdent(NorskIdent().withIdent(receivedSykmelding.personNrPasient)))
            ).person?.diskresjonskode?.value
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
        retry(callName = "it_hent_infotrygdForesp",
                retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L, 3600000L, 14400000L),
                legalExceptions = *arrayOf(IOException::class, WstxException::class, IllegalStateException::class)
        ) {
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

fun List<HelseOpplysningerArbeidsuforhet.Aktivitet.Periode>.sortedFOMDate(): List<LocalDate> =
        map { it.periodeFOMDato }.sorted()

fun createInfotrygdBlokk(
    marshalledFellesformat: String,
    itfh: InfotrygdForespAndHealthInformation,
    periode: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode,
    personNrPasient: String,
    signaturDato: LocalDate,
    helsepersonellKategoriVerdi: String,
    tssid: String?,
    loggingMeta: LoggingMeta,
    navKontorNr: String,
    operasjonstypeKode: Int = findOperasjonstype(periode, itfh, loggingMeta)
) = unmarshal<XMLEIFellesformat>(marshalledFellesformat).apply {
    any.add(KontrollSystemBlokk().apply {
        infotrygdBlokk.add(KontrollsystemBlokkType.InfotrygdBlokk().apply {
            fodselsnummer = personNrPasient
            tkNummer = navKontorNr

            operasjonstype = operasjonstypeKode.toBigInteger()

            val typeSMinfo = itfh.infotrygdForesp.sMhistorikk?.sykmelding
                    ?.sortedSMInfos()
                    ?.lastOrNull()

            if ((typeSMinfo != null && tssid?.toBigInteger() != typeSMinfo.periode.legeInstNr) || operasjonstype == 1.toBigInteger()) {
                legeEllerInstitusjonsNummer = tssid?.toBigInteger() ?: "".toBigInteger()
                legeEllerInstitusjon = if (itfh.healthInformation.behandler != null) {
                    itfh.healthInformation.behandler.formatName()
                } else {
                    ""
                }
            }

            forsteFravaersDag = when (operasjonstype) {
                1.toBigInteger() -> itfh.healthInformation.aktivitet.periode.sortedFOMDate().first()
                else -> typeSMinfo?.periode?.arbufoerOppr ?: throw RuntimeException("Unable to find første fraværsdag in IT")
            }

            mottakerKode = helsepersonellKategoriVerdi

            if (itfh.infotrygdForesp.diagnosekodeOK != null) {
                hovedDiagnose = itfh.infotrygdForesp.hovedDiagnosekode
                hovedDiagnoseGruppe = itfh.infotrygdForesp.hovedDiagnosekodeverk.toBigInteger()
                hovedDiagnoseTekst = itfh.infotrygdForesp.diagnosekodeOK.diagnoseTekst
            }

            if (operasjonstype == 1.toBigInteger()) {
                behandlingsDato = findbBehandlingsDato(itfh, signaturDato)

                arbeidsKategori = findarbeidsKategori(itfh)
                gruppe = "96"
                saksbehandler = "Auto"

                if (itfh.infotrygdForesp.biDiagnosekodeverk != null &&
                        itfh.healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.firstOrNull()?.dn != null &&
                        itfh.infotrygdForesp.diagnosekodeOK != null) {
                    biDiagnose = itfh.infotrygdForesp.biDiagnoseKode
                    biDiagnoseGruppe = itfh.infotrygdForesp.biDiagnosekodeverk.toBigInteger()
                    biDiagnoseTekst = itfh.infotrygdForesp.diagnosekodeOK.bidiagnoseTekst
                }
            }

            if (itfh.healthInformation.medisinskVurdering?.isSvangerskap != null &&
                    itfh.healthInformation.medisinskVurdering.isSvangerskap) {
                isErSvangerskapsrelatert = true
            }

            arbeidsufoerTOM = periode.periodeTOMDato
            ufoeregrad = when {
                periode.gradertSykmelding != null -> periode.gradertSykmelding.sykmeldingsgrad.toBigInteger()
                periode.aktivitetIkkeMulig != null -> 100.toBigInteger()
                else -> 0.toBigInteger()
            }
        })
    })
}

fun findbBehandlingsDato(itfh: InfotrygdForespAndHealthInformation, signaturDato: LocalDate): LocalDate {
    return if (itfh.healthInformation.kontaktMedPasient?.kontaktDato != null &&
            itfh.healthInformation.kontaktMedPasient?.behandletDato != null) {
        listOf(itfh.healthInformation.kontaktMedPasient.kontaktDato,
                itfh.healthInformation.kontaktMedPasient.behandletDato.toLocalDate()).sorted().first()
    } else if (itfh.healthInformation.kontaktMedPasient?.behandletDato != null) {
        itfh.healthInformation.kontaktMedPasient.behandletDato.toLocalDate()
    } else {
        signaturDato
    }
}

suspend fun fetchDoctor(hprService: IHPR2Service, doctorIdent: String): HPRPerson = retry(
        callName = "hpr_hent_person_med_personnummer",
        retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
        legalExceptions = *arrayOf(IOException::class, WstxException::class)
) {
    hprService.hentPersonMedPersonnummer(doctorIdent, datatypeFactory.newXMLGregorianCalendar(GregorianCalendar()))
}

fun finnAktivHelsepersonellAutorisasjons(helsepersonelPerson: HPRPerson): String =
        helsepersonelPerson.godkjenninger.godkjenning.firstOrNull {
            it?.helsepersonellkategori?.isAktiv != null &&
                    it.autorisasjon?.isAktiv == true &&
                    it.helsepersonellkategori.isAktiv != null &&
                    it.helsepersonellkategori.verdi != null
        }?.helsepersonellkategori?.verdi ?: ""

fun HelseOpplysningerArbeidsuforhet.Behandler.formatName(): String =
        if (navn.mellomnavn == null) {
            "${navn.etternavn.toUpperCase()} ${navn.fornavn.toUpperCase()}"
        } else {
            "${navn.etternavn.toUpperCase()} ${navn.fornavn.toUpperCase()} ${navn.mellomnavn.toUpperCase()}"
        }

data class LoggingMeta(
    val logValues: Array<StructuredArgument>
) {
    private val logFormat: String = logValues.joinToString(prefix = "(", postfix = ")", separator = ", ") { "{}" }
    override fun toString() = logFormat
}

class TrackableException(override val cause: Throwable, val loggingMeta: LoggingMeta) : RuntimeException()

suspend fun <T : Any, O> T.wrapExceptions(loggingMeta: LoggingMeta, block: suspend T.() -> O): O {
        try {
            return block()
        } catch (e: Exception) {
            throw TrackableException(e, loggingMeta)
        }
}
