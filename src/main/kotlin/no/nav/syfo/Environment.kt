package no.nav.syfo

import no.nav.syfo.mq.MqConfig

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosminfotrygd"),
    val naiscluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val infotrygdSporringQueue: String = getEnvVar("INFOTRYGD_SPORRING_QUEUE"),
    val infotrygdOppdateringQueue: String = getEnvVar("INFOTRYGD_OPPDATERING_QUEUE"),
    val norg2V1EndpointURL: String =
        getEnvVar("NORG2_V1_ENDPOINT_URL", "http://norg2.org/norg2/api/v1"),
    override val mqHostname: String = getEnvVar("MQ_HOST_NAME"),
    override val mqPort: Int = getEnvVar("MQ_PORT").toInt(),
    override val mqGatewayName: String = getEnvVar("MQ_GATEWAY_NAME"),
    override val mqChannelName: String = getEnvVar("MQ_CHANNEL_NAME"),
    val norskHelsenettEndpointURL: String = "http://syfohelsenettproxy",
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    val aadAccessTokenV2Url: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val clientIdV2: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val clientSecretV2: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val pdlScope: String = getEnvVar("PDL_SCOPE"),
    val helsenettproxyScope: String = getEnvVar("HELSENETT_SCOPE"),
    val manuellUrl: String = "http://syfosmmanuell-backend",
    val manuellScope: String = getEnvVar("MANUELL_SCOPE"),
    val okSykmeldingTopic: String = "teamsykmelding.ok-sykmelding",
    val produserOppgaveTopic: String = "teamsykmelding.oppgave-produser-oppgave",
    val retryTopic: String = "teamsykmelding.privat-sminfotrygd-retry",
    val syketilfelleEndpointURL: String =
        getEnvVar("SYKETILLFELLE_ENDPOINT_URL", "http://flex-syketilfelle.flex"),
    val syketilfelleScope: String = getEnvVar("SYKETILLFELLE_SCOPE"),
    val smregisterEndpointURL: String = getEnvVar("SMREGISTER_URL", "http://syfosmregister"),
    val smregisterAudience: String = getEnvVar("SMREGISTER_AUDIENCE"),
    val jwkKeysUrlV2: String = getEnvVar("AZURE_OPENID_CONFIG_JWKS_URI"),
    val jwtIssuerV2: String = getEnvVar("AZURE_OPENID_CONFIG_ISSUER"),
) : MqConfig

data class ServiceUser(
    val serviceuserUsername: String = getEnvVar("SERVICEUSER_USERNAME"),
    val serviceuserPassword: String = getEnvVar("SERVICEUSER_PASSWORD"),
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName)
        ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
