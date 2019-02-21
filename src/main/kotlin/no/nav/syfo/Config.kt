package no.nav.syfo

import java.nio.file.Path
import java.nio.file.Paths

val vaultApplicationPropertiesPath: Path = Paths.get("/var/run/secrets/nais.io/vault/credentials.json")

data class ApplicationConfig(
    val applicationThreads: Int = 1,
    val applicationPort: Int = 8080,
    val mqHostname: String,
    val mqPort: Int,
    val mqGatewayName: String,
    val mqChannelName: String,
    val kafkaBootstrapServers: String,
    val sm2013AutomaticHandlingTopic: String = "privat-syfo-sm2013-automatiskBehandling",
    val smPaperAutomaticHandlingTopic: String = "privat-syfo-smpapir-automatiskBehandling",
    val personV3EndpointURL: String,
    val securityTokenServiceUrl: String,
    val infotrygdSporringQueue: String,
    val infotrygdOppdateringQueue: String,
    val arbeidsfordelingV1EndpointURL: String
)

data class VaultCredentials(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val mqUsername: String,
    val mqPassword: String
)
