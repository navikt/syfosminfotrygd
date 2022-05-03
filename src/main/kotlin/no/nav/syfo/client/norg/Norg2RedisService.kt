package no.nav.syfo.client.norg

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.objectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import java.time.Duration

class Norg2RedisService(
    private val jedisPool: JedisPool,
    private val redisSecret: String
) {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(Norg2RedisService::class.java)
        private val redisTimeoutSeconds: Long = Duration.ofDays(30).toSeconds()
        private const val prefix = "NORG"
    }

    fun putEnhet(geografiskOmraade: String, enhet: Enhet) {
        var jedis: Jedis? = null
        try {
            jedis = jedisPool.resource
            jedis.auth(redisSecret)
            jedis.setex("$prefix$geografiskOmraade", redisTimeoutSeconds, objectMapper.writeValueAsString(enhet))
        } catch (ex: Exception) {
            log.error("Could not update redis for GT {}", ex.message)
        } finally {
            jedis?.close()
        }
    }

    fun getEnhet(geografiskOmraade: String): Enhet? {
        var jedis: Jedis? = null
        return try {
            jedis = jedisPool.resource
            jedis.auth(redisSecret)
            when (val stringValue = jedis.get("$prefix$geografiskOmraade")) {
                null -> null
                else -> objectMapper.readValue<Enhet>(stringValue)
            }
        } catch (ex: Exception) {
            log.error("Could not get redis for GT {}", ex.message)
            null
        } finally {
            jedis?.close()
        }
    }
}
