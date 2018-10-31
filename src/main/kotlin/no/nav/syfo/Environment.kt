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

data class Environment(
    val applicationPort: Int = config.getProperty("application.port").toInt(),
    val applicationThreads: Int = config.getProperty("application.threads").toInt(),
    val srvsminfotrygdUsername: String = config.getProperty("serviceuser.username"),
    val srvsminfotrygdPassword: String = config.getProperty("serviceuser.password"),
    val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL", "SSL://12345.test.local:8443"),
    val sm2013AutomaticHandlingTopic: String = getEnvVar("SM2013_AUTOMATIC_HANDLING_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),
    val smPaperAutomaticHandlingTopic: String = getEnvVar("SMPAPER_AUTOMATIC_HANDLING_TOPIC", "privat-syfo-smpapir-automatiskBehandling"),
    val infotrygdSporringQueue: String = getEnvVar("EIA_QUEUE_INFOTRYGD_REQUEST_QUEUENAME", "infotrygdSporringQueue"),
    val infotrygdOppdateringQueue: String = getEnvVar("EIA_QUEUE_INFOTRYGD_OUTBOUND_QUEUENAME", "infotrygdOppdateringQueue"),
    val mqHostname: String = getEnvVar("MQGATEWAY03_HOSTNAME", "mqHostname"),
    val mqPort: Int = getEnvVar("MQGATEWAY03_PORT", "1413").toInt(),
    val mqQueueManagerName: String = getEnvVar("MQGATEWAY03_NAME", "mqQueueManagerName"),
    val mqChannelName: String = getEnvVar("SYFOSMINFOTRYGD_CHANNEL_NAME", "mqChannelName"),
    val mqUsername: String = getEnvVar("SRVAPPSERVER_USERNAME", "srvappserver"),
    val mqPassword: String = getEnvVar("SRVAPPSERVER_PASSWORD", "")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
