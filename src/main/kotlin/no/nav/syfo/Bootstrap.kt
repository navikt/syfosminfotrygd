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
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.jackson.jackson
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
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.exception.ServiceUnavailableException
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.client.ManuellClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.SyketilfelleClient
import no.nav.syfo.client.norg.Norg2Client
import no.nav.syfo.client.norg.Norg2RedisService
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.OpprettOppgaveKafkaMessage
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.mq.MqTlsUtils
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.pdl.PdlFactory
import no.nav.syfo.rules.validation.sortedPeriodeFOMDate
import no.nav.syfo.services.BehandlingsutfallService
import no.nav.syfo.services.FinnNAVKontorService
import no.nav.syfo.services.ManuellBehandlingService
import no.nav.syfo.services.MottattSykmeldingService
import no.nav.syfo.services.OppgaveService
import no.nav.syfo.services.RedisService
import no.nav.syfo.services.SykmeldingService
import no.nav.syfo.services.updateinfotrygd.UpdateInfotrygdService
import no.nav.syfo.smregister.SmregisterClient
import no.nav.syfo.util.JacksonKafkaSerializer
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.TrackableException
import no.nav.syfo.util.fellesformatUnmarshaller
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.io.StringReader
import java.io.StringWriter
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
const val NAV_VIKAFOSSEN_KONTOR_NR = "2103"
const val UTENLANDSK_SYKEHUS = "9900004"

