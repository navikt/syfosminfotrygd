package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials
import no.nav.syfo.mq.MqConfig
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosminfotrygd"),
    val naiscluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val sm2013AutomaticHandlingTopic: String = getEnvVar("KAFKA_SM2013_AUTOMATIC_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),
    val sm2013BehandlingsUtfallToipic: String = getEnvVar("KAFKA_SM2013_BEHANDLING_TOPIC", "privat-syfo-sm2013-behandlingsUtfall"),
    val sm2013infotrygdRetry: String = getEnvVar("KAFKA_SM2013_INFOTRYGD_RETRY_TOPIC", "privat-syfo-sminfotrygd-retry"),
    val sm2013OpppgaveTopic: String = getEnvVar("KAFKA_SM2013_OPPGAVE_TOPIC", "aapen-syfo-oppgave-produserOppgave"),
    val infotrygdSporringQueue: String = getEnvVar("INFOTRYGD_SPORRING_QUEUE"),
    val infotrygdOppdateringQueue: String = getEnvVar("INFOTRYGD_OPPDATERING_QUEUE"),
    val norg2V1EndpointURL: String = getEnvVar("NORG2_V1_ENDPOINT_URL"),
    override val mqHostname: String = getEnvVar("MQ_HOST_NAME"),
    override val mqPort: Int = getEnvVar("MQ_PORT").toInt(),
    override val mqGatewayName: String = getEnvVar("MQ_GATEWAY_NAME"),
    override val mqChannelName: String = getEnvVar("MQ_CHANNEL_NAME"),
    val norskHelsenettEndpointURL: String = getEnvVar("HELSENETT_ENDPOINT_URL"),
    val redisHost: String = getEnvVar("REDIS_HOST", "syfosminfotrygd-redis.teamsykmelding.svc.nais.local"),
    val redisPort: Int = 6379,
    val redisSecret: String = getEnvVar("REDIS_PASSWORD"),
    val tssQueue: String = getEnvVar("MQ_TSS_SAMHANDLER_SERVICE_QUEUE"),
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    val aadAccessTokenV2Url: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val clientIdV2: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val clientSecretV2: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val pdlScope: String = getEnvVar("PDL_SCOPE"),
    override val truststore: String? = getEnvVar("NAV_TRUSTSTORE_PATH"),
    override val truststorePassword: String? = getEnvVar("NAV_TRUSTSTORE_PASSWORD"),
    override val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val helsenettproxyScope: String = getEnvVar("HELSENETT_SCOPE"),
    val manuellUrl: String = "http://syfosmmanuell-backend",
    val manuellScope: String = getEnvVar("MANUELL_SCOPE"),
    val okSykmeldingTopic: String = "teamsykmelding.ok-sykmelding",
    val behandlingsUtfallTopic: String = "teamsykmelding.sykmelding-behandlingsutfall",
    val produserOppgaveTopic: String = "teamsykmelding.oppgave-produser-oppgave",
    val retryTopic: String = "teamsykmelding.privat-sminfotrygd-retry"
) : MqConfig, KafkaConfig

data class VaultServiceUser(
    val serviceuserUsername: String = getFileAsString("/secrets/serviceuser/username"),
    val serviceuserPassword: String = getFileAsString("/secrets/serviceuser/password")
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

data class VaultCredentials(
    val mqUsername: String,
    val mqPassword: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
