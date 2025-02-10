package no.nav.syfo.client.norg

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.NAV_OPPFOLGING_UTLAND_KONTOR_NR
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta

class Norg2Client(
    private val httpClient: HttpClient,
    private val endpointUrl: String,
    private val norg2ValkeyService: Norg2ValkeyService,
) {
    suspend fun getLocalNAVOffice(
        geografiskOmraade: String?,
        diskresjonskode: String?,
        loggingMeta: LoggingMeta
    ): Enhet {
        if (diskresjonskode == null && geografiskOmraade != null) {
            norg2ValkeyService.getEnhet(geografiskOmraade)?.let {
                log.debug("Traff cache for GT $geografiskOmraade")
                return it
            }
        }
        val httpResponse =
            httpClient.get("$endpointUrl/enhet/navkontor/$geografiskOmraade") {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                if (!diskresjonskode.isNullOrEmpty()) {
                    parameter("disk", diskresjonskode)
                }
            }
        when (httpResponse.status) {
            HttpStatusCode.OK -> {
                val enhet = httpResponse.body<Enhet>()
                if (diskresjonskode == null && geografiskOmraade != null) {
                    norg2ValkeyService.putEnhet(geografiskOmraade, enhet)
                }
                return enhet
            }
            HttpStatusCode.NotFound -> {
                log.info(
                    "Fant ikke lokalt NAV-kontor for geografisk tilhørighet: $geografiskOmraade, setter da NAV-kontor oppfølging utland som lokalt navkontor: $NAV_OPPFOLGING_UTLAND_KONTOR_NR, {}",
                    StructuredArguments.fields(loggingMeta),
                )
                return Enhet(NAV_OPPFOLGING_UTLAND_KONTOR_NR)
            }
            else -> {
                log.error(
                    "Noe gikk galt ved henting av lokalkontor fra norg: ${httpResponse.status}, ${httpResponse.body<String>()}, {}",
                    StructuredArguments.fields(loggingMeta),
                )
                throw RuntimeException(
                    "Noe gikk galt ved henting av lokalkontor fra norg: ${httpResponse.status}, ${httpResponse.body<String>()}"
                )
            }
        }
    }
}

data class Enhet(
    val enhetNr: String,
)
