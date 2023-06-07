package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.NotFound
import no.nav.syfo.log
import java.io.IOException

class NorskHelsenettClient(
    private val httpClient: HttpClient,
    private val endpointUrl: String,
    private val accessTokenClient: AccessTokenClientV2,
    private val resourceId: String,
) {
    suspend fun finnBehandler(behandlerFnr: String, msgId: String): Behandler? {
        log.info("Henter behandler fra syfohelsenettproxy for msgId {}", msgId)
        val httpResponse: HttpResponse = httpClient.get("$endpointUrl/api/v2/behandler") {
            accept(ContentType.Application.Json)
            val accessToken = accessTokenClient.getAccessTokenV2(resourceId)
            headers {
                append("Authorization", "Bearer $accessToken")
                append("Nav-CallId", msgId)
                append("behandlerFnr", behandlerFnr)
            }
        }
        return when (httpResponse.status) {
            HttpStatusCode.OK -> {
                httpResponse.body<Behandler>()
            }

            NotFound -> {
                log.warn("Syfohelsenettproxy svarte med finnBehandler basert på fnr med NotFound msgId {}", msgId)
                null
            }
            else -> {
                log.error("Syfohelsenettproxy svarte med feilmelding for msgId {}: {}", msgId, httpResponse.status)
                throw IOException("Syfohelsenettproxy svarte med feilmelding for $msgId")
            }
        }
    }
}

data class Behandler(
    val godkjenninger: List<Godkjenning>,
)

data class Godkjenning(
    val helsepersonellkategori: Kode? = null,
    val autorisasjon: Kode? = null,
)

data class Kode(
    val aktiv: Boolean,
    val oid: Int,
    val verdi: String?,
)
