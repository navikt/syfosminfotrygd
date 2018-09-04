package no.nav.syfo

import io.ktor.application.Application
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers.append
import no.kith.xmlstds.msghead._2006_05_24.XMLMsgHead
import no.kith.xmlstds.msghead._2006_05_24.XMLOrganisation
import no.nav.syfo.api.registerNaisApi
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import no.trygdeetaten.xml.eiff._1.XMLMottakenhetBlokk
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.StringReader
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.xml.bind.JAXBContext
import javax.xml.bind.Unmarshaller

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

val jaxBContext: JAXBContext = JAXBContext.newInstance(XMLEIFellesformat::class.java, XMLMsgHead::class.java,
        XMLMottakenhetBlokk::class.java)
val unmarshaller: Unmarshaller = jaxBContext.createUnmarshaller()

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.sminfotrygd")

fun main(args: Array<String>) = runBlocking {
    val env = Environment()
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    try {
        val listeners = (1..env.applicationThreads).map {
            launch {
                val consumerProperties = readConsumerConfig(env, valueDeserializer = StringDeserializer::class)
                val producerProperties = readProducerConfig(env, valueSerializer = StringSerializer::class)
                val kafkaconsumer = KafkaConsumer<String, String>(consumerProperties)
                kafkaconsumer.subscribe(listOf(env.syfoMottakInfotrygdRouteTopic, env.kafkaSM2013PapirmottakTopic))
                val kafkaproducer = KafkaProducer<String, String>(producerProperties)

                blockingApplicationLogic(applicationState, kafkaconsumer, kafkaproducer, env)
            }
        }.toList()

        applicationState.initialized = true

        Runtime.getRuntime().addShutdownHook(Thread {
            applicationServer.stop(10, 10, TimeUnit.SECONDS)
        })
        runBlocking { listeners.forEach { it.join() } }
    } finally {
        applicationState.running = false
    }
}

suspend fun blockingApplicationLogic(applicationState: ApplicationState, kafkaconsumer: KafkaConsumer<String, String>, kafkaproducer: KafkaProducer<String, String>, env: Environment) {
    while (applicationState.running) {
        kafkaconsumer.poll(Duration.ofMillis(0)).forEach {
            val fellesformat = unmarshaller.unmarshal(StringReader(it.value())) as XMLEIFellesformat
            val msgHead: XMLMsgHead = fellesformat.get()
            val mottakEnhetBlokk: XMLMottakenhetBlokk = fellesformat.get()
            val marker = append("msgId", msgHead.msgInfo.msgId)
                    .and<LogstashMarker>(append("ediLoggId", mottakEnhetBlokk.ediLoggId))
                    .and<LogstashMarker>(append("organizationNumber", msgHead.msgInfo.sender.organisation.extractOrganizationNumber()))
            log.info(marker, "Received a SM2013, going through rules and persisting in infotrygd")

            kafkaproducer.send(ProducerRecord(env.syfoMottakOppgaveGsakInfotrygdTopic, it.value()))
        }
        delay(100)
    }
}

fun XMLOrganisation.extractOrganizationNumber(): String? = ident.find { it.typeId.v == "ENH" }?.id

inline fun <reified T> XMLEIFellesformat.get(): T = any.find { it is T } as T

fun Application.initRouting(applicationState: ApplicationState) {
    routing {
        registerNaisApi(
                readynessCheck = {
                    applicationState.initialized
                },
                livenessCheck = {
                    applicationState.running
                }
        )
    }
}
