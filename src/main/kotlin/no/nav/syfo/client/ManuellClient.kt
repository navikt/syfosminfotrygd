package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.features.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpStatement
import io.ktor.http.HttpStatusCode
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta

class ManuellClient(
    private val httpClient: HttpClient,
    private val endpointUrl: String,
    private val accessTokenClient: AccessTokenClientV2,
    private val resourceId: String
) {
    @KtorExperimentalAPI
    suspend fun behandletAvManuell(sykmeldingId: String, loggingMeta: LoggingMeta): Boolean =
        retry("manuell") {
            try {
                httpClient.get<HttpStatement>("$endpointUrl/api/v1/sykmelding/$sykmeldingId") {
                    val accessToken = accessTokenClient.getAccessTokenV2(resourceId)
                    headers {
                        append("Authorization", "Bearer $accessToken")
                    }
                }.execute()
                return@retry true
            } catch (e: Exception) {
                if (e is ClientRequestException && e.response.status == HttpStatusCode.NotFound) {
                    return@retry false
                } else {
                    log.error("Noe gikk galt ved sjekk av om sykmeldingid $sykmeldingId har vært til manuell behandling, {}", loggingMeta)
                    throw RuntimeException("Noe gikk galt ved sjekk av om sykmeldingid $sykmeldingId har vært til manuell behandling")
                }
            }
        }
}
