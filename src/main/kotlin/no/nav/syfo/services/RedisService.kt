package no.nav.syfo.services

import io.opentelemetry.instrumentation.annotations.WithSpan
import java.security.MessageDigest
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.sm2013.KontrollsystemBlokkType
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import no.nav.syfo.util.LoggingMeta
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.params.SetParams

class RedisService(private val jedisPool: JedisPool) {
    @WithSpan
    fun erIRedis(redisKey: String): Boolean {
        var jedis: Jedis? = null
        return try {
            jedis = jedisPool.resource
            when (jedis.get(redisKey)) {
                null -> false
                else -> true
            }
        } catch (e: Exception) {
            log.error("Noe gikk galt ved sjekk mot redis", e.message)
            throw e
        } finally {
            jedis?.close()
        }
    }

    @WithSpan
    fun oppdaterRedis(
        redisKey: String,
        redisValue: String,
        sekunder: Int,
        loggingMeta: LoggingMeta
    ): String? {
        log.info("Prøver å oppdaterer redis {}", fields(loggingMeta))
        var jedis: Jedis? = null
        try {
            jedis = jedisPool.resource
            return jedis.set(
                redisKey,
                redisValue,
                SetParams().apply {
                    ex(sekunder.toLong())
                    nx()
                },
            )
        } catch (e: Exception) {
            log.error("Noe gikk galt ved oppdatering av redis {}", e.message)
            throw e
        } finally {
            jedis?.close()
        }
    }

    fun slettRedisKey(redisKey: String, loggingMeta: LoggingMeta): Long {
        log.info("Prøver å slette redis key for {}", fields(loggingMeta))
        var jedis: Jedis? = null
        try {
            jedis = jedisPool.resource
            return jedis.del(redisKey)
        } catch (e: Exception) {
            log.error("Noe gikk galt ved sletting av key i redis", e.message)
            throw e
        } finally {
            jedis?.close()
        }
    }

    fun oppdaterAntallErrorIInfotrygd(
        redisKey: String,
        redisValue: String,
        sekunder: Int,
        loggingMeta: LoggingMeta
    ) {
        when (erIRedis(redisKey)) {
            false -> oppdaterRedis(redisKey, redisValue, sekunder, loggingMeta)
            true -> {
                var jedis: Jedis? = null
                try {
                    jedis = jedisPool.resource
                    jedis.incr(redisKey)
                } catch (e: Exception) {
                    log.error(
                        "Noe gikk galt ved oppdatering av antall infotrygdfeil i redis",
                        e.message
                    )
                    throw e
                } finally {
                    jedis?.close()
                }
            }
        }
    }

    fun antallErrorIInfotrygd(redisKey: String, loggingMeta: LoggingMeta): Int {
        log.info("Henter ut antall infotrygd error i redis {}", fields(loggingMeta))
        var jedis: Jedis? = null
        try {
            jedis = jedisPool.resource
            return jedis.get(redisKey)?.toInt() ?: 0
        } catch (e: Exception) {
            log.error("Noe gikk galt ved henting av antall infotrygdfeil fra redis", e.message)
            throw e
        } finally {
            jedis?.close()
        }
    }
}

fun sha256hashstring(infotrygdblokk: KontrollsystemBlokkType.InfotrygdBlokk): String =
    MessageDigest.getInstance("SHA-256")
        .digest(objectMapper.writeValueAsBytes(infotrygdblokk))
        .fold("") { str, it -> str + "%02x".format(it) }
