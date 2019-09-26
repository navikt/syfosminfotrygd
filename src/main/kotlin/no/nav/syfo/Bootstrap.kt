package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
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
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Properties
import java.util.concurrent.TimeUnit
import javax.jms.MessageProducer
import javax.jms.Session
import javax.xml.bind.Marshaller
import javax.xml.stream.XMLInputFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments.fields
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.api.AccessTokenClient
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.client.Norg2Client
import no.nav.syfo.client.NorskHelsenettClient
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
import no.nav.syfo.sak.avro.PrioritetType
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.services.FindNAVKontorService
import no.nav.syfo.services.UpdateInfotrygdService
import no.nav.syfo.services.fetchInfotrygdForesp
import no.nav.syfo.util.JacksonKafkaSerializer
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.ws.createPort
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.binding.ArbeidsfordelingV1
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.sminfotrygd")
val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

const val NAV_OPPFOLGING_UTLAND_KONTOR_NR = "0393"

@KtorExperimentalAPI
fun main() {
    val env = Environment()
    val credentials = objectMapper.readValue<VaultCredentials>(Paths.get("/var/run/secrets/nais.io/vault/credentials.json").toFile())
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    DefaultExports.initialize()

            val kafkaBaseConfig = loadBaseConfig(env, credentials)
            val consumerProperties = kafkaBaseConfig.toConsumerConfig("${env.applicationName}-consumer", valueDeserializer = StringDeserializer::class)
            val producerPropertiesCreateTask = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = KafkaAvroSerializer::class)

            val producerPropertiesvalidationResult = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)

            val producerPropertiesReceivedSykmelding = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)

            val kafkaproducerreceivedSykmelding = KafkaProducer<String, ReceivedSykmelding>(producerPropertiesReceivedSykmelding)

            val kafkaproducerCreateTask = KafkaProducer<String, ProduceTask>(producerPropertiesCreateTask)

            val kafkaproducervalidationResult = KafkaProducer<String, ValidationResult>(producerPropertiesvalidationResult)

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

            val accessTokenClient = AccessTokenClient(env.aadAccessTokenUrl, env.clientId, credentials.clientsecret)
            val norskHelsenetthttpClient = HttpClient(CIO) {
            install(JsonFeature) {
                serializer = JacksonSerializer {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    }
                expectSuccess = false
                }
            }
            val norskHelsenettClient = NorskHelsenettClient(norskHelsenetthttpClient, env.norskHelsenettEndpointURL, accessTokenClient, env.helsenettproxyId)

            val norg2ClientHttpClient = HttpClient(Apache) {
                install(JsonFeature) {
                    serializer = JacksonSerializer {
                        registerKotlinModule()
                        registerModule(JavaTimeModule())
                        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    }
                }
                expectSuccess = false
            }

            val norg2Client = Norg2Client(norg2ClientHttpClient, env.norg2V1EndpointURL)

            Runtime.getRuntime().addShutdownHook(Thread {
                smIkkeOkQueue.close()
                mqQueueManager.disconnect()
                applicationServer.stop(10, 10, TimeUnit.SECONDS)
            })

            launchListeners(
                    applicationState,
                    kafkaproducerCreateTask,
                    kafkaproducervalidationResult,
                    personV3,
                    arbeidsfordelingV1,
                    env,
                    norskHelsenettClient,
                    consumerProperties,
                    smIkkeOkQueue,
                    norg2Client,
                    kafkaproducerreceivedSykmelding,
                    credentials)
}

fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
        GlobalScope.launch {
            try {
                action()
            } catch (e: TrackableException) {
                log.error("En uhåndtert feil oppstod, applikasjonen restarter {}", fields(e.loggingMeta), e.cause)
            } finally {
                applicationState.running = false
            }
        }

