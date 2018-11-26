package no.nav.syfo

import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object BootstrapIt : Spek({
    val e = EmbeddedEnvironment()
    beforeGroup { e.start() }
    afterEachTest {
        e.resetMocks()
    }
        afterGroup {
            e.shutdown()
        }

    describe("Full flow exception") {
        it("Ends on backout queue") {
            e.produceKafaMessageOnTopic(e.sm2013AutomaticHandlingTopic, "test")
            val message = e.consumeKafaMessageOnTopic()
            message.size shouldEqual 1
            message[0].value() shouldEqual message
            println("I am here")
        }
    }
})