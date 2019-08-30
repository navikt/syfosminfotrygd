package no.nav.syfo

data class LoggingMeta(
    val mottakId: String,
    val orgNr: String?,
    val msgId: String,
    val sykmeldingId: String,
    val retry: Boolean
)

class TrackableException(override val cause: Throwable, val loggingMeta: LoggingMeta) : RuntimeException()

suspend fun <O> wrapExceptions(loggingMeta: LoggingMeta, block: suspend () -> O): O {
    try {
        return block()
    } catch (e: Exception) {
        throw TrackableException(e, loggingMeta)
    }
}
