package no.nav.syfo

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "4").toInt(),
    val srvsminfotrygdUsername: String = getEnvVar("SRVSYFOSMINFOTRYGD_USERNAME"),
    val srvsminfotrygdPassword: String = getEnvVar("SRVSYFOSMINFOTRYGD_PASSWORD"),
    val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val sm2013AutomaticHandlingTopic: String = getEnvVar("SM2013_AUTOMATIC_HANDLING_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),
    val smPaperAutomaticHandlingTopic: String = getEnvVar("SMPAPER_AUTOMATIC_HANDLING_TOPIC", "privat-syfo-smpapir-automatiskBehandling"),
    val smGsakTaskCreationTopic: String = getEnvVar("SMINFOTRYGD_MANUAL_HANDLING_TOPIC", "privat-syfo-sminfotrygd-manualHandling")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
