package no.nav.syfo

import java.net.ServerSocket
import java.time.Duration
import java.util.Properties
import no.nav.common.KafkaEnvironment
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object KafkaITSpek : Spek({
    val topic = "aapen-test-topic"
    fun getRandomPort() = ServerSocket(0).use {
        it.localPort
    }

    val embeddedEnvironment = KafkaEnvironment(
            autoStart = false,
            topicNames = listOf(topic)
    )

    val credentials = VaultCredentials("", "", "", "", "", "")

    val config = Environment(mqHostname = "mqhost", mqPort = getRandomPort(),
            mqGatewayName = "mqGateway", kafkaBootstrapServers = embeddedEnvironment.brokersURL,
            mqChannelName = "syfomottak", infotrygdOppdateringQueue = "apprequeue",
            infotrygdSporringQueue = "infotrygdqueue",
            personV3EndpointURL = "personApi", securityTokenServiceUrl = "secApi",
            sm2013AutomaticHandlingTopic = "topic1", smPaperAutomaticHandlingTopic = "topic2", applicationName = "syfosminfotrygd",
            norskHelsenettEndpointURL = "helseAPi", infotrygdSmIkkeOKQueue = "smikkeok", norg2V1EndpointURL = "/enhet/navkontor",
            clientId = "1313", helsenettproxyId = "12313", aadAccessTokenUrl = "acccess"

    )

    fun Properties.overrideForTest(): Properties = apply {
        remove("security.protocol")
        remove("sasl.mechanism")
    }

    val baseConfig = loadBaseConfig(config, credentials).overrideForTest()

    val producerProperties = baseConfig
            .toProducerConfig("spek.integration", valueSerializer = StringSerializer::class)
    val producer = KafkaProducer<String, String>(producerProperties)

    val consumerProperties = baseConfig
            .toConsumerConfig("spek.integration-consumer", valueDeserializer = StringDeserializer::class)
    val consumer = KafkaConsumer<String, String>(consumerProperties)
    consumer.subscribe(listOf(topic))

    beforeGroup {
        embeddedEnvironment.start()
    }

    afterGroup {
        embeddedEnvironment.tearDown()
    }

    describe("Push a message on a topic") {
        val message = "Test message"
        it("Can read the messages from the kafka topic") {
            producer.send(ProducerRecord(topic, message))

            val messages = consumer.poll(Duration.ofMillis(5000)).toList()
            messages.size shouldEqual 1
            messages[0].value() shouldEqual message
        }
    }
})
