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
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.features.HttpTimeout
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.tssSamhandlerData.XMLTssSamhandlerData
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.client.Norg2Client
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.SyfosmreglerClient
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.metrics.MESSAGES_ON_INFOTRYGD_SMIKKEOK_QUEUE_COUNTER
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.metrics.RULE_HIT_STATUS_COUNTER
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.RuleMetadata
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.pdl.PdlFactory
import no.nav.syfo.rules.Rule
import no.nav.syfo.rules.TssRuleChain
import no.nav.syfo.rules.ValidationRuleChain
import no.nav.syfo.rules.executeFlow
import no.nav.syfo.rules.sortedSMInfos
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.services.FinnNAVKontorService
import no.nav.syfo.services.UpdateInfotrygdService
import no.nav.syfo.services.fetchInfotrygdForesp
import no.nav.syfo.services.fetchTssSamhandlerInfo
import no.nav.syfo.util.JacksonKafkaSerializer
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.TrackableException
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.wrapExceptions
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import java.io.StringReader
import java.io.StringWriter
import java.net.ProxySelector
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Properties
import javax.jms.MessageProducer
import javax.jms.Session
import javax.xml.bind.Marshaller
import javax.xml.stream.XMLInputFactory

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
    val vaultServiceUser = VaultServiceUser()
    val credentials = objectMapper.readValue<VaultCredentials>(Paths.get("/var/run/secrets/nais.io/vault/credentials.json").toFile())
    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
        env,
        applicationState
    )

    val applicationServer = ApplicationServer(applicationEngine)
    applicationServer.start()

    DefaultExports.initialize()

    val kafkaBaseConfig = loadBaseConfig(env, vaultServiceUser)
    kafkaBaseConfig["auto.offset.reset"] = "none"
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

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        install(HttpTimeout) {
            socketTimeoutMillis = 5000
        }
    }

    val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        config()
        engine {
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
            }
        }
    }

    val httpClient = HttpClient(Apache, config)
    val httpClientWithProxy = HttpClient(Apache, proxyConfig)

    val norg2Client = Norg2Client(httpClient, env.norg2V1EndpointURL)

    val accessTokenClientV2 = AccessTokenClientV2(env.aadAccessTokenV2Url, env.clientIdV2, env.clientSecretV2, httpClientWithProxy)
    val norskHelsenettClient = NorskHelsenettClient(httpClient, env.norskHelsenettEndpointURL, accessTokenClientV2, env.helsenettproxyScope)
    val pdlPersonService = PdlFactory.getPdlService(env, httpClient, accessTokenClientV2, env.pdlScope)
    val finnNAVKontorService = FinnNAVKontorService(pdlPersonService, norg2Client)

    val syfosmreglerClient = SyfosmreglerClient(env.syfosmreglerUrl, httpClient)

    launchListeners(
        applicationState,
        kafkaproducerCreateTask,
        kafkaproducervalidationResult,
        finnNAVKontorService,
        env,
        norskHelsenettClient,
        syfosmreglerClient,
        consumerProperties,
        smIkkeOkQueue,
        kafkaproducerreceivedSykmelding,
        credentials
    )
}

fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
    GlobalScope.launch {
        try {
            action()
        } catch (e: TrackableException) {
            log.error("En uhåndtert feil oppstod, applikasjonen restarter {}", fields(e.loggingMeta), e.cause)
        } finally {
            applicationState.alive = false
            applicationState.ready = false
        }
    }

