package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
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
    suspend fun executeRuleValidation(receivedSykmelding: ReceivedSykmelding, loggingMeta: LoggingMeta): ValidationResult = retry("syfosmregler_validate") {
        val httpResponse = client.post<HttpStatement>("$endpointUrl/v1/rules/validate") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            body = receivedSykmelding
        }.execute()
        if (httpResponse.status != HttpStatusCode.OK) {
            log.error("Syfosmregler svarte med feilmelding ${httpResponse.status} for {}", fields(loggingMeta))
            throw IOException("Syfosmregler svarte med feilmelding ${httpResponse.status}")
        } else {
            httpResponse.call.response.receive<ValidationResult>()
        }
    }
}
