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
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.NAV_OPPFOLGING_UTLAND_KONTOR_NR
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta

@KtorExperimentalAPI
class Norg2Client(
    private val httpClient: HttpClient,
    private val endpointUrl: String
) {
    suspend fun getLocalNAVOffice(geografiskOmraade: String?, diskresjonskode: String?, loggingMeta: LoggingMeta): Enhet =
        retry("find_local_nav_office") {
            log.info("Henter lokalkontor for diskresjonskode {} og GT {}, {}", diskresjonskode, geografiskOmraade, StructuredArguments.fields(loggingMeta))
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
                    log.info("Fant ikke lokalt NAV-kontor for geografisk tilhørighet: $geografiskOmraade, setter da NAV-kontor oppfølging utland som lokalt navkontor: $NAV_OPPFOLGING_UTLAND_KONTOR_NR, {}", StructuredArguments.fields(loggingMeta))
                    Enhet(NAV_OPPFOLGING_UTLAND_KONTOR_NR)
                } else {
                    log.error("Noe gikk galt ved henting av lokalkontor fra norg: ${e.message}, {}", StructuredArguments.fields(loggingMeta))
                    throw e
                }
            }
        }
}

data class Enhet(
    val enhetNr: String
)
