package no.nav.syfo.tss

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.util.LoggingMeta
import org.slf4j.LoggerFactory

class SmtssClient(
    private val endpointUrl: String,
    private val accessTokenClientV2: AccessTokenClientV2,
    private val scope: String,
    private val httpClient: HttpClient,
) {

    companion object {
        val logger = LoggerFactory.getLogger(SmtssClient::class.java)
    }

    suspend fun getTssId(
        samhandlerFnr: String,
        samhandlerOrgnr: String,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
    ): String? {
        val accessToken = accessTokenClientV2.getAccessTokenV2(scope)
        val httpResponse =
            httpClient.get("$endpointUrl/api/v1/samhandler/all") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                header("Authorization", "Bearer $accessToken")
                header("requestId", sykmeldingId)
                header("samhandlerFnr", samhandlerFnr)
            }
        return getTssId(httpResponse, samhandlerOrgnr, loggingMeta)
    }

    private suspend fun getTssId(
        httpResponse: HttpResponse,
        samhandlerOrgnr: String,
        loggingMeta: LoggingMeta,
    ): String? {
        return when (httpResponse.status) {
            HttpStatusCode.OK -> {
                val response =
                    httpResponse.body<List<SmtssResponse>>().flatMap {
                        it.samhandlerAvd125?.samhAvd ?: emptyList()
                    }
                response.firstOrNull { it.offNrAvd == samhandlerOrgnr }?.idOffTSS
                    ?: response.firstOrNull { it.gyldigAvd == "J" }?.idOffTSS
            }
            HttpStatusCode.NotFound -> {
                logger.info(
                    "smtss responded with {} for {}",
                    httpResponse.status,
                    StructuredArguments.fields(loggingMeta),
                )
                null
            }
            else -> {
                logger.error(
                    "Error getting TSS-id ${httpResponse.status} : ${httpResponse.bodyAsText()}"
                )
                throw RuntimeException("Error getting TSS-id")
            }
        }
    }
}

data class TSSident(
    val tssid: String,
)
