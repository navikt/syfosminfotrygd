package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.features.ClientRequestException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.NAV_OPPFOLGING_UTLAND_KONTOR_NR
import no.nav.syfo.helpers.retry
import no.nav.syfo.log

@KtorExperimentalAPI
class Norg2Client(
    private val httpClient: HttpClient,
    private val endpointUrl: String
) {
    suspend fun getLocalNAVOffice(geografiskOmraade: String?, diskresjonskode: String?): Enhet =
        retry("find_local_nav_office") {
            try {
                return@retry httpClient.get<Enhet>("$endpointUrl/enhet/navkontor/$geografiskOmraade") {
                    accept(ContentType.Application.Json)
                    contentType(ContentType.Application.Json)
                    if (!diskresjonskode.isNullOrEmpty()) {
                        parameter("disk", diskresjonskode)
                    }
                }
            } catch (e: Exception) {
                if (e is ClientRequestException && e.response.status == HttpStatusCode.NotFound) {
                    log.info("Fant ikke lokalt NAV-kontor for geografisk tilhørighet: $geografiskOmraade, setter da NAV-kontor oppfølging utland som lokalt navkontor: $NAV_OPPFOLGING_UTLAND_KONTOR_NR")
                    Enhet(NAV_OPPFOLGING_UTLAND_KONTOR_NR)
                } else {
                    log.error("Noe gikk galt ved henting av lokalkontor fra norg: ${e.message}")
                    throw e
                }
            }
        }
}

data class Enhet(
    val enhetNr: String
)
