package no.nav.syfo

import java.nio.file.Files
import java.nio.file.Paths
import java.util.Properties

private val vaultApplicationPropertiesPath = Paths.get("/var/run/secrets/nais.io/vault/application.properties")

private val config = Properties().apply {
    putAll(Properties().apply {
        load(Environment::class.java.getResourceAsStream("/application.properties"))
    })
    if (Files.exists(vaultApplicationPropertiesPath)) {
        load(Files.newInputStream(vaultApplicationPropertiesPath))
    }
}
fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

data class Environment(
    val applicationPort: Int = config.getProperty("application.port").toInt(),
    val applicationThreads: Int = config.getProperty("application.threads").toInt(),
    val srvsminfotrygdUsername: String = config.getProperty("serviceuser.username"),
    val srvsminfotrygdPassword: String = config.getProperty("serviceuser.password"),
    val sm2013AutomaticHandlingTopic: String = config.getProperty("kafka.syfo.sm2013.automatiskBehandling.topic"),
    val smPaperAutomaticHandlingTopic: String = config.getProperty("kafka.syfo.smpapir.automatiskBehandling.topic"),
    val mqPort: Int = config.getProperty("mq.port").toInt(),
    val mqUsername: String = config.getProperty("mq.username"),
    val mqPassword: String = config.getProperty("mq.password"),
    val syfoSmRegelerApiURL: String = config.getProperty("http.syfosmapprec.url"),

    val organisasjonEnhetV2EndpointURL: String = getEnvVar("VIRKSOMHET_ORGANISASJONENHET_V2_ENDPOINTURL"),
    val personV3EndpointURL: String = getEnvVar("VIRKSOMHET_PERSON_V3_ENDPOINTURL"),
    val securityTokenServiceUrl: String = getEnvVar("SECURITYTOKENSERVICE_URL"),
    val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val infotrygdSporringQueue: String = getEnvVar("EIA_QUEUE_INFOTRYGD_REQUEST_QUEUENAME"),
    val infotrygdOppdateringQueue: String = getEnvVar("EIA_QUEUE_INFOTRYGD_OUTBOUND_QUEUENAME"),
    val mqHostname: String = getEnvVar("MQGATEWAY03_HOSTNAME"),
    val mqQueueManagerName: String = getEnvVar("MQGATEWAY03_NAME"),
    val mqChannelName: String = getEnvVar("SYFOSMINFOTRYGD_CHANNEL_NAME")
)
