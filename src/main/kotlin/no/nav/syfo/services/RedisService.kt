package no.nav.syfo.services

import java.security.MessageDigest
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.sm2013.KontrollsystemBlokkType
import no.nav.syfo.LoggingMeta
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import redis.clients.jedis.Jedis

fun erIRedis(redisKey: String, jedis: Jedis): Boolean =
        when (jedis.get(redisKey)) {
            null -> false
            else -> true
        }

fun oppdaterRedis(redisKey: String, jedis: Jedis, sekunder: Int, loggingMeta: LoggingMeta) {
            log.info("Oppdaterer redis {}", fields(loggingMeta))
            jedis.setex(redisKey, sekunder, redisKey)
}

fun sha256hashstring(infotrygdblokk: KontrollsystemBlokkType.InfotrygdBlokk): String =
        MessageDigest.getInstance("SHA-256")
                .digest(objectMapper.writeValueAsBytes(infotrygdblokk))
                .fold("") { str, it -> str + "%02x".format(it) }
