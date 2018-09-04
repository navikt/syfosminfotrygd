package no.nav.syfo

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "4").toInt(),
    val srvsminfotrygdUsername: String = getEnvVar("SRVSYFOSMINFOTRYGD_USERNAME"),
    val srvsminfotrygdPassword: String = getEnvVar("SRVSYFOSMINFOTRYGD_PASSWORD"),
    val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val syfoMottakInfotrygdRouteTopic: String = getEnvVar("SYFOMOTTAK_INFOTRYGD_ROUTE_TOPIC", "privat-syfomottak-sm2013-infotrygdRoute"),
    val kafkaSM2013PapirmottakTopic: String = getEnvVar("KAFKA_SM2013_PAPIR_MOTTAK_TOPIC", "privat-syfosmpapirmottak-sm2013"),
    val syfoMottakOppgaveGsakInfotrygdTopic: String = getEnvVar("SYFOMOTTAK_OPPGAVE_GSAK_INFOTRYGD_TOPIC", "privat-syfomottak-sm2013-oppgaveGsakInfotrygd")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
