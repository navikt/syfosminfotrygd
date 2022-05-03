package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta

class ManuellClient(
    private val httpClient: HttpClient,
    private val endpointUrl: String,
    private val accessTokenClient: AccessTokenClientV2,
    private val resourceId: String
) {
    suspend fun behandletAvManuell(sykmeldingId: String, loggingMeta: LoggingMeta): Boolean {
        val httpResponse: HttpResponse = httpClient.get("$endpointUrl/api/v1/sykmelding/$sykmeldingId") {
            val accessToken = accessTokenClient.getAccessTokenV2(resourceId)
            headers {
                append("Authorization", "Bearer $accessToken")
            }
        }
        return when (httpResponse.status) {
            HttpStatusCode.OK -> {
                true
            }
            HttpStatusCode.NotFound -> {
                false
            }
            else -> {
                log.error("Noe gikk galt ved sjekk av om sykmeldingid $sykmeldingId har vært til manuell behandling, {}", loggingMeta)
                throw RuntimeException("Noe gikk galt ved sjekk av om sykmeldingid $sykmeldingId har vært til manuell behandling")
            }
        }
    }
}
