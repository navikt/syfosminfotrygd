package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.features.ResponseException
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.util.LoggingMeta
import java.io.IOException

@KtorExperimentalAPI
class SyfosmreglerClient(private val endpointUrl: String, private val client: HttpClient) {
    suspend fun executeRuleValidation(receivedSykmelding: ReceivedSykmelding, loggingMeta: LoggingMeta): ValidationResult =
        retry("syfosmregler_validate") {
            try {
                return@retry client.post<ValidationResult>("$endpointUrl/v1/rules/validate") {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    body = receivedSykmelding
                }
            } catch (e: Exception) {
                if (e is ResponseException) {
                    log.error("Syfosmregler svarte med feilmelding ${e.response.status} for {}", fields(loggingMeta))
                    throw IOException("Syfosmregler svarte med feilmelding ${e.response.status}")
                } else {
                    log.error("Noe gikk galt ved sjekk mot syfosmregler for {}", fields(loggingMeta))
                    throw e
                }
            }
        }
}
