package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials
import no.nav.syfo.mq.MqConfig

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosminfotrygd"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val sm2013AutomaticHandlingTopic: String = getEnvVar("KAFKA_SM2013_AUTOMATIC_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),
    val smPaperAutomaticHandlingTopic: String = getEnvVar("KAFKA_SMPAPIR_AUTOMATIC_TOPIC", "privat-syfo-smpapir-automatiskBehandling"),
    val sm2013BehandlingsUtfallToipic: String = getEnvVar("KAFKA_SM2013_BEHANDLING_TOPIC", "privat-syfo-sm2013-behandlingsUtfall"),
    val sm2013infotrygdRetry: String = getEnvVar("KAFKA_SM2013_INFOTRYGD_RETRY_TOPIC", "privat-syfo-sminfotrygd-retry"),
    val sm2013OpppgaveTopic: String = getEnvVar("KAFKA_SM2013_OPPGAVE_TOPIC", "aapen-syfo-oppgave-produserOppgave"),
    val personV3EndpointURL: String = getEnvVar("PERSON_V3_ENDPOINT_URL"),
    val securityTokenServiceUrl: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL"),
    val infotrygdSporringQueue: String = getEnvVar("INFOTRYGD_SPORRING_QUEUE"),
    val infotrygdOppdateringQueue: String = getEnvVar("INFOTRYGD_OPPDATERING_QUEUE"),
    val norg2V1EndpointURL: String = getEnvVar("NORG2_V1_ENDPOINT_URL"),
    override val mqHostname: String = getEnvVar("MQ_HOST_NAME"),
    override val mqPort: Int = getEnvVar("MQ_PORT").toInt(),
    override val mqGatewayName: String = getEnvVar("MQ_GATEWAY_NAME"),
    override val mqChannelName: String = getEnvVar("MQ_CHANNEL_NAME"),
    val norskHelsenettEndpointURL: String = getEnvVar("HELSENETT_ENDPOINT_URL", "http://syfohelsenettproxy"),
    val clientId: String = getEnvVar("CLIENT_ID"),
    val helsenettproxyId: String = getEnvVar("HELSENETTPROXY_ID"),
    val aadAccessTokenUrl: String = getEnvVar("AADACCESSTOKEN_URL"),
    val infotrygdSmIkkeOKQueue: String = getEnvVar("MQ_INFOTRYGD_SMIKKEOK_QUEUE"),
    val redishost: String = getEnvVar("REDIS_HOST", "syfosminfotrygd-redis.default.svc.nais.local")
) : MqConfig, KafkaConfig

data class VaultCredentials(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val mqUsername: String,
    val mqPassword: String,
    val clientsecret: String
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