@KtorExperimentalAPI
fun launchListeners(
    applicationState: ApplicationState,
    kafkaproducerCreateTask: KafkaProducer<String, ProduceTask>,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    personV3: PersonV3,
    arbeidsfordelingV1: ArbeidsfordelingV1,
    env: Environment,
    norskHelsenettClient: NorskHelsenettClient,
    consumerProperties: Properties,
    smIkkeOkQueue: MQQueue,
    norg2Client: Norg2Client,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    credentials: VaultCredentials
) {
        val kafkaconsumerRecievedSykmelding = KafkaConsumer<String, String>(consumerProperties)

        kafkaconsumerRecievedSykmelding.subscribe(
                listOf(env.sm2013AutomaticHandlingTopic, env.smPaperAutomaticHandlingTopic, env.sm2013infotrygdRetry)
        )
        createListener(applicationState) {
            connectionFactory(env).createConnection(credentials.mqUsername, credentials.mqPassword).use { connection ->
                Jedis(env.redishost, 6379).use { jedis ->
                connection.start()
                val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
                val infotrygdOppdateringProducer = session.producerForQueue("queue:///${env.infotrygdOppdateringQueue}?targetClient=1")
                val infotrygdSporringProducer = session.producerForQueue("queue:///${env.infotrygdSporringQueue}?targetClient=1")

                blockingApplicationLogic(applicationState, kafkaconsumerRecievedSykmelding, kafkaproducerCreateTask,
                        kafkaproducervalidationResult, infotrygdOppdateringProducer, infotrygdSporringProducer,
                        session, personV3, arbeidsfordelingV1, env.sm2013BehandlingsUtfallToipic, norskHelsenettClient,
                        smIkkeOkQueue, norg2Client, jedis, kafkaproducerreceivedSykmelding, env.sm2013infotrygdRetry,
                        env.sm2013OpppgaveTopic)
                }
            }
        }

    applicationState.initialized = true
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
    sm2013BehandlingsUtfallToipic: String,
    norskHelsenettClient: NorskHelsenettClient,
    smIkkeOkQueue: MQQueue,
    norg2Client: Norg2Client,
    jedis: Jedis,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    infotrygdRetryTopic: String,
    oppgaveTopic: String
) {
    while (applicationState.running) {
        kafkaConsumer.poll(Duration.ofMillis(0)).forEach { consumerRecord ->
            val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(consumerRecord.value())
            val loggingMeta = LoggingMeta(
                    mottakId = receivedSykmelding.navLogId,
                    orgNr = receivedSykmelding.legekontorOrgNr,
                    msgId = receivedSykmelding.msgId,
                    sykmeldingId = receivedSykmelding.sykmelding.id
            )

            handleMessage(
                    receivedSykmelding, kafkaproducerCreateTask, kafkaproducervalidationResult,
                    infotrygdOppdateringProducer, infotrygdSporringProducer,
                    session, personV3, arbeidsfordelingV1, sm2013BehandlingsUtfallToipic, norskHelsenettClient,
                    smIkkeOkQueue, loggingMeta, norg2Client, jedis, kafkaproducerreceivedSykmelding,
                    infotrygdRetryTopic, oppgaveTopic)
        }
        delay(100)
    }
}

@KtorExperimentalAPI
suspend fun handleMessage(
    receivedSykmelding: ReceivedSykmelding,
    kafkaproducerCreateTask: KafkaProducer<String, ProduceTask>,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    infotrygdOppdateringProducer: MessageProducer,
    infotrygdSporringProducer: MessageProducer,
    session: Session,
    personV3: PersonV3,
    arbeidsfordelingV1: ArbeidsfordelingV1,
    sm2013BehandlingsUtfallToipic: String,
    norskHelsenettClient: NorskHelsenettClient,
    smIkkeOkQueue: MQQueue,
    loggingMeta: LoggingMeta,
    norg2Client: Norg2Client,
    jedis: Jedis,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    infotrygdRetryTopic: String,
    oppgaveTopic: String
) {
    wrapExceptions(loggingMeta) {
        log.info("Received a SM2013, {}", fields(loggingMeta))

        val smIkkeOkCurrentDepth = smIkkeOkQueue.currentDepth.toDouble()
        MESSAGES_ON_INFOTRYGD_SMIKKEOK_QUEUE_COUNTER.set(smIkkeOkCurrentDepth)

        val requestLatency = REQUEST_TIME.startTimer()

        val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(receivedSykmelding.fellesformat)) as XMLEIFellesformat
        val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)

        val infotrygdForespResponse = fetchInfotrygdForesp(
                receivedSykmelding,
                infotrygdSporringProducer,
                session,
                healthInformation)

        val validationResult = ruleCheck(receivedSykmelding, infotrygdForespResponse, loggingMeta)

        val findNAVKontorService = FindNAVKontorService(receivedSykmelding, personV3, norg2Client, arbeidsfordelingV1, loggingMeta)

        val behandlendeEnhet = findNAVKontorService.finnBehandlendeEnhet()
        val lokaltNavkontor = findNAVKontorService.finnLokaltNavkontor()

        UpdateInfotrygdService().updateInfotrygd(receivedSykmelding,
                norskHelsenettClient,
                validationResult,
                infotrygdOppdateringProducer,
                kafkaproducerCreateTask,
                behandlendeEnhet,
                lokaltNavkontor,
                loggingMeta,
                session,
                infotrygdForespResponse,
                healthInformation,
                jedis,
                kafkaproducerreceivedSykmelding,
                infotrygdRetryTopic,
                oppgaveTopic,
                kafkaproducervalidationResult,
                sm2013BehandlingsUtfallToipic
                )

        val currentRequestLatency = requestLatency.observeDuration()

        log.info("Message processing took {}s, for message {}",
                keyValue("latency", currentRequestLatency),
                fields(loggingMeta))
    }
}