@DelicateCoroutinesApi
fun main() {
    val env = Environment()
    val serviceUser = ServiceUser()
    MqTlsUtils.getMqTlsConfig().forEach { key, value -> System.setProperty(key as String, value as String) }
    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
        env,
        applicationState,
    )

    val applicationServer = ApplicationServer(applicationEngine)

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
            },
    )

    MQEnvironment.channel = env.mqChannelName
    MQEnvironment.port = env.mqPort
    MQEnvironment.hostname = env.mqHostname
    MQEnvironment.userID = serviceUser.serviceuserUsername
    MQEnvironment.password = serviceUser.serviceuserPassword

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        install(HttpTimeout) {
            socketTimeoutMillis = 6000
        }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                when (exception) {
                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                }
            }
        }
        install(HttpRequestRetry) {
            constantDelay(100, 0, false)
            retryOnExceptionIf(3) { _, throwable ->
                log.warn("Caught exception ${throwable.message}")
                true
            }
            retryIf(maxRetries) { _, response ->
                if (response.status.value.let { it in 500..599 }) {
                    log.warn("Retrying for statuscode ${response.status.value}")
                    true
                } else {
                    false
                }
            }
        }
    }

    val httpClient = HttpClient(Apache, config)

    val jedisPool = JedisPool(JedisPoolConfig(), env.redisHost, env.redisPort)
    val redisService = RedisService(jedisPool, env.redisSecret)

    val norg2Client = Norg2Client(httpClient, env.norg2V1EndpointURL, Norg2RedisService(jedisPool, env.redisSecret))

    val accessTokenClientV2 =
        AccessTokenClientV2(env.aadAccessTokenV2Url, env.clientIdV2, env.clientSecretV2, httpClient)
    val norskHelsenettClient =
        NorskHelsenettClient(httpClient, env.norskHelsenettEndpointURL, accessTokenClientV2, env.helsenettproxyScope)
    val pdlPersonService = PdlFactory.getPdlService(env, httpClient, accessTokenClientV2, env.pdlScope)
    val finnNAVKontorService = FinnNAVKontorService(pdlPersonService, norg2Client)

    val manuellClient = ManuellClient(httpClient, env.manuellUrl, accessTokenClientV2, env.manuellScope)
    val syketilfelleClient = SyketilfelleClient(env.syketilfelleEndpointURL, accessTokenClientV2, env.syketilfelleScope, httpClient)
    val sykmeldingService = SykmeldingService(
        smregisterClient = SmregisterClient(
            smregisterEndpointURL = env.smregisterEndpointURL,
            accessTokenClientV2 = accessTokenClientV2,
            scope = env.smregisterAudience,
            httpClient = httpClient,
        ),
    )
    val behandlingsutfallService = BehandlingsutfallService(
        kafkaAivenProducerBehandlingsutfall = kafkaAivenProducerBehandlingsutfall,
        behandlingsUtfallTopic = env.behandlingsUtfallTopic,
    )

    val oppgaveService = OppgaveService(
        kafkaAivenProducerOppgave = kafkaAivenProducerOppgave,
        produserOppgaveTopic = env.produserOppgaveTopic,
    )
    val manuellBehandlingService = ManuellBehandlingService(
        behandlingsutfallService = behandlingsutfallService,
        redisService = redisService,
        oppgaveService = oppgaveService,
        applicationState = applicationState,
        sykmeldingService = sykmeldingService,
    )

    val updateInfotrygdService = UpdateInfotrygdService(
        kafkaAivenProducerReceivedSykmelding = kafkaAivenProducerReceivedSykmelding,
        retryTopic = env.retryTopic,
        behandlingsutfallService = behandlingsutfallService,
        redisService = redisService,
    )

    val mottattSykmeldingService = MottattSykmeldingService(
        updateInfotrygdService = updateInfotrygdService,
        finnNAVKontorService = finnNAVKontorService,
        manuellClient = manuellClient,
        manuellBehandlingService = manuellBehandlingService,
        behandlingsutfallService = behandlingsutfallService,
        norskHelsenettClient = norskHelsenettClient,
        syketilfelleClient = syketilfelleClient,
    )

    launchListeners(
        applicationState,
        env,
        serviceUser,
        kafkaAivenConsumerReceivedSykmelding,
        mottattSykmeldingService,
    )

    applicationServer.start()
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
    env: Environment,
    serviceUser: ServiceUser,
    kafkaAivenConsumerReceivedSykmelding: KafkaConsumer<String, String>,
    mottattSykmeldingService: MottattSykmeldingService,
) {
    createListener(applicationState) {
        connectionFactory(env).createConnection(serviceUser.serviceuserUsername, serviceUser.serviceuserPassword)
            .use { connection ->
                connection.start()
                val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
                val infotrygdOppdateringProducer =
                    session.producerForQueue("queue:///${env.infotrygdOppdateringQueue}?targetClient=1")
                val infotrygdSporringProducer =
                    session.producerForQueue("queue:///${env.infotrygdSporringQueue}?targetClient=1")

                blockingApplicationLogic(
                    applicationState,
                    infotrygdOppdateringProducer,
                    infotrygdSporringProducer,
                    session,
                    env,
                    kafkaAivenConsumerReceivedSykmelding,
                    mottattSykmeldingService,
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
    env: Environment,
    kafkaAivenConsumerReceivedSykmelding: KafkaConsumer<String, String>,
    mottattSykmeldingService: MottattSykmeldingService,
) {
    while (applicationState.ready) {
        if (shouldRun(getCurrentTime())) {
            log.info("Starter kafkaconsumer")
            kafkaAivenConsumerReceivedSykmelding.subscribe(
                listOf(env.okSykmeldingTopic, env.retryTopic),
            )
            runKafkaConsumer(
                infotrygdOppdateringProducer,
                infotrygdSporringProducer,
                session,
                applicationState,
                kafkaAivenConsumerReceivedSykmelding,
                mottattSykmeldingService,
            )
            kafkaAivenConsumerReceivedSykmelding.unsubscribe()
            log.info("Stopper KafkaConsumer")
        }
        delay(100)
    }
}

private suspend fun runKafkaConsumer(
    infotrygdOppdateringProducer: MessageProducer,
    infotrygdSporringProducer: MessageProducer,
    session: Session,
    applicationState: ApplicationState,
    kafkaAivenConsumerReceivedSykmelding: KafkaConsumer<String, String>,
    mottattSykmeldingService: MottattSykmeldingService,
) {
    while (applicationState.ready && shouldRun(getCurrentTime())) {
        kafkaAivenConsumerReceivedSykmelding.poll(Duration.ofMillis(0)).mapNotNull { it.value() }
            .forEach { receivedSykmeldingString ->
                val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(receivedSykmeldingString)
                val loggingMeta = LoggingMeta(
                    mottakId = receivedSykmelding.navLogId,
                    orgNr = receivedSykmelding.legekontorOrgNr,
                    msgId = receivedSykmelding.msgId,
                    sykmeldingId = receivedSykmelding.sykmelding.id,
                )
                log.info("Har mottatt sykmelding, {}", fields(loggingMeta))
                mottattSykmeldingService.handleMessage(
                    receivedSykmelding = receivedSykmelding,
                    infotrygdOppdateringProducer = infotrygdOppdateringProducer,
                    infotrygdSporringProducer = infotrygdSporringProducer,
                    session = session,
                    loggingMeta = loggingMeta,
                )
            }
        delay(100)
    }
}

fun getCurrentTime(): OffsetTime {
    return OffsetTime.now(ZoneId.of("Europe/Oslo"))
}

fun shouldRun(now: OffsetTime): Boolean {
    return now.hour in 5..20
}

fun Marshaller.toString(input: Any): String = StringWriter().use {
    marshal(input, it)
    it.toString()
}

fun ReceivedSykmelding.erUtenlandskSykmelding(): Boolean {
    return utenlandskSykmelding != null
}

fun ReceivedSykmelding.erTilbakedatert(): Boolean {
    return sykmelding.behandletTidspunkt.toLocalDate() > sykmelding.perioder.sortedPeriodeFOMDate().first().plusDays(8)
}

val inputFactory = XMLInputFactory.newInstance()!!
inline fun <reified T> unmarshal(text: String): T =
    fellesformatUnmarshaller.unmarshal(inputFactory.createXMLEventReader(StringReader(text)), T::class.java).value

inline fun <reified T> XMLEIFellesformat.get() = this.any.find { it is T } as T

fun extractHelseOpplysningerArbeidsuforhet(fellesformat: XMLEIFellesformat): HelseOpplysningerArbeidsuforhet =
    fellesformat.get<XMLMsgHead>().document[0].refDoc.content.any[0] as HelseOpplysningerArbeidsuforhet

fun ClosedRange<LocalDate>.daysBetween(): Long = ChronoUnit.DAYS.between(start, endInclusive)

fun List<HelseOpplysningerArbeidsuforhet.Aktivitet.Periode>.sortedFOMDate(): List<LocalDate> =
    map { it.periodeFOMDato }.sorted()

data class InfotrygdForespAndHealthInformation(
    val infotrygdForesp: InfotrygdForesp,
    val healthInformation: HelseOpplysningerArbeidsuforhet,
)
