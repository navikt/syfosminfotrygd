package no.nav.syfo.util

import io.valkey.DefaultJedisClientConfig
import io.valkey.HostAndPort
import io.valkey.JedisPool
import io.valkey.JedisPoolConfig
import no.nav.syfo.getEnvVar

class ValkeyConfig(
    val username: String = getEnvVar("VALKEY_USERNAME_INFOTRYGD"),
    val password: String = getEnvVar("VALKEY_PASSWORD_INFOTRYGD"),
    val host: String = getEnvVar("VALKEY_HOST_SMTSS"),
    val port: Int = getEnvVar("VALKEY_PORT_SMTSS").toInt(),
    val ssl: Boolean = true
)

fun createJedisPool(valkeyConfig: ValkeyConfig = ValkeyConfig()): JedisPool {
    return JedisPool(
        JedisPoolConfig(),
        HostAndPort(valkeyConfig.host, valkeyConfig.port),
        DefaultJedisClientConfig.builder()
            .ssl(valkeyConfig.ssl)
            .user(valkeyConfig.username)
            .password(valkeyConfig.password)
            .build()
    )
}
