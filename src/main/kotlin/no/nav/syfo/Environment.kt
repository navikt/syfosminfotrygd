package no.nav.syfo

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "4").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosminfotrygd"),
    val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val sm2013AutomaticHandlingTopic: String = getEnvVar("KAFKA_SM2013_AUTOMATIC_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),
    val smPaperAutomaticHandlingTopic: String = getEnvVar("KAFKA_SMPAPIR_AUTOMATIC_TOPIC", "privat-syfo-smpapir-automatiskBehandling"),
    val personV3EndpointURL: String = getEnvVar("PERSON_V3_ENDPOINT_URL"),
    val securityTokenServiceUrl: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL"),
    val infotrygdSporringQueue: String = getEnvVar("INFOTRYGD_SPORRING_QUEUE"),
    val infotrygdOppdateringQueue: String = getEnvVar("INFOTRYGD_OPPDATERING_QUEUE"),
    val arbeidsfordelingV1EndpointURL: String = getEnvVar("ARBEIDSFORDELING_V1_ENDPOINT_URL"),
    val mqHostname: String = getEnvVar("MQ_HOST_NAME"),
    val mqPort: Int = getEnvVar("MQ_PORT").toInt(),
    val mqGatewayName: String = getEnvVar("MQ_GATEWAY_NAME"),
    val mqChannelName: String = getEnvVar("MQ_CHANNEL_NAME")
)

data class VaultCredentials(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val mqUsername: String,
    val mqPassword: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
