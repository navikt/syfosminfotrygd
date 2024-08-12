package no.nav.syfo.helpers

import java.io.IOException
import kotlin.reflect.KClass
import kotlinx.coroutines.delay
import net.logstash.logback.argument.StructuredArguments.keyValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.network-helpers")

suspend inline fun <reified T> retry(
    callName: String,
    vararg legalExceptions: KClass<out Throwable> = arrayOf(IOException::class),
    retryIntervals: Array<Long> = arrayOf(500, 1000, 3000, 5000, 10000),
    exceptionCausedByDepth: Int = 3,
    crossinline block: suspend () -> T
): T {
    for (interval in retryIntervals) {
        try {
            return block()
        } catch (e: Throwable) {
            if (!isCausedBy(e, exceptionCausedByDepth, legalExceptions)) {
                throw e
            }
            log.warn(
                "Failed to execute {}, retrying in $interval ms",
                keyValue("callName", callName),
                e
            )
        }
        delay(interval)
    }
    return block()
}

fun isCausedBy(
    throwable: Throwable,
    depth: Int,
    legalExceptions: Array<out KClass<out Throwable>>
): Boolean {
    var current: Throwable = throwable
    for (i in 0.until(depth)) {
        if (legalExceptions.any { it.isInstance(current) }) {
            return true
        }
        current = current.cause ?: break
    }
    return false
}