fun ruleCheck(
    receivedSykmelding: ReceivedSykmelding,
    infotrygdForespResponse: InfotrygdForesp,
    loggingMeta: LoggingMeta
): ValidationResult {

    log.info("Going through rules {}", fields(loggingMeta))

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
    log.info("Rules hit {}, $loggingMeta", results.map { rule -> rule.name }, fields(loggingMeta))

    val validationResult = validationResult(results)
    RULE_HIT_STATUS_COUNTER.labels(validationResult.status.name).inc()
    return validationResult
}

fun sendRuleCheckValidationResult(
    receivedSykmelding: ReceivedSykmelding,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    validationResult: ValidationResult,
    sm2013BehandlingsUtfallToipic: String,
    loggingMeta: LoggingMeta
) {
    kafkaproducervalidationResult.send(ProducerRecord(sm2013BehandlingsUtfallToipic, receivedSykmelding.sykmelding.id, validationResult))
    log.info("Validation results send to kafka {} $loggingMeta", sm2013BehandlingsUtfallToipic, fields(loggingMeta))
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

val inputFactory = XMLInputFactory.newInstance()!!
inline fun <reified T> unmarshal(text: String): T = fellesformatUnmarshaller.unmarshal(inputFactory.createXMLEventReader(StringReader(text)), T::class.java).value

fun produceManualTaskAndSendValidationResults(
    kafkaProducer: KafkaProducer<String, ProduceTask>,
    receivedSykmelding: ReceivedSykmelding,
    validationResult: ValidationResult,
    navKontorNr: String,
    loggingMeta: LoggingMeta,
    oppgaveTopic: String,
    sm2013BehandlingsUtfallToipic: String,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>
) {
    sendRuleCheckValidationResult(receivedSykmelding, kafkaproducervalidationResult,
            validationResult, sm2013BehandlingsUtfallToipic, loggingMeta)
    createTask(kafkaProducer, receivedSykmelding, validationResult, navKontorNr, loggingMeta, oppgaveTopic)
}

fun createTask(
    kafkaProducer: KafkaProducer<String,
    ProduceTask>,
    receivedSykmelding: ReceivedSykmelding,
    validationResult: ValidationResult,
    navKontorNr: String,
    loggingMeta: LoggingMeta,
    oppgaveTopic: String
) {
    kafkaProducer.send(ProducerRecord(oppgaveTopic, receivedSykmelding.sykmelding.id,
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
    log.info("Message sendt to topic: aapen-syfo-oppgave-produserOppgave, {}", fields(loggingMeta))
}

inline fun <reified T> XMLEIFellesformat.get() = this.any.find { it is T } as T

fun extractHelseOpplysningerArbeidsuforhet(fellesformat: XMLEIFellesformat): HelseOpplysningerArbeidsuforhet =
        fellesformat.get<XMLMsgHead>().document[0].refDoc.content.any[0] as HelseOpplysningerArbeidsuforhet

fun ClosedRange<LocalDate>.daysBetween(): Long = ChronoUnit.DAYS.between(start, endInclusive)

fun validationResult(results: List<Rule<Any>>): ValidationResult =
        ValidationResult(
                status = results
                        .map { status -> status.status }.let {
                            it.firstOrNull { status -> status == Status.MANUAL_PROCESSING }
                                    ?: Status.OK
                        },
                ruleHits = results.map { rule -> RuleInfo(rule.name, rule.messageForUser!!, rule.messageForSender!!, rule.status) }
        )
fun List<HelseOpplysningerArbeidsuforhet.Aktivitet.Periode>.sortedFOMDate(): List<LocalDate> =
        map { it.periodeFOMDato }.sorted()
