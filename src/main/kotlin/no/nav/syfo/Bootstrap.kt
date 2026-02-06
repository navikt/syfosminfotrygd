package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
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
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.prometheus.client.hotspot.DefaultExports
import java.io.StringReader
import java.io.StringWriter
import java.net.URI
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.xml.bind.Marshaller
import javax.xml.stream.XMLInputFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.application.exception.ServiceUnavailableException
import no.nav.syfo.application.setupAuth
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.client.ManuellClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.norg.Norg2Client
import no.nav.syfo.client.norg.Norg2ValkeyService
import no.nav.syfo.infotrygd.InfotrygdService
import no.nav.syfo.infotrygd.registerInfotrygdApi
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.OpprettOppgaveKafkaMessage
import no.nav.syfo.model.sykmelding.ReceivedSykmelding
import no.nav.syfo.mq.MqTlsUtils
import no.nav.syfo.pdl.PdlFactory
import no.nav.syfo.rules.validation.sortedPeriodeFOMDate
import no.nav.syfo.services.FinnNAVKontorService
import no.nav.syfo.services.ManuellBehandlingService
import no.nav.syfo.services.MottattSykmeldingService
import no.nav.syfo.services.OppgaveService
import no.nav.syfo.services.SykmeldingConsumerService
import no.nav.syfo.services.ValkeyService
import no.nav.syfo.services.updateinfotrygd.UpdateInfotrygdService
import no.nav.syfo.tss.SmtssClient
import no.nav.syfo.util.JacksonKafkaSerializer
import no.nav.syfo.util.createJedisPool
import no.nav.syfo.util.fellesformatUnmarshaller
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.sminfotrygd")
val objectMapper: ObjectMapper =
    ObjectMapper().apply {
        enable(SerializationFeature.INDENT_OUTPUT)
        registerKotlinModule()
        registerModule(JavaTimeModule())
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

const val NAV_OPPFOLGING_UTLAND_KONTOR_NR = "2101"
const val NAV_VIKAFOSSEN_KONTOR_NR = "2103"
const val UTENLANDSK_SYKEHUS = "9900004"

@DelicateCoroutinesApi
fun main() {

    val embeddedServer =
        embeddedServer(
            Netty,
            port = Environment().applicationPort,
            module = Application::module,
        )
    Runtime.getRuntime()
        .addShutdownHook(
            Thread {
                log.info("Shutting down application from shutdown hook")
                embeddedServer.stop(TimeUnit.SECONDS.toMillis(10), TimeUnit.SECONDS.toMillis(10))
            },
        )
    embeddedServer.start(true)
}

@OptIn(DelicateCoroutinesApi::class)
suspend fun Application.module() {
    MqTlsUtils.getMqTlsConfig().forEach { (key, value) ->
        System.setProperty(key as String, value as String)
    }
    val env = Environment()
    val serviceUser = ServiceUser()
    val applicationState = ApplicationState()
    install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }

    setupAuth(
        JwkProviderBuilder(URI.create(env.jwkKeysUrlV2).toURL())
            .cached(10, java.time.Duration.ofHours(24))
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build(),
        env,
    )

    DefaultExports.initialize()
    val kafkaAivenProducerReceivedSykmelding =
        KafkaProducer<String, ReceivedSykmelding>(getkafkaProducerConfig("retry-producer", env))
    val kafkaAivenProducerOppgave =
        KafkaProducer<String, OpprettOppgaveKafkaMessage>(
            getkafkaProducerConfig("oppgave-producer", env),
        )

    val kafkaAivenConsumerReceivedSykmelding =
        KafkaConsumer<String, String>(getkafkaConsumerConfig("sykmelding-consumer", env))

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
        install(HttpTimeout) { socketTimeoutMillis = 6000 }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                when (exception) {
                    is SocketTimeoutException ->
                        throw ServiceUnavailableException(exception.message)
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
    val jedisPool = createJedisPool()
    val valkeyService = ValkeyService(jedisPool)

    val norg2Client = Norg2Client(httpClient, env.norg2V1EndpointURL, Norg2ValkeyService(jedisPool))

    val accessTokenClientV2 =
        AccessTokenClientV2(env.aadAccessTokenV2Url, env.clientIdV2, env.clientSecretV2, httpClient)
    val norskHelsenettClient =
        NorskHelsenettClient(
            httpClient,
            env.norskHelsenettEndpointURL,
            accessTokenClientV2,
            env.helsenettproxyScope,
        )
    val pdlPersonService =
        PdlFactory.getPdlService(env, httpClient, accessTokenClientV2, env.pdlScope)
    val finnNAVKontorService = FinnNAVKontorService(pdlPersonService, norg2Client)

    val manuellClient =
        ManuellClient(httpClient, env.manuellUrl, accessTokenClientV2, env.manuellScope)

    val oppgaveService =
        OppgaveService(
            kafkaAivenProducerOppgave = kafkaAivenProducerOppgave,
            produserOppgaveTopic = env.produserOppgaveTopic,
        )
    val manuellBehandlingService =
        ManuellBehandlingService(
            valkeyService = valkeyService,
            oppgaveService = oppgaveService,
            applicationState = applicationState,
        )

    val updateInfotrygdService =
        UpdateInfotrygdService(
            kafkaAivenProducerReceivedSykmelding = kafkaAivenProducerReceivedSykmelding,
            retryTopic = env.retryTopic,
            valkeyService = valkeyService,
        )
    val smtssClient =
        SmtssClient(env.smtssEndpointURL, accessTokenClientV2, env.smtssScope, httpClient)
    val mottattSykmeldingService =
        MottattSykmeldingService(
            updateInfotrygdService = updateInfotrygdService,
            finnNAVKontorService = finnNAVKontorService,
            manuellClient = manuellClient,
            manuellBehandlingService = manuellBehandlingService,
            norskHelsenettClient = norskHelsenettClient,
            cluster = env.naiscluster,
            smtssClient = smtssClient
        )
    routing {
        registerNaisApi(applicationState)
        authenticate("servicebrukerAAD") {
            registerInfotrygdApi(
                InfotrygdService(
                    finnNAVKontorService,
                    serviceUser,
                    env,
                    env.infotrygdSporringQueue,
                    env.infotrygdOppdateringQueue
                )
            )
        }
    }

    val sykmeldingConsumerService =
        SykmeldingConsumerService(
            mottattSykmeldingService,
            env,
            serviceUser,
            kafkaAivenConsumerReceivedSykmelding,
            applicationState
        )
    monitor.subscribe(ApplicationStarted) {
        log.info("Application is ready -> starting kafka consumer")
        launch { sykmeldingConsumerService.startConsumer() }
    }

    monitor.subscribe(ApplicationStopping) { applicationState.ready = false }
}

private fun getkafkaProducerConfig(producerId: String, env: Environment) =
    KafkaUtils.getAivenKafkaConfig(producerId)
        .toProducerConfig(
            env.applicationName,
            valueSerializer = JacksonKafkaSerializer::class,
        )

private fun getkafkaConsumerConfig(consumerId: String, env: Environment) =
    KafkaUtils.getAivenKafkaConfig(consumerId)
        .toConsumerConfig(
            "${env.applicationName}-consumer",
            valueDeserializer = StringDeserializer::class,
        )
        .also {
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
            it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 1
        }

fun getCurrentTime(): OffsetDateTime {
    return OffsetDateTime.now(ZoneId.of("Europe/Oslo"))
}

fun CoroutineScope.shouldRun(now: OffsetDateTime): Boolean {
    return isActive && now.hour in 5..20
}

fun Marshaller.toString(input: Any): String =
    StringWriter().use {
        marshal(input, it)
        it.toString()
    }

fun ReceivedSykmelding.erUtenlandskSykmelding(): Boolean {
    return utenlandskSykmelding != null
}

fun ReceivedSykmelding.erTilbakedatert(): Boolean {
    return sykmelding.signaturDato.toLocalDate() >
        sykmelding.perioder.sortedPeriodeFOMDate().first().plusDays(8)
}

val inputFactory = XMLInputFactory.newInstance()!!

inline fun <reified T> unmarshal(text: String): T =
    fellesformatUnmarshaller
        .unmarshal(inputFactory.createXMLEventReader(StringReader(text)), T::class.java)
        .value

inline fun <reified T> XMLEIFellesformat.get() = this.any.find { it is T } as T

fun extractHelseOpplysningerArbeidsuforhet(
    fellesformat: XMLEIFellesformat
): HelseOpplysningerArbeidsuforhet =
    fellesformat.get<XMLMsgHead>().document[0].refDoc.content.any[0]
        as HelseOpplysningerArbeidsuforhet

fun ClosedRange<LocalDate>.daysBetween(): Long = ChronoUnit.DAYS.between(start, endInclusive)

fun List<HelseOpplysningerArbeidsuforhet.Aktivitet.Periode>.sortedFOMDate(): List<LocalDate> =
    map { it.periodeFOMDato }.sorted()

data class InfotrygdForespAndHealthInformation(
    val infotrygdForesp: InfotrygdForesp,
    val healthInformation: HelseOpplysningerArbeidsuforhet,
)