@KtorExperimentalAPI
fun launchListeners(
    applicationState: ApplicationState,
    kafkaproducerCreateTask: KafkaProducer<String, ProduceTask>,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    finnNAVKontorService: FinnNAVKontorService,
    env: Environment,
    norskHelsenettClient: NorskHelsenettClient,
    syfosmreglerClient: SyfosmreglerClient,
    consumerProperties: Properties,
    smIkkeOkQueue: MQQueue,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    credentials: VaultCredentials
) {
    val kafkaconsumerRecievedSykmelding = KafkaConsumer<String, String>(consumerProperties)

    createListener(applicationState) {
        connectionFactory(env).createConnection(credentials.mqUsername, credentials.mqPassword).use { connection ->
            Jedis(env.redisHost, 6379).use { jedis ->
                connection.start()
                val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
                val infotrygdOppdateringProducer = session.producerForQueue("queue:///${env.infotrygdOppdateringQueue}?targetClient=1")
                val infotrygdSporringProducer = session.producerForQueue("queue:///${env.infotrygdSporringQueue}?targetClient=1")
                val tssProducer = session.producerForQueue("queue:///${env.tssQueue}?targetClient=1")

                jedis.auth(env.redisSecret)

                applicationState.ready = true

                blockingApplicationLogic(
                    applicationState, kafkaconsumerRecievedSykmelding, kafkaproducerCreateTask,
                    kafkaproducervalidationResult, infotrygdOppdateringProducer, infotrygdSporringProducer,
                    session, finnNAVKontorService, env.sm2013BehandlingsUtfallToipic, norskHelsenettClient, syfosmreglerClient,
                    smIkkeOkQueue, jedis, kafkaproducerreceivedSykmelding, env.sm2013infotrygdRetry,
                    env.sm2013OpppgaveTopic, tssProducer, env
                )
            }
        }
    }

    applicationState.alive = true
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
    finnNAVKontorService: FinnNAVKontorService,
    sm2013BehandlingsUtfallToipic: String,
    norskHelsenettClient: NorskHelsenettClient,
    syfosmreglerClient: SyfosmreglerClient,
    smIkkeOkQueue: MQQueue,
    jedis: Jedis,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    infotrygdRetryTopic: String,
    oppgaveTopic: String,
    tssProducer: MessageProducer,
    env: Environment
) {
    while (applicationState.ready) {
        if (shouldRun(getCurrentTime())) {
            log.info("Starter KafkaConsumer")
            kafkaConsumer.subscribe(
                listOf(env.sm2013AutomaticHandlingTopic, env.sm2013infotrygdRetry)
            )
            runKafkaConsumer(kafkaConsumer, kafkaproducerCreateTask, kafkaproducervalidationResult, infotrygdOppdateringProducer, infotrygdSporringProducer, session, finnNAVKontorService, sm2013BehandlingsUtfallToipic, norskHelsenettClient, syfosmreglerClient, smIkkeOkQueue, jedis, kafkaproducerreceivedSykmelding, infotrygdRetryTopic, oppgaveTopic, applicationState, tssProducer)
            kafkaConsumer.unsubscribe()
            log.info("Stopper KafkaConsumer")
        }
        delay(100)
    }
}

@KtorExperimentalAPI
private suspend fun runKafkaConsumer(
    kafkaConsumer: KafkaConsumer<String, String>,
    kafkaproducerCreateTask: KafkaProducer<String, ProduceTask>,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    infotrygdOppdateringProducer: MessageProducer,
    infotrygdSporringProducer: MessageProducer,
    session: Session,
    finnNAVKontorService: FinnNAVKontorService,
    sm2013BehandlingsUtfallTopic: String,
    norskHelsenettClient: NorskHelsenettClient,
    syfosmreglerClient: SyfosmreglerClient,
    smIkkeOkQueue: MQQueue,
    jedis: Jedis,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    infotrygdRetryTopic: String,
    oppgaveTopic: String,
    applicationState: ApplicationState,
    tssProducer: MessageProducer
) {
    while (applicationState.ready && shouldRun(getCurrentTime())) {
        kafkaConsumer.poll(Duration.ofMillis(0)).mapNotNull { it.value() }.forEach { receivedSykmeldingString ->
            val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(receivedSykmeldingString)
            val loggingMeta = LoggingMeta(
                mottakId = receivedSykmelding.navLogId,
                orgNr = receivedSykmelding.legekontorOrgNr,
                msgId = receivedSykmelding.msgId,
                sykmeldingId = receivedSykmelding.sykmelding.id
            )
            when (skalOppdatereInfotrygd(receivedSykmelding)) {
                true -> {
                    handleMessage(
                        receivedSykmelding, syfosmreglerClient, kafkaproducerCreateTask, kafkaproducervalidationResult,
                        infotrygdOppdateringProducer, infotrygdSporringProducer,
                        session, finnNAVKontorService, sm2013BehandlingsUtfallTopic, norskHelsenettClient,
                        smIkkeOkQueue, loggingMeta, jedis, kafkaproducerreceivedSykmelding,
                        infotrygdRetryTopic, oppgaveTopic, applicationState, tssProducer
                    )
                }
                else -> {
                    log.info("Skal ikke oppdatere infotrygd ved merknad ${receivedSykmelding.merknader?.joinToString { it.type }} {}", fields(loggingMeta))
                    val validationResult = if (receivedSykmelding.merknader?.any { it.type == "UNDER_BEHANDLING" } == true) {
                        ValidationResult(Status.OK, listOf(RuleInfo("UNDER_BEHANDLING", "Sykmeldingen er til manuell behandling", "Sykmeldingen er til manuell behandling", Status.OK)))
                    } else {
                        ValidationResult(Status.OK, emptyList())
                    }
                    sendRuleCheckValidationResult(receivedSykmelding, kafkaproducervalidationResult, validationResult, sm2013BehandlingsUtfallTopic, loggingMeta)
                }
            }
        }
        delay(100)
    }
}

