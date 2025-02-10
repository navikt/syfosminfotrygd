package no.nav.syfo.services

import io.opentelemetry.instrumentation.annotations.WithSpan
import io.valkey.Jedis
import io.valkey.JedisPool
import io.valkey.params.SetParams
import java.security.MessageDigest
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.sm2013.KontrollsystemBlokkType
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import no.nav.syfo.util.LoggingMeta

class ValkeyService(private val jedisPool: JedisPool) {
    @WithSpan
    fun erIValkey(valkeyKey: String): Boolean {
        var jedis: Jedis? = null
        return try {
            jedis = jedisPool.resource
            when (jedis.get(valkeyKey)) {
                null -> false
                else -> true
            }
        } catch (e: Exception) {
            log.error("Noe gikk galt ved sjekk mot valkey: {}", e.message)
            throw e
        } finally {
            jedis?.close()
        }
    }

    @WithSpan
    fun oppdaterValkey(
        valkeyKey: String,
        valkeyValue: String,
        sekunder: Int,
        loggingMeta: LoggingMeta
    ): String? {
        log.info("Prøver å oppdaterer valkey {}", fields(loggingMeta))
        var jedis: Jedis? = null
        try {
            jedis = jedisPool.resource
            return jedis.set(
                valkeyKey,
                valkeyValue,
                SetParams().apply {
                    ex(sekunder.toLong())
                    nx()
                },
            )
        } catch (e: Exception) {
            log.error("Noe gikk galt ved oppdatering av valkey {}", e.message)
            throw e
        } finally {
            jedis?.close()
        }
    }

    fun slettValkeyKey(valkeyKey: String, loggingMeta: LoggingMeta): Long {
        log.info("Prøver å slette valkey key for {}", fields(loggingMeta))
        var jedis: Jedis? = null
        try {
            jedis = jedisPool.resource
            return jedis.del(valkeyKey)
        } catch (e: Exception) {
            log.error("Noe gikk galt ved sletting av key i valkey: {}", e.message)
            throw e
        } finally {
            jedis?.close()
        }
    }

    fun oppdaterAntallErrorIInfotrygd(
        valkeyKey: String,
        valkeyValue: String,
        sekunder: Int,
        loggingMeta: LoggingMeta
    ) {
        when (erIValkey(valkeyKey)) {
            false -> oppdaterValkey(valkeyKey, valkeyValue, sekunder, loggingMeta)
            true -> {
                var jedis: Jedis? = null
                try {
                    jedis = jedisPool.resource
                    jedis.incr(valkeyKey)
                } catch (e: Exception) {
                    log.error(
                        "Noe gikk galt ved oppdatering av antall infotrygdfeil i valkey: {}",
                        e.message
                    )
                    throw e
                } finally {
                    jedis?.close()
                }
            }
        }
    }

    fun antallErrorIInfotrygd(valkeyKey: String, loggingMeta: LoggingMeta): Int {
        log.info("Henter ut antall infotrygd error i valkey {}", fields(loggingMeta))
        var jedis: Jedis? = null
        try {
            jedis = jedisPool.resource
            return jedis.get(valkeyKey)?.toInt() ?: 0
        } catch (e: Exception) {
            log.error("Noe gikk galt ved henting av antall infotrygdfeil fra valkey: {}", e.message)
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
