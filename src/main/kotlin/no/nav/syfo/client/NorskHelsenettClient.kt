package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.features.ClientRequestException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import java.io.IOException

@KtorExperimentalAPI
class NorskHelsenettClient(
    private val httpClient: HttpClient,
    private val endpointUrl: String,
    private val accessTokenClient: AccessTokenClientV2,
    private val resourceId: String
) {
    suspend fun finnBehandler(behandlerFnr: String, msgId: String): Behandler? = retry(
        callName = "finnbehandler",
        retryIntervals = arrayOf(500L, 1000L, 3000L)
    ) {
        log.info("Henter behandler fra syfohelsenettproxy for msgId {}", msgId)
        try {
            return@retry httpClient.get<Behandler>("$endpointUrl/api/v2/behandler") {
                accept(ContentType.Application.Json)
                val accessToken = accessTokenClient.getAccessTokenV2(resourceId)
                headers {
                    append("Authorization", "Bearer $accessToken")
                    append("Nav-CallId", msgId)
                    append("behandlerFnr", behandlerFnr)
                }
            }
        } catch (e: Exception) {
            if (e is ClientRequestException && e.response.status == NotFound) {
                log.error("BehandlerFnr mangler i request for msgId {}", msgId)
                null
            } else {
                log.error("Syfohelsenettproxy svarte med feilmelding for msgId {}: {}", msgId, e.message)
                throw IOException("Syfohelsenettproxy svarte med feilmelding for $msgId")
            }
        }
    }
}

data class Behandler(
    val godkjenninger: List<Godkjenning>
)

data class Godkjenning(
    val helsepersonellkategori: Kode? = null,
    val autorisasjon: Kode? = null
)

data class Kode(
    val aktiv: Boolean,
    val oid: Int,
    val verdi: String?
)
