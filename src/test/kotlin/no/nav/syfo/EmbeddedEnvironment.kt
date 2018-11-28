package no.nav.syfo

import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.prometheus.client.CollectorRegistry
import no.nav.common.KafkaEnvironment
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.tjeneste.virksomhet.organisasjonenhet.v2.binding.OrganisasjonEnhetV2
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.server.ActiveMQServer
import org.apache.activemq.artemis.core.server.ActiveMQServers
import org.apache.cxf.BusFactory
import org.apache.cxf.ext.logging.LoggingFeature
import org.apache.cxf.frontend.ClientProxy
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.apache.cxf.transport.http.HTTPConduit
import org.apache.cxf.transport.servlet.CXFNonSpringServlet
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.mockito.Mockito
import java.net.ServerSocket
import java.time.Duration
import javax.jms.Connection
import javax.jms.ConnectionFactory
import javax.jms.MessageProducer
import javax.jms.Queue
import javax.jms.Session
import javax.naming.InitialContext
import javax.xml.ws.Endpoint

class EmbeddedEnvironment {
    private val applicationState = ApplicationState()
    private val personV3Mock: PersonV3 = Mockito.mock(PersonV3::class.java)
    private val organisasjonEnhetV2Mock: OrganisasjonEnhetV2 = Mockito.mock(OrganisasjonEnhetV2::class.java)

    private val wsMockPort = randomPort()
    private val wsBaseUrl = "http://localhost:$wsMockPort"

    private val activeMQServer: ActiveMQServer = ActiveMQServers.newActiveMQServer(ConfigurationImpl()
            .setPersistenceEnabled(false)
            .setJournalDirectory("target/data/journal")
            .setSecurityEnabled(false)
            .addAcceptorConfiguration("invm", "vm://0"))
    private val connectionFactory: ConnectionFactory
    private val queueConnection: Connection
    private val initialContext: InitialContext
    private val session: Session
    private val server: Server
    val infotrygdOppdateringQueue: Queue
    val infotrygdSporringQueue: Queue
    val infotrygdOppdateringProducer: MessageProducer
    val infotrygdSporringProducer: MessageProducer

    private val embeddedKafkaEnvironment = KafkaEnvironment(
            autoStart = false,
            topics = listOf("privat-syfo-sm2013-automatiskBehandling", "privat-syfo-smpapir-automatiskBehandling")
    )

    private val env = Environment(
            kafkaBootstrapServers = embeddedKafkaEnvironment.brokersURL,
            organisasjonEnhetV2EndpointURL = "$wsBaseUrl/ws/norg2",
            personV3EndpointURL = "$wsBaseUrl/ws/tps",
            securityTokenServiceUrl = "$wsBaseUrl/ws/sts",
            infotrygdSporringQueue = "infotrygd_foresporsel_queue",
            infotrygdOppdateringQueue = "infotrygd_oppdatering_queue",
            mqHostname = "mqgateway",
            mqQueueManagerName = "mqgatewayname",
            mqChannelName = "mqchannel")

    val sm2013AutomaticHandlingTopic = env.sm2013AutomaticHandlingTopic

    private val kafkaproducerInput: KafkaProducer<String, String>
    private val kafkaproducerOutput: KafkaProducer<String, ProduceTask>
    private val kafkaconsumer: KafkaConsumer<String, String>

    private val organisasjonEnhetV2Client: OrganisasjonEnhetV2
    private val personV3: PersonV3

    init {
            activeMQServer.start()
            initialContext = InitialContext()
            connectionFactory = initialContext.lookup("ConnectionFactory") as ConnectionFactory

            server = createJettyServer()

            queueConnection = connectionFactory.createConnection()
            queueConnection.start()

            session = queueConnection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            infotrygdOppdateringQueue = session.createQueue("infotrygd_foresporsel_queue")
            infotrygdSporringQueue = session.createQueue("infotrygd_oppdatering_queue")
            infotrygdOppdateringProducer = session.createProducer(infotrygdOppdateringQueue)
            infotrygdSporringProducer = session.createProducer(infotrygdSporringQueue)

            embeddedKafkaEnvironment.start()
            kafkaconsumer = KafkaConsumer(readConsumerConfig(env, StringDeserializer::class).apply {
                remove("security.protocol")
                remove("sasl.mechanism")
            })
            kafkaconsumer.subscribe(listOf(env.sm2013AutomaticHandlingTopic, env.smPaperAutomaticHandlingTopic))

            kafkaproducerOutput = KafkaProducer(readProducerConfig(env, KafkaAvroSerializer::class).apply {
                remove("security.protocol")
                remove("sasl.mechanism")
            })

            kafkaproducerInput = KafkaProducer(readProducerConfig(env, StringSerializer::class).apply {
                remove("security.protocol")
                remove("sasl.mechanism")
            })

            personV3 = JaxWsProxyFactoryBean().apply {
                address = "$wsBaseUrl/ws/tps"
                features.add(LoggingFeature())
                serviceClass = PersonV3::class.java
            }.create() as PersonV3
            configureTimeout(personV3)

            organisasjonEnhetV2Client = JaxWsProxyFactoryBean().apply {
                address = "$wsBaseUrl/ws/norg2"
                features.add(LoggingFeature())
                serviceClass = OrganisasjonEnhetV2::class.java
            }.create() as OrganisasjonEnhetV2
            configureTimeout(organisasjonEnhetV2Client)
        }

    suspend fun doBlockingApplicationLogic() {
        blockingApplicationLogic(applicationState, kafkaconsumer, kafkaproducerOutput, infotrygdOppdateringProducer, infotrygdSporringProducer, session, personV3, organisasjonEnhetV2Client)
    }

    private fun configureTimeout(service: Any) {
        val client = ClientProxy.getClient(service)
        val conduit = client.conduit as HTTPConduit
        val httpClientPolicy = HTTPClientPolicy()
        httpClientPolicy.connectionTimeout = 1000
        httpClientPolicy.receiveTimeout = 1000
        conduit.client = httpClientPolicy
    }

    fun resetMocks() {
        Mockito.reset(personV3Mock, organisasjonEnhetV2Mock)
    }

    fun shutdown() {
        applicationState.running = false
        activeMQServer.stop(true)
        embeddedKafkaEnvironment.stop()
        kafkaconsumer.close()
        kafkaproducerInput.close()
        kafkaproducerOutput.close()
        server.stop()
        CollectorRegistry.defaultRegistry.clear()
    }

    fun produceKafaMessageOnTopic(topic: String, message: String) {
        kafkaproducerInput.send(ProducerRecord(topic, message))
    }

    fun consumeKafaMessageOnTopic() =
        kafkaconsumer.poll(Duration.ofMillis(10000)).toList()

    private fun randomPort(): Int = ServerSocket(0).use { it.localPort }

    private fun createJettyServer(): Server = Server(wsMockPort).apply {
        val soapServlet = CXFNonSpringServlet()

        val servletHandler = ServletContextHandler()
        servletHandler.addServlet(ServletHolder(soapServlet), "/ws/*")
        handler = servletHandler
        start()

        BusFactory.setDefaultBus(soapServlet.bus)
        Endpoint.publish("/tps", personV3Mock)
        Endpoint.publish("/norg2", organisasjonEnhetV2Mock)
    }
}