fun skalOppdatereInfotrygd(receivedSykmelding: ReceivedSykmelding): Boolean {
    return receivedSykmelding.merknader?.none {
        it.type == "UGYLDIG_TILBAKEDATERING" ||
            it.type == "TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER" ||
            it.type == "TILBAKEDATERT_PAPIRSYKMELDING" ||
            it.type == "UNDER_BEHANDLING"
    } ?: true
}

fun getCurrentTime(): OffsetTime {
    return OffsetTime.now(ZoneId.of("Europe/Oslo"))
}

fun shouldRun(now: OffsetTime): Boolean {
    return now.hour in 5..20
}

@KtorExperimentalAPI
suspend fun handleMessage(
    receivedSykmelding: ReceivedSykmelding,
    syfosmreglerClient: SyfosmreglerClient,
    kafkaproducerCreateTask: KafkaProducer<String, ProduceTask>,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    infotrygdOppdateringProducer: MessageProducer,
    infotrygdSporringProducer: MessageProducer,
    session: Session,
    finnNAVKontorService: FinnNAVKontorService,
    sm2013BehandlingsUtfallToipic: String,
    norskHelsenettClient: NorskHelsenettClient,
    smIkkeOkQueue: MQQueue,
    loggingMeta: LoggingMeta,
    jedis: Jedis,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    infotrygdRetryTopic: String,
    oppgaveTopic: String,
    applicationState: ApplicationState,
    tssProducer: MessageProducer
) {
    wrapExceptions(loggingMeta) {
        log.info("Received a SM2013, {}", fields(loggingMeta))

        val smIkkeOkCurrentDepth = smIkkeOkQueue.currentDepth.toDouble()
        MESSAGES_ON_INFOTRYGD_SMIKKEOK_QUEUE_COUNTER.set(smIkkeOkCurrentDepth)

        val requestLatency = REQUEST_TIME.startTimer()

        val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(receivedSykmelding.fellesformat)) as XMLEIFellesformat
        val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)

        val validationResultForMottattSykmelding = validerMottattSykmelding(healthInformation)
        if (validationResultForMottattSykmelding.status == Status.MANUAL_PROCESSING) {
            log.info("Mottatt sykmelding kan ikke legges inn i infotrygd automatisk, oppretter oppgave, {}", fields(loggingMeta))
            sendRuleCheckValidationResult(receivedSykmelding, kafkaproducervalidationResult, validationResultForMottattSykmelding, sm2013BehandlingsUtfallToipic, loggingMeta)
            UpdateInfotrygdService().opprettOppgave(kafkaproducerCreateTask, syfosmreglerClient, receivedSykmelding, validationResultForMottattSykmelding, loggingMeta, oppgaveTopic)
        } else {
            val infotrygdForespResponse = fetchInfotrygdForesp(
                receivedSykmelding,
                infotrygdSporringProducer,
                session,
                healthInformation
            )

            var receivedSykmeldingMedTssId = receivedSykmelding

            if (receivedSykmelding.tssid.isNullOrBlank()) {
                val tssIdInfotrygd = finnTssIdFraInfotrygdRespons(
                    infotrygdForespResponse.sMhistorikk?.sykmelding?.sortedSMInfos()?.lastOrNull()?.periode,
                    receivedSykmelding.sykmelding.behandler
                )
                if (!tssIdInfotrygd.isNullOrBlank()) {
                    log.info("Sykmelding mangler tssid, har hentet tssid $tssIdInfotrygd fra infotrygd, {}", fields(loggingMeta))
                    receivedSykmeldingMedTssId = receivedSykmelding.copy(tssid = tssIdInfotrygd)
                } else {
                    try {
                        val tssSamhandlerInfoResponse = fetchTssSamhandlerInfo(receivedSykmelding, tssProducer, session)

                        val tssIdFraTSS = finnTssIdFraTSSRespons(tssSamhandlerInfoResponse)

                        if (!tssIdFraTSS.isNullOrBlank()) {
                            log.info("Sykmelding mangler tssid, har hentet tssid $tssIdFraTSS fra tss, {}", fields(loggingMeta))
                            receivedSykmeldingMedTssId = receivedSykmelding.copy(tssid = tssIdFraTSS)
                        }
                        log.info("Fant ingen tssider fra TSS!!!")
                    } catch (e: Exception) {
                        log.error("Kall mot TSS gikk dårligt", e)
                    }
                }
            }

            val validationResult = ruleCheck(receivedSykmeldingMedTssId, infotrygdForespResponse, loggingMeta)

            val lokaltNavkontor = finnNAVKontorService.finnLokaltNavkontor(receivedSykmelding.personNrPasient, loggingMeta)

            UpdateInfotrygdService().updateInfotrygd(
                receivedSykmeldingMedTssId,
                syfosmreglerClient,
                norskHelsenettClient,
                validationResult,
                infotrygdOppdateringProducer,
                kafkaproducerCreateTask,
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
                sm2013BehandlingsUtfallToipic,
                applicationState
            )
        }
        val currentRequestLatency = requestLatency.observeDuration()

        log.info(
            "Message processing took {}s, for message {}",
            currentRequestLatency.toString(),
            fields(loggingMeta)
        )
    }
}

