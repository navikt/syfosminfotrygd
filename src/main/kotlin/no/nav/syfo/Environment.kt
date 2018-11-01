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
    val kafkaBootstrapServers: String = config.getProperty("kafka.bootstrap.servers.url"),
    val sm2013AutomaticHandlingTopic: String = config.getProperty("kafka.syfo.sm2013.automatiskBehandling.topic"),
    val smPaperAutomaticHandlingTopic: String = config.getProperty("kafka.syfo.smpapir.automatiskBehandling.topic"),
    val infotrygdSporringQueue: String = config.getProperty("mq.queue.infotrygd.request.queuename"),
    val infotrygdOppdateringQueue: String = config.getProperty("mq.queue.infotrygd.outbound.queuename"),
    val mqHostname: String = config.getProperty("mq.gateway03.hostname"),
    val mqPort: Int = config.getProperty("mq.port").toInt(),
    val mqQueueManagerName: String = config.getProperty("mq.queueManager.name"),
    val mqChannelName: String = config.getProperty("mq.channel.name"),
    val mqUsername: String = config.getProperty("mq.username"),
    val mqPassword: String = config.getProperty("mq.password ")
)
