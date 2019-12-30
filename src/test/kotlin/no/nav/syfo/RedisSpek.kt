package no.nav.syfo

import io.ktor.util.KtorExperimentalAPI
import java.util.concurrent.TimeUnit
import no.nav.syfo.services.INFOTRYGD
import no.nav.syfo.services.antallErrorIInfotrygd
import no.nav.syfo.services.oppdaterAntallErrorIInfotrygd
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import redis.clients.jedis.Jedis
import redis.embedded.RedisServer

@KtorExperimentalAPI
object RedisSpek : Spek({
    describe("Testing the redis functions") {

        it("Should set errorFromInfotrygd count to 2") {
            val redisServer = RedisServer(6379)

            redisServer.start()

            Jedis("localhost", 6379).use { jedis ->
                jedis.connect()

                val loggingMeta = LoggingMeta(
                        mottakId = "1313",
                        orgNr = "0",
                        msgId = "0",
                        sykmeldingId = "0"
                )

                oppdaterAntallErrorIInfotrygd(INFOTRYGD, "1", jedis, TimeUnit.MINUTES.toSeconds(5).toInt(), loggingMeta)
                oppdaterAntallErrorIInfotrygd(INFOTRYGD, "1", jedis, TimeUnit.MINUTES.toSeconds(5).toInt(), loggingMeta)
                antallErrorIInfotrygd(INFOTRYGD, jedis, loggingMeta) shouldEqual 2
            }
            redisServer.stop()
        }

        it("Should set errorFromInfotrygd count to 1") {
            val redisServer = RedisServer(6379)

            redisServer.start()

            Jedis("localhost", 6379).use { jedis ->
                jedis.connect()

                val loggingMeta = LoggingMeta(
                        mottakId = "1313",
                        orgNr = "0",
                        msgId = "0",
                        sykmeldingId = "0"
                )

                oppdaterAntallErrorIInfotrygd(INFOTRYGD, "1", jedis, TimeUnit.MINUTES.toSeconds(5).toInt(), loggingMeta)
                antallErrorIInfotrygd(INFOTRYGD, jedis, loggingMeta) shouldEqual 1
            }
            redisServer.stop()
        }
    }
})