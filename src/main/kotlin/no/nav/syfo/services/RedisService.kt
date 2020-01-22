package no.nav.syfo.services

import java.security.MessageDigest
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.sm2013.KontrollsystemBlokkType
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import no.nav.syfo.util.LoggingMeta
import redis.clients.jedis.Jedis

fun erIRedis(redisKey: String, jedis: Jedis): Boolean =
        when (jedis.get(redisKey)) {
            null -> false
            else -> true
        }

fun oppdaterRedis(redisKey: String, redisValue: String, jedis: Jedis, sekunder: Int, loggingMeta: LoggingMeta): String? {
    log.info("Oppdaterer redis {}", fields(loggingMeta))
    return jedis.set(redisKey, redisValue, "nx", "ex", sekunder.toLong())
}

fun oppdaterAntallErrorIInfotrygd(redisKey: String, redisValue: String, jedis: Jedis, sekunder: Int, loggingMeta: LoggingMeta) {
    when (jedis.get(redisKey)) {
        null -> oppdaterRedis(redisKey, redisValue, jedis, sekunder, loggingMeta)
        else -> jedis.incr(redisKey)
    }
}

fun antallErrorIInfotrygd(redisKey: String, jedis: Jedis, loggingMeta: LoggingMeta): Int {
    log.info("Henter ut antall infotrygd error i redis {}", fields(loggingMeta))
    return jedis.get(redisKey)?.toInt() ?: 0
}

fun sha256hashstring(infotrygdblokk: KontrollsystemBlokkType.InfotrygdBlokk): String =
        MessageDigest.getInstance("SHA-256")
                .digest(objectMapper.writeValueAsBytes(infotrygdblokk))
                .fold("") { str, it -> str + "%02x".format(it) }
