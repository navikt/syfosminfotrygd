package no.nav.syfo.mq

import java.util.Properties

class MqTlsUtils {
    companion object {
        fun getMqTlsConfig(): Properties {
            return Properties().also {
                val mqEnv = MQEnvironment()
                it["javax.net.ssl.keyStore"] = mqEnv.NAV_TRUSTSTORE_PATH + "/ca-bundle.jks"
                it["javax.net.ssl.keyStorePassword"] = mqEnv.NAV_TRUSTSTORE_PASSWORD
                it["javax.net.ssl.keyStoreType"] = "jks"
            }
        }
    }
}
