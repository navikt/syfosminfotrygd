package no.nav.syfo.client.norg

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.objectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import java.time.Duration

class Norg2RedisService(
    private val jedis: Jedis
) {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(Norg2RedisService::class.java)
        private val redisTimeoutSeconds: Long = Duration.ofDays(30).toSeconds()
        private const val prefix = "NORG"
    }

    fun putEnhet(geografiskOmraade: String, enhet: Enhet) {
        try {
            jedis.setex("$prefix$geografiskOmraade", redisTimeoutSeconds, objectMapper.writeValueAsString(enhet))
        } catch (ex: Exception) {
            log.error("Could not update redis for GT {}", ex.message)
        } finally {
            jedis.close()
        }
    }

    fun getEnhet(geografiskOmraade: String): Enhet? {
        return try {
            when (val stringValue = jedis.get("$prefix$geografiskOmraade")) {
                null -> null
                else -> objectMapper.readValue<Enhet>(stringValue)
            }
        } catch (ex: Exception) {
            log.error("Could not get redis for GT {}", ex.message)
            null
        } finally {
            jedis.close()
        }
    }
}
