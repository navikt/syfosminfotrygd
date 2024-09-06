package no.nav.syfo.mq

data class MQEnvironment(
    val NAV_TRUSTSTORE_PASSWORD: String = getEnvVar("NAV_TRUSTSTORE_PASSWORD"),
    val NAV_TRUSTSTORE_PATH: String = getEnvVar("NAV_TRUSTSTORE_PATH")
) {
    companion object {
        fun getEnvVar(varName: String, defaultValue: String? = null) =
            System.getenv(varName)
                ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
    }
}
