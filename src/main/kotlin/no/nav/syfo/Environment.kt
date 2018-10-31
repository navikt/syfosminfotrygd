package no.nav.syfo

import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import java.io.File

private const val vaultApplicationPropertiesPath = "/var/run/secrets/nais.io/vault/application.properties"

private val config = if (System.getenv("APPLICATION_PROFILE") == "remote") {
    systemProperties() overriding
            EnvironmentVariables() overriding
            ConfigurationProperties.fromFile(File(vaultApplicationPropertiesPath)) overriding
            ConfigurationProperties.fromResource("application.properties")
} else {
    systemProperties() overriding
            EnvironmentVariables() overriding
            ConfigurationProperties.fromResource("application.properties")
}

data class Environment(
    val isDevProfile: Boolean = config[Key("application.profile", stringType)] == "local",

    val applicationPort: Int = config[Key("application.port", intType)],
    val applicationThreads: Int = config[Key("application.threads", intType)],
    val srvsminfotrygdUsername: String = config[Key("serviceuser.username", stringType)],
    val srvsminfotrygdPassword: String = config[Key("serviceuser.password", stringType)],
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
