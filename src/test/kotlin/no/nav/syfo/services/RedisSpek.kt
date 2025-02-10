package no.nav.syfo.services

import io.kotest.core.spec.style.FunSpec
import io.valkey.JedisPool
import io.valkey.JedisPoolConfig
import java.util.concurrent.TimeUnit
import no.nav.syfo.services.updateinfotrygd.INFOTRYGD
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo
import org.testcontainers.containers.GenericContainer

class RedisSpek :
    FunSpec({
        val loggingMeta =
            LoggingMeta(
                mottakId = "1313",
                orgNr = "0",
                msgId = "0",
                sykmeldingId = "0",
            )

        val valkeyContainer: GenericContainer<Nothing> =
            GenericContainer("valkey/valkey:8.0.2-alpine")
        valkeyContainer.withExposedPorts(6379)

        valkeyContainer.start()
        val jedisPool =
            JedisPool(JedisPoolConfig(), valkeyContainer.host, valkeyContainer.getMappedPort(6379))
        val valkeyService = ValkeyService(jedisPool)

        beforeTest {
            val jedis = jedisPool.resource
            jedis.flushAll()
        }

        afterSpec { valkeyContainer.stop() }

        context("Testing the redis functions") {
            test("Should set errorFromInfotrygd count to 2") {
                valkeyService.oppdaterAntallErrorIInfotrygd(
                    INFOTRYGD,
                    "1",
                    TimeUnit.MINUTES.toSeconds(5).toInt(),
                    loggingMeta
                )
                valkeyService.oppdaterAntallErrorIInfotrygd(
                    INFOTRYGD,
                    "1",
                    TimeUnit.MINUTES.toSeconds(5).toInt(),
                    loggingMeta
                )

                valkeyService.antallErrorIInfotrygd(INFOTRYGD, loggingMeta) shouldBeEqualTo 2
            }

            test("Should set errorFromInfotrygd count to 1") {
                valkeyService.oppdaterAntallErrorIInfotrygd(
                    INFOTRYGD,
                    "1",
                    TimeUnit.MINUTES.toSeconds(5).toInt(),
                    loggingMeta
                )

                valkeyService.antallErrorIInfotrygd(INFOTRYGD, loggingMeta) shouldBeEqualTo 1
            }

            test("Oppdatering returnerer OK når key ikke finnes") {
                val oppdaterRedis =
                    valkeyService.oppdaterValkey(
                        INFOTRYGD,
                        "1",
                        TimeUnit.MINUTES.toSeconds(5).toInt(),
                        loggingMeta
                    )

                oppdaterRedis shouldBeEqualTo "OK"
            }

            test("Oppdatering returnerer null når key finnes fra før") {
                valkeyService.oppdaterValkey(
                    INFOTRYGD,
                    "1",
                    TimeUnit.MINUTES.toSeconds(5).toInt(),
                    loggingMeta
                )
                val oppdaterRedisFAIL =
                    valkeyService.oppdaterValkey(
                        INFOTRYGD,
                        "1",
                        TimeUnit.MINUTES.toSeconds(5).toInt(),
                        loggingMeta
                    )

                oppdaterRedisFAIL shouldBeEqualTo null
            }

            test("Should delete 1 key") {
                valkeyService.oppdaterValkey(
                    INFOTRYGD,
                    "1",
                    TimeUnit.MINUTES.toSeconds(10).toInt(),
                    loggingMeta
                )
                val antallSlettede = valkeyService.slettRedisKey(INFOTRYGD, loggingMeta)
                antallSlettede shouldBeEqualTo 1L
            }
        }
    })
