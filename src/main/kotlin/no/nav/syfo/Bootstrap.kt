package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.ibm.mq.MQEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.features.HttpResponseValidator
import io.ktor.client.features.HttpTimeout
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.network.sockets.SocketTimeoutException
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
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
import no.nav.syfo.application.exception.ServiceUnavailableException
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.client.ManuellClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.norg.Norg2Client
import no.nav.syfo.client.norg.Norg2RedisService
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.metrics.RULE_HIT_STATUS_COUNTER
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.OpprettOppgaveKafkaMessage
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.RuleMetadata
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.pdl.PdlFactory
import no.nav.syfo.rules.RuleResult
import no.nav.syfo.rules.TssRuleChain
import no.nav.syfo.rules.ValidationRuleChain
import no.nav.syfo.rules.sortedSMInfos
import no.nav.syfo.services.BehandlingsutfallService
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
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.io.StringReader
import java.io.StringWriter
import java.net.ProxySelector
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
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

@DelicateCoroutinesApi
fun main() {
    val env = Environment()
    val credentials =
        objectMapper.readValue<VaultCredentials>(Paths.get("/var/run/secrets/nais.io/vault/credentials.json").toFile())
    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
        env,
        applicationState
    )

    val applicationServer = ApplicationServer(applicationEngine)
    applicationServer.start()

    DefaultExports.initialize()

    val kafkaAivenBaseConfig = KafkaUtils.getAivenKafkaConfig()
    val kafkaAivenProducerProperties =
        kafkaAivenBaseConfig.toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)
    val kafkaAivenProducerReceivedSykmelding = KafkaProducer<String, ReceivedSykmelding>(kafkaAivenProducerProperties)
    val kafkaAivenProducerBehandlingsutfall = KafkaProducer<String, ValidationResult>(kafkaAivenProducerProperties)
    val kafkaAivenProducerOppgave = KafkaProducer<String, OpprettOppgaveKafkaMessage>(kafkaAivenProducerProperties)

    val kafkaAivenConsumerReceivedSykmelding = KafkaConsumer<String, String>(
        kafkaAivenBaseConfig
            .toConsumerConfig("${env.applicationName}-consumer", valueDeserializer = StringDeserializer::class)
            .also {
                it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
                it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 1
            }
    )

    MQEnvironment.channel = env.mqChannelName
    MQEnvironment.port = env.mqPort
    MQEnvironment.hostname = env.mqHostname
    MQEnvironment.userID = credentials.mqUsername
    MQEnvironment.password = credentials.mqPassword

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
        HttpResponseValidator {
            handleResponseException { exception ->
                when (exception) {
                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                }
            }
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

    val jedisPool = JedisPool(JedisPoolConfig(), env.redisHost, env.redisPort)
    val jedis = jedisPool.resource
    jedis.auth(env.redisSecret)

    val norg2Client = Norg2Client(httpClient, env.norg2V1EndpointURL, Norg2RedisService(jedis))

    val accessTokenClientV2 =
        AccessTokenClientV2(env.aadAccessTokenV2Url, env.clientIdV2, env.clientSecretV2, httpClientWithProxy)
    val norskHelsenettClient =
        NorskHelsenettClient(httpClient, env.norskHelsenettEndpointURL, accessTokenClientV2, env.helsenettproxyScope)
    val pdlPersonService = PdlFactory.getPdlService(env, httpClient, accessTokenClientV2, env.pdlScope)
    val finnNAVKontorService = FinnNAVKontorService(pdlPersonService, norg2Client)

    val manuellClient = ManuellClient(httpClient, env.manuellUrl, accessTokenClientV2, env.manuellScope)
    val behandlingsutfallService = BehandlingsutfallService(
        kafkaAivenProducerBehandlingsutfall = kafkaAivenProducerBehandlingsutfall,
        behandlingsUtfallTopic = env.behandlingsUtfallTopic
    )

    val updateInfotrygdService = UpdateInfotrygdService(
        manuellClient = manuellClient,
        norskHelsenettClient = norskHelsenettClient,
        applicationState = applicationState,
        kafkaAivenProducerReceivedSykmelding = kafkaAivenProducerReceivedSykmelding,
        kafkaAivenProducerOppgave = kafkaAivenProducerOppgave,
        retryTopic = env.retryTopic,
        produserOppgaveTopic = env.produserOppgaveTopic,
        behandlingsutfallService = behandlingsutfallService
    )

    launchListeners(
        applicationState,
        finnNAVKontorService,
        env,
        updateInfotrygdService,
        credentials,
        jedis,
        kafkaAivenConsumerReceivedSykmelding,
        behandlingsutfallService
    )
}

