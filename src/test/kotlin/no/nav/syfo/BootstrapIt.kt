package no.nav.syfo

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.spekframework.spek2.Spek
import java.util.concurrent.Executors

object BootstrapIt : Spek({ runBlocking(Executors.newFixedThreadPool(4).asCoroutineDispatcher()) {
/*
    val e = EmbeddedEnvironment()
    beforeGroup {
        launch {
            try {
                e.doBlockingApplicationLogic()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    afterEachTest {
        e.resetMocks()
    }
    afterGroup {
        e.shutdown()
    }

    describe("Testing") {
        it("Testing read for kafaka") {
            e.produceKafaMessageOnTopic(e.sm2013AutomaticHandlingTopic, "test")
            val message = e.consumeKafaMessageOnTopic()
            message.size shouldEqual 1
            message[0].value() shouldEqual "test"
            println("I am here")
        }
    }*/
} })