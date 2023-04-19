package no.nav.syfo

import io.kotest.core.spec.style.FunSpec
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.StatusType
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.syfo.util.infotrygdSporringMarshaller
import no.nav.syfo.util.infotrygdSporringUnmarshaller
import org.amshove.kluent.shouldBeEqualTo
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.server.ActiveMQServers
import java.io.StringReader
import java.time.LocalDate
import javax.jms.ConnectionFactory
import javax.jms.Session
import javax.jms.TextMessage
import javax.naming.InitialContext

class MqSpek : FunSpec({

    val activeMQServer = ActiveMQServers.newActiveMQServer(
        ConfigurationImpl()
            .setPersistenceEnabled(false)
            .setJournalDirectory("target/data/journal")
            .setSecurityEnabled(false)
            .addAcceptorConfiguration("invm", "vm://0"),
    )

    beforeSpec {
        activeMQServer.start()
    }

    afterSpec {
        activeMQServer.stop()
    }

    context("Push a message on a queue") {
        test("Can read the messages from the tmp mq queue") {
            val initialContext = InitialContext()
            val connectionFactory = initialContext.lookup("ConnectionFactory") as ConnectionFactory
            connectionFactory.createConnection().use { connection ->
                connection.start()

                val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
                val infotrygdSporringQueue = session.createQueue("it_sporring")

                val producer = session.createProducer(infotrygdSporringQueue)
                val consumer = session.createConsumer(infotrygdSporringQueue)

                val infotrygdForesp = InfotrygdForesp().apply {
                    sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                        sykmelding.add(
                            TypeSMinfo().apply {
                                periode = TypeSMinfo.Periode().apply {
                                    arbufoerFOM = LocalDate.now()
                                    arbufoerTOM = LocalDate.now().plusDays(2)
                                }
                            },
                        )
                        status = StatusType().apply {
                            kodeMelding = "04"
                        }
                    }
                }

                producer.send(
                    session.createTextMessage().apply {
                        text = infotrygdSporringMarshaller.toString(infotrygdForesp)
                        log.info("Sending: {}", StructuredArguments.keyValue("message", text))
                        log.info("Pushed message to queue")
                    },
                )
                val message = consumer.receiveNoWait()

                val inputMessageText = when (message) {
                    is TextMessage -> message.text
                    else -> throw RuntimeException("Incoming message needs to be a byte message or text message")
                }

                val infotrygdForespResponse = infotrygdSporringUnmarshaller.unmarshal(StringReader(inputMessageText)) as InfotrygdForesp
                infotrygdForespResponse.fodselsnummer shouldBeEqualTo null
            }
        }
    }
})