@DelicateCoroutinesApi
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

@DelicateCoroutinesApi
fun launchListeners(
    applicationState: ApplicationState,
    finnNAVKontorService: FinnNAVKontorService,
    env: Environment,
    updateInfotrygdService: UpdateInfotrygdService,
    credentials: VaultCredentials,
    jedis: Jedis,
    kafkaAivenConsumerReceivedSykmelding: KafkaConsumer<String, String>,
    behandlingsutfallService: BehandlingsutfallService
) {
    createListener(applicationState) {
        connectionFactory(env).createConnection(credentials.mqUsername, credentials.mqPassword).use { connection ->
            connection.start()
            val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            val infotrygdOppdateringProducer =
                session.producerForQueue("queue:///${env.infotrygdOppdateringQueue}?targetClient=1")
            val infotrygdSporringProducer =
                session.producerForQueue("queue:///${env.infotrygdSporringQueue}?targetClient=1")
            val tssProducer = session.producerForQueue("queue:///${env.tssQueue}?targetClient=1")

            applicationState.ready = true

            blockingApplicationLogic(
                applicationState, infotrygdOppdateringProducer, infotrygdSporringProducer,
                session, finnNAVKontorService, updateInfotrygdService,
                jedis, tssProducer, env, kafkaAivenConsumerReceivedSykmelding, behandlingsutfallService
            )
        }
    }

    applicationState.alive = true
}

suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    infotrygdOppdateringProducer: MessageProducer,
    infotrygdSporringProducer: MessageProducer,
    session: Session,
    finnNAVKontorService: FinnNAVKontorService,
    updateInfotrygdService: UpdateInfotrygdService,
    jedis: Jedis,
    tssProducer: MessageProducer,
    env: Environment,
    kafkaAivenConsumerReceivedSykmelding: KafkaConsumer<String, String>,
    behandlingsutfallService: BehandlingsutfallService
) {
    while (applicationState.ready) {
        if (shouldRun(getCurrentTime())) {
            log.info("Starter kafkaconsumer aiven")
            kafkaAivenConsumerReceivedSykmelding.subscribe(
                listOf(env.okSykmeldingTopic, env.retryTopic)
            )
            runKafkaConsumer(
                infotrygdOppdateringProducer,
                infotrygdSporringProducer,
                session,
                finnNAVKontorService,
                updateInfotrygdService,
                jedis,
                applicationState,
                tssProducer,
                kafkaAivenConsumerReceivedSykmelding,
                behandlingsutfallService
            )
            kafkaAivenConsumerReceivedSykmelding.unsubscribe()
            log.info("Stopper KafkaConsumer aiven")
        }
        delay(100)
    }
}

