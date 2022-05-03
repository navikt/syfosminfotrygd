package no.nav.syfo

import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.services.INFOTRYGD
import no.nav.syfo.services.antallErrorIInfotrygd
import no.nav.syfo.services.oppdaterAntallErrorIInfotrygd
import no.nav.syfo.services.oppdaterRedis
import no.nav.syfo.services.slettRedisKey
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo
import redis.clients.jedis.Jedis
import redis.embedded.RedisServer
import java.util.concurrent.TimeUnit

class RedisSpek : FunSpec({
    context("Testing the redis functions") {

        test("Should set errorFromInfotrygd count to 2") {
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
                antallErrorIInfotrygd(INFOTRYGD, jedis, loggingMeta) shouldBeEqualTo 2
            }
            redisServer.stop()
        }

        test("Should set errorFromInfotrygd count to 1") {
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
                antallErrorIInfotrygd(INFOTRYGD, jedis, loggingMeta) shouldBeEqualTo 1
            }
            redisServer.stop()
        }
    }

    context("Testing the redis duplicate") {

        test("Should return OK") {
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

                val oppdaterRedis = oppdaterRedis(INFOTRYGD, "1", jedis, TimeUnit.MINUTES.toSeconds(5).toInt(), loggingMeta)
                oppdaterRedis shouldBeEqualTo "OK"
            }
            redisServer.stop()
        }

        test("Should return OK") {
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

                oppdaterRedis(INFOTRYGD, "1", jedis, TimeUnit.MINUTES.toSeconds(5).toInt(), loggingMeta)
                val oppdaterRedisFAIL = oppdaterRedis(INFOTRYGD, "1", jedis, TimeUnit.MINUTES.toSeconds(5).toInt(), loggingMeta)
                oppdaterRedisFAIL shouldBeEqualTo null
            }
            redisServer.stop()
        }

        test("Should return set redis expire") {
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

                oppdaterRedis(INFOTRYGD, "1", jedis, TimeUnit.MINUTES.toSeconds(5).toInt(), loggingMeta)
                val oppdaterRedisFAIL = oppdaterRedis(INFOTRYGD, "1", jedis, TimeUnit.MINUTES.toSeconds(5).toInt(), loggingMeta)
                oppdaterRedisFAIL shouldBeEqualTo null
            }
            redisServer.stop()
        }

        test("Should delete 1 key") {
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

                oppdaterRedis(INFOTRYGD, "1", jedis, TimeUnit.MINUTES.toSeconds(10).toInt(), loggingMeta)
                val antallSlette = slettRedisKey(INFOTRYGD, jedis, loggingMeta)
                antallSlette shouldBeEqualTo 1L
            }
            redisServer.stop()
        }
    }
})