fun finnTssIdFraInfotrygdRespons(sisteSmPeriode: TypeSMinfo.Periode?, behandler: Behandler): String? {
    if (sisteSmPeriode != null &&
        behandler.etternavn.equals(sisteSmPeriode.legeNavn?.etternavn, true) &&
        behandler.fornavn.equals(sisteSmPeriode.legeNavn?.fornavn, true)
    ) {
        return sisteSmPeriode.legeInstNr?.toString()
    }
    return null
}

fun finnTssIdFraTSSRespons(tssSamhandlerInfoResponse: XMLTssSamhandlerData): String? {
    return tssSamhandlerInfoResponse.tssOutputData.samhandlerODataB960?.enkeltSamhandler?.firstOrNull()?.samhandlerAvd125?.samhAvd?.find {
        it.avdNr == "01"
    }?.idOffTSS
}

fun validerMottattSykmelding(helseOpplysningerArbeidsuforhet: HelseOpplysningerArbeidsuforhet): ValidationResult {
    return if (helseOpplysningerArbeidsuforhet.medisinskVurdering.hovedDiagnose == null) {
        RULE_HIT_STATUS_COUNTER.labels("MANUAL_PROCESSING").inc()
        log.warn("Sykmelding mangler hoveddiagnose")
        ValidationResult(
            Status.MANUAL_PROCESSING,
            listOf(
                RuleInfo(
                    "HOVEDDIAGNOSE_MANGLER",
                    "Sykmeldingen inneholder ingen hoveddiagnose, vi kan ikke automatisk oppdatere Infotrygd",
                    "Sykmeldingen inneholder ingen hoveddiagnose, vi kan ikke automatisk oppdatere Infotrygd",
                    Status.MANUAL_PROCESSING
                )
            )
        )
    } else {
        ValidationResult(Status.OK, emptyList())
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
        infotrygdForespResponse
    )

    val tssRuleResults = TssRuleChain.values().executeFlow(
        receivedSykmelding.sykmelding,
        RuleMetadata(
            receivedDate = receivedSykmelding.mottattDato,
            signatureDate = receivedSykmelding.sykmelding.signaturDato,
            patientPersonNumber = receivedSykmelding.personNrPasient,
            rulesetVersion = receivedSykmelding.rulesetVersion,
            legekontorOrgnr = receivedSykmelding.legekontorOrgNr,
            tssid = receivedSykmelding.tssid
        )
    )

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
    try {
        kafkaproducervalidationResult.send(ProducerRecord(sm2013BehandlingsUtfallToipic, receivedSykmelding.sykmelding.id, validationResult)).get()
        log.info("Validation results send to kafka {} $loggingMeta", sm2013BehandlingsUtfallToipic, fields(loggingMeta))
    } catch (ex: Exception) {
        log.error("Error writing validationResult to kafka for sykmelding {} {}", loggingMeta.sykmeldingId, loggingMeta)
        throw ex
    }
}

fun Marshaller.toString(input: Any): String = StringWriter().use {
    marshal(input, it)
    it.toString()
}

val inputFactory = XMLInputFactory.newInstance()!!
inline fun <reified T> unmarshal(text: String): T = fellesformatUnmarshaller.unmarshal(inputFactory.createXMLEventReader(StringReader(text)), T::class.java).value

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

data class InfotrygdForespAndHealthInformation(
    val infotrygdForesp: InfotrygdForesp,
    val healthInformation: HelseOpplysningerArbeidsuforhet
)