private suspend fun runKafkaConsumer(
    infotrygdOppdateringProducer: MessageProducer,
    infotrygdSporringProducer: MessageProducer,
    session: Session,
    finnNAVKontorService: FinnNAVKontorService,
    updateInfotrygdService: UpdateInfotrygdService,
    jedis: Jedis,
    applicationState: ApplicationState,
    tssProducer: MessageProducer,
    kafkaAivenConsumerReceivedSykmelding: KafkaConsumer<String, String>,
    behandlingsutfallService: BehandlingsutfallService
) {
    while (applicationState.ready && shouldRun(getCurrentTime())) {
        kafkaAivenConsumerReceivedSykmelding.poll(Duration.ofMillis(0)).mapNotNull { it.value() }
            .forEach { receivedSykmeldingString ->
                val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(receivedSykmeldingString)
                val loggingMeta = LoggingMeta(
                    mottakId = receivedSykmelding.navLogId,
                    orgNr = receivedSykmelding.legekontorOrgNr,
                    msgId = receivedSykmelding.msgId,
                    sykmeldingId = receivedSykmelding.sykmelding.id
                )
                log.info("Har mottatt sykmelding fra aiven, {}", fields(loggingMeta))
                when (skalOppdatereInfotrygd(receivedSykmelding)) {
                    true -> {
                        handleMessage(
                            receivedSykmelding, updateInfotrygdService,
                            infotrygdOppdateringProducer, infotrygdSporringProducer,
                            session, finnNAVKontorService,
                            loggingMeta, jedis, tssProducer, behandlingsutfallService
                        )
                    }
                    else -> {
                        log.info(
                            "Oppdaterer ikke infotrygd for sykmelding med merknad eller reisetilskudd",
                            fields(loggingMeta)
                        )
                        val validationResult =
                            if (receivedSykmelding.merknader?.any { it.type == "UNDER_BEHANDLING" } == true) {
                                ValidationResult(
                                    Status.OK,
                                    listOf(
                                        RuleInfo(
                                            "UNDER_BEHANDLING",
                                            "Sykmeldingen er til manuell behandling",
                                            "Sykmeldingen er til manuell behandling",
                                            Status.OK
                                        )
                                    )
                                )
                            } else {
                                ValidationResult(Status.OK, emptyList())
                            }
                        behandlingsutfallService.sendRuleCheckValidationResult(
                            receivedSykmelding,
                            validationResult,
                            loggingMeta
                        )
                    }
                }
            }
        delay(100)
    }
}

fun skalOppdatereInfotrygd(receivedSykmelding: ReceivedSykmelding): Boolean {
    // Vi skal ikke oppdatere Infotrygd hvis sykmeldingen inneholder en av de angitte merknadene
    val merknad = receivedSykmelding.merknader?.none {
        it.type == "UGYLDIG_TILBAKEDATERING" ||
            it.type == "TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER" ||
            it.type == "TILBAKEDATERT_PAPIRSYKMELDING" ||
            it.type == "UNDER_BEHANDLING"
    } ?: true

    // Vi skal ikke oppdatere infotrygd hvis sykmeldingen inneholder reisetilskudd
    val reisetilskudd = receivedSykmelding.sykmelding.perioder.none {
        it.reisetilskudd || (it.gradert?.reisetilskudd == true)
    }

    return merknad && reisetilskudd
}

fun getCurrentTime(): OffsetTime {
    return OffsetTime.now(ZoneId.of("Europe/Oslo"))
}

fun shouldRun(now: OffsetTime): Boolean {
    return now.hour in 5..20
}

