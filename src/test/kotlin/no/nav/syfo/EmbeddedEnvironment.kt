package no.nav.syfo

import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.prometheus.client.CollectorRegistry
import kotlinx.coroutines.runBlocking
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
import org.apache.kafka.common.serialization.StringSerializer
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.mockito.Mockito
import java.net.ServerSocket
import java.time.Duration
import javax.jms.Connection
import javax.jms.ConnectionFactory
import javax.jms.Session
import javax.naming.InitialContext
import javax.xml.ws.Endpoint

class EmbeddedEnvironment {
    private val env = Environment()
    private val applicationState = ApplicationState()
    private val personV3Mock: PersonV3 = Mockito.mock(PersonV3::class.java)
    private val organisasjonEnhetV2Mock: OrganisasjonEnhetV2 = Mockito.mock(OrganisasjonEnhetV2::class.java)

    val sm2013AutomaticHandlingTopic = env.sm2013AutomaticHandlingTopic

    private val wsMockPort = randomPort()
    private val wsBaseUrl = "http://localhost:$wsMockPort"

    private lateinit var activeMQServer: ActiveMQServer
    private lateinit var connectionFactory: ConnectionFactory
    private lateinit var queueConnection: Connection
    private lateinit var initialContext: InitialContext

    private lateinit var session: Session

    private lateinit var server: Server

    private val embeddedKafkaEnvironment = KafkaEnvironment(
            autoStart = false,
            topics = listOf(env.sm2013AutomaticHandlingTopic, env.smPaperAutomaticHandlingTopic)
    )

    private lateinit var kafkaproducerInput: KafkaProducer<String, String>

    private lateinit var kafkaconsumer: KafkaConsumer<String, String>

        fun start() = runBlocking {

            activeMQServer = ActiveMQServers.newActiveMQServer(ConfigurationImpl()
                    .setPersistenceEnabled(false)
                    .setJournalDirectory("target/data/journal")
                    .setSecurityEnabled(false)
                    .addAcceptorConfiguration("invm", "vm://0"))
            activeMQServer.start()
            // val properties = Properties()
            // properties.setProperty(Context.INITIAL_CONTEXT_FACTORY,"org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory")
            initialContext = InitialContext()
            connectionFactory = initialContext.lookup("ConnectionFactory") as ConnectionFactory

            server = createJettyServer()

            queueConnection = connectionFactory.createConnection()
            queueConnection.start()

            session = queueConnection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            val infotrygdOppdateringQueue = session.createQueue("queue:///${env.infotrygdOppdateringQueue}?targetClient=1")
            val infotrygdSporringQueue = session.createQueue("queue:///${env.infotrygdSporringQueue}?targetClient=1")
            val infotrygdOppdateringProducer = session.createProducer(infotrygdOppdateringQueue)
            val infotrygdSporringProducer = session.createProducer(infotrygdSporringQueue)

            embeddedKafkaEnvironment.start()
            kafkaconsumer = KafkaConsumer(readProducerConfig(env, StringSerializer::class).apply {
                remove("security.protocol")
                remove("sasl.mechanism")
            })
            kafkaconsumer.subscribe(listOf(env.sm2013AutomaticHandlingTopic, env.smPaperAutomaticHandlingTopic))

            val kafkaproducerOutput = KafkaProducer<String, ProduceTask>(readProducerConfig(env, KafkaAvroSerializer::class).apply {
                remove("security.protocol")
                remove("sasl.mechanism")
            })

            kafkaproducerInput = KafkaProducer(readProducerConfig(env, StringSerializer::class).apply {
                remove("security.protocol")
                remove("sasl.mechanism")
            })

            val personV3 = JaxWsProxyFactoryBean().apply {
                address = "$wsBaseUrl/ws/tps"
                features.add(LoggingFeature())
                serviceClass = PersonV3::class.java
            }.create() as PersonV3
            configureTimeout(personV3)

            val organisasjonEnhetV2Client = JaxWsProxyFactoryBean().apply {
                address = "$wsBaseUrl/ws/norg2"
                features.add(LoggingFeature())
                serviceClass = OrganisasjonEnhetV2::class.java
            }.create() as OrganisasjonEnhetV2
            configureTimeout(organisasjonEnhetV2Client)

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
        activeMQServer.stop(true)
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
