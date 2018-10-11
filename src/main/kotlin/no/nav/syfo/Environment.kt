package no.nav.syfo

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "4").toInt(),
    val srvsminfotrygdUsername: String = getEnvVar("SRVSYFOSMINFOTRYGD_USERNAME", "srvsminfotrygd"),
    val srvsminfotrygdPassword: String = getEnvVar("SRVSYFOSMINFOTRYGD_PASSWORD", "srvsminfotrygdPassword"),
    val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL", "SSL://12345.test.local:8443"),
    val sm2013AutomaticHandlingTopic: String = getEnvVar("SM2013_AUTOMATIC_HANDLING_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),
    val smPaperAutomaticHandlingTopic: String = getEnvVar("SMPAPER_AUTOMATIC_HANDLING_TOPIC", "privat-syfo-smpapir-automatiskBehandling"),
    val infotrygdSporringQueue: String = getEnvVar("INFOTRYGD_REQUEST_Q4_QUEUENAME", "infotrygdSporringQueue"),
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