suspend fun handleMessage(
    receivedSykmelding: ReceivedSykmelding,
    updateInfotrygdService: UpdateInfotrygdService,
    infotrygdOppdateringProducer: MessageProducer,
    infotrygdSporringProducer: MessageProducer,
    session: Session,
    finnNAVKontorService: FinnNAVKontorService,
    loggingMeta: LoggingMeta,
    jedis: Jedis,
    tssProducer: MessageProducer,
    behandlingsutfallService: BehandlingsutfallService
) {
    wrapExceptions(loggingMeta) {
        log.info("Received a SM2013, {}", fields(loggingMeta))

        val requestLatency = REQUEST_TIME.startTimer()

        val fellesformat =
            fellesformatUnmarshaller.unmarshal(StringReader(receivedSykmelding.fellesformat)) as XMLEIFellesformat
        val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)

        val validationResultForMottattSykmelding = validerMottattSykmelding(healthInformation)
        if (validationResultForMottattSykmelding.status == Status.MANUAL_PROCESSING) {
            log.info(
                "Mottatt sykmelding kan ikke legges inn i infotrygd automatisk, oppretter oppgave, {}",
                fields(loggingMeta)
            )
            behandlingsutfallService.sendRuleCheckValidationResult(
                receivedSykmelding,
                validationResultForMottattSykmelding,
                loggingMeta
            )
            updateInfotrygdService.opprettOppgave(receivedSykmelding, validationResultForMottattSykmelding, loggingMeta)
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
                    log.info(
                        "Sykmelding mangler tssid, har hentet tssid $tssIdInfotrygd fra infotrygd, {}",
                        fields(loggingMeta)
                    )
                    receivedSykmeldingMedTssId = receivedSykmelding.copy(tssid = tssIdInfotrygd)
                } else {
                    try {
                        val tssSamhandlerInfoResponse = fetchTssSamhandlerInfo(receivedSykmelding, tssProducer, session)

                        val tssIdFraTSS = finnTssIdFraTSSRespons(tssSamhandlerInfoResponse)

                        if (!tssIdFraTSS.isNullOrBlank()) {
                            log.info(
                                "Sykmelding mangler tssid, har hentet tssid $tssIdFraTSS fra tss, {}",
                                fields(loggingMeta)
                            )
                            receivedSykmeldingMedTssId = receivedSykmelding.copy(tssid = tssIdFraTSS)
                        }
                        log.info("Fant ingen tssider fra TSS!!!")
                    } catch (e: Exception) {
                        log.error("Kall mot TSS gikk dårligt", e)
                    }
                }
            }

            val validationResult = ruleCheck(receivedSykmeldingMedTssId, infotrygdForespResponse, loggingMeta)

            val lokaltNavkontor =
                finnNAVKontorService.finnLokaltNavkontor(receivedSykmelding.personNrPasient, loggingMeta)

            updateInfotrygdService.updateInfotrygd(
                receivedSykmeldingMedTssId,
                validationResult,
                infotrygdOppdateringProducer,
                lokaltNavkontor,
                loggingMeta,
                session,
                infotrygdForespResponse,
                healthInformation,
                jedis
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

    val results = listOf(
        ValidationRuleChain(receivedSykmelding.sykmelding, infotrygdForespResponse).executeRules(),
        TssRuleChain(
            RuleMetadata(
                receivedDate = receivedSykmelding.mottattDato,
                signatureDate = receivedSykmelding.sykmelding.signaturDato,
                patientPersonNumber = receivedSykmelding.personNrPasient,
                rulesetVersion = receivedSykmelding.rulesetVersion,
                legekontorOrgnr = receivedSykmelding.legekontorOrgNr,
                tssid = receivedSykmelding.tssid
            )
        ).executeRules(),
    ).flatten().filter { it.result }

    log.info("Rules hit {}, $loggingMeta", results.map { ruleResult -> ruleResult.rule.name }, fields(loggingMeta))

    val validationResult = validationResult(results)
    RULE_HIT_STATUS_COUNTER.labels(validationResult.status.name).inc()
    return validationResult
}

fun Marshaller.toString(input: Any): String = StringWriter().use {
    marshal(input, it)
    it.toString()
}

val inputFactory = XMLInputFactory.newInstance()!!
inline fun <reified T> unmarshal(text: String): T =
    fellesformatUnmarshaller.unmarshal(inputFactory.createXMLEventReader(StringReader(text)), T::class.java).value

inline fun <reified T> XMLEIFellesformat.get() = this.any.find { it is T } as T

fun extractHelseOpplysningerArbeidsuforhet(fellesformat: XMLEIFellesformat): HelseOpplysningerArbeidsuforhet =
    fellesformat.get<XMLMsgHead>().document[0].refDoc.content.any[0] as HelseOpplysningerArbeidsuforhet

fun ClosedRange<LocalDate>.daysBetween(): Long = ChronoUnit.DAYS.between(start, endInclusive)

fun validationResult(results: List<RuleResult<*>>): ValidationResult =
    ValidationResult(
        status = results
            .map { it.rule.status }.let {
                it.firstOrNull { status -> status == Status.MANUAL_PROCESSING }
                    ?: Status.OK
            },
        ruleHits = results.map { ruleReuslt ->
            RuleInfo(
                ruleReuslt.rule.name,
                ruleReuslt.rule.messageForUser,
                ruleReuslt.rule.messageForSender,
                ruleReuslt.rule.status
            )
        }
    )

fun List<HelseOpplysningerArbeidsuforhet.Aktivitet.Periode>.sortedFOMDate(): List<LocalDate> =
    map { it.periodeFOMDato }.sorted()

data class InfotrygdForespAndHealthInformation(
    val infotrygdForesp: InfotrygdForesp,
    val healthInformation: HelseOpplysningerArbeidsuforhet
)
