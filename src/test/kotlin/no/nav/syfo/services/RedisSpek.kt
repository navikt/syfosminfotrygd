package no.nav.syfo.services

import io.kotest.core.spec.style.FunSpec
import java.util.concurrent.TimeUnit
import no.nav.syfo.services.updateinfotrygd.INFOTRYGD
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo
import org.testcontainers.containers.GenericContainer
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

class RedisSpek :
    FunSpec({
        val loggingMeta =
            LoggingMeta(
                mottakId = "1313",
                orgNr = "0",
                msgId = "0",
                sykmeldingId = "0",
            )

        val redisContainer: GenericContainer<Nothing> = GenericContainer("redis:7.0.12-alpine")
        redisContainer.withExposedPorts(6379)

        redisContainer.start()
        val jedisPool =
            JedisPool(JedisPoolConfig(), redisContainer.host, redisContainer.getMappedPort(6379))
        val redisService = RedisService(jedisPool)

        beforeTest {
            val jedis = jedisPool.resource
            jedis.flushAll()
        }

        afterSpec { redisContainer.stop() }

        context("Testing the redis functions") {
            test("Should set errorFromInfotrygd count to 2") {
                redisService.oppdaterAntallErrorIInfotrygd(
                    INFOTRYGD,
                    "1",
                    TimeUnit.MINUTES.toSeconds(5).toInt(),
                    loggingMeta
                )
                redisService.oppdaterAntallErrorIInfotrygd(
                    INFOTRYGD,
                    "1",
                    TimeUnit.MINUTES.toSeconds(5).toInt(),
                    loggingMeta
                )

                redisService.antallErrorIInfotrygd(INFOTRYGD, loggingMeta) shouldBeEqualTo 2
            }

            test("Should set errorFromInfotrygd count to 1") {
                redisService.oppdaterAntallErrorIInfotrygd(
                    INFOTRYGD,
                    "1",
                    TimeUnit.MINUTES.toSeconds(5).toInt(),
                    loggingMeta
                )

                redisService.antallErrorIInfotrygd(INFOTRYGD, loggingMeta) shouldBeEqualTo 1
            }

            test("Oppdatering returnerer OK når key ikke finnes") {
                val oppdaterRedis =
                    redisService.oppdaterRedis(
                        INFOTRYGD,
                        "1",
                        TimeUnit.MINUTES.toSeconds(5).toInt(),
                        loggingMeta
                    )

                oppdaterRedis shouldBeEqualTo "OK"
            }

            test("Oppdatering returnerer null når key finnes fra før") {
                redisService.oppdaterRedis(
                    INFOTRYGD,
                    "1",
                    TimeUnit.MINUTES.toSeconds(5).toInt(),
                    loggingMeta
                )
                val oppdaterRedisFAIL =
                    redisService.oppdaterRedis(
                        INFOTRYGD,
                        "1",
                        TimeUnit.MINUTES.toSeconds(5).toInt(),
                        loggingMeta
                    )

                oppdaterRedisFAIL shouldBeEqualTo null
            }

            test("Should delete 1 key") {
                redisService.oppdaterRedis(
                    INFOTRYGD,
                    "1",
                    TimeUnit.MINUTES.toSeconds(10).toInt(),
                    loggingMeta
                )
                val antallSlettede = redisService.slettRedisKey(INFOTRYGD, loggingMeta)
                antallSlettede shouldBeEqualTo 1L
            }
        }
    })
