package no.nav.syfo

import no.nav.common.KafkaEnvironment
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.ServerSocket
import java.time.Duration

object KafkaITSpek : Spek({
    val topic = "aapen-test-topic"
    fun getRandomPort() = ServerSocket(0).use {
        it.localPort
    }

    val embeddedEnvironment = KafkaEnvironment(
            autoStart = false,
            topics = listOf(topic)
    )

    val credentials = VaultCredentials("", "", "", "")
    val config = ApplicationConfig(mqHostname = "mqhost", mqPort = getRandomPort(),
            mqGatewayName = "mqGateway", kafkaBootstrapServers = embeddedEnvironment.brokersURL,
            mqChannelName = "syfomottak", infotrygdOppdateringQueue = "apprequeue",
            infotrygdSporringQueue = "infotrygdqueue",
            personV3EndpointURL = "personApi", securityTokenServiceUrl = "secApi", arbeidsfordelingV1EndpointURL = "arkapi",
            sm2013AutomaticHandlingTopic = "topic1", smPaperAutomaticHandlingTopic = "topic2"
    )

    val producer = KafkaProducer<String, String>(readProducerConfig(config, credentials, StringSerializer::class).apply {
        remove("security.protocol")
        remove("sasl.mechanism")
    })

    val consumer = KafkaConsumer<String, String>(readConsumerConfig(config, credentials, StringDeserializer::class).apply {
        remove("security.protocol")
        remove("sasl.mechanism")
    })
    consumer.subscribe(listOf(topic))

    beforeGroup {
        embeddedEnvironment.start()
    }

    afterGroup {
        embeddedEnvironment.stop()
    }

    describe("Push a message on a topic") {
        val message = "Test message"
        it("Can read the messages from the kafka topic") {
            producer.send(ProducerRecord(topic, message))

            val messages = consumer.poll(Duration.ofMillis(5000)).toList()
            // TODO fix this
            // messages.size shouldEqual 1
            // messages[0].value() shouldEqual message
        }
    }
})
