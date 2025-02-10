package no.nav.syfo.client.norg

import com.fasterxml.jackson.module.kotlin.readValue
import io.valkey.Jedis
import io.valkey.JedisPool
import java.time.Duration
import no.nav.syfo.objectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Norg2ValkeyService(private val jedisPool: JedisPool) {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(Norg2ValkeyService::class.java)
        private val redisTimeoutSeconds: Long = Duration.ofDays(30).toSeconds()
        private const val prefix = "NORG"
    }

    fun putEnhet(geografiskOmraade: String, enhet: Enhet) {
        var jedis: Jedis? = null
        try {
            jedis = jedisPool.resource
            jedis.setex(
                "$prefix$geografiskOmraade",
                redisTimeoutSeconds,
                objectMapper.writeValueAsString(enhet)
            )
        } catch (ex: Exception) {
            log.error("Could not update valkey for GT {}", ex.message)
        } finally {
            jedis?.close()
        }
    }

    fun getEnhet(geografiskOmraade: String): Enhet? {
        var jedis: Jedis? = null
        return try {
            jedis = jedisPool.resource
            when (val stringValue = jedis.get("$prefix$geografiskOmraade")) {
                null -> null
                else -> objectMapper.readValue<Enhet>(stringValue)
            }
        } catch (ex: Exception) {
            log.error("Could not get valkey for GT {}", ex.message)
            null
        } finally {
            jedis?.close()
        }
    }
}
