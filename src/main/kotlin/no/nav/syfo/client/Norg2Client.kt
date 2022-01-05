package no.nav.syfo.client

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.client.HttpClient
import io.ktor.client.features.ClientRequestException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.NAV_OPPFOLGING_UTLAND_KONTOR_NR
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta
import java.time.Duration

class Norg2Client(
    private val httpClient: HttpClient,
    private val endpointUrl: String,
    private val cache: Cache<String, Enhet> = Caffeine
        .newBuilder().expireAfterWrite(Duration.ofDays(30))
        .maximumSize(500)
        .build()
) {
    suspend fun getLocalNAVOffice(geografiskOmraade: String?, diskresjonskode: String?, loggingMeta: LoggingMeta): Enhet {
        if (diskresjonskode == null && geografiskOmraade != null) {
            cache.getIfPresent(geografiskOmraade)?.let {
                log.debug("Traff cache for GT $geografiskOmraade")
                return it
            }
        }
        return retry("find_local_nav_office") {
            try {
                val enhet = httpClient.get<Enhet>("$endpointUrl/enhet/navkontor/$geografiskOmraade") {
                    accept(ContentType.Application.Json)
                    contentType(ContentType.Application.Json)
                    if (!diskresjonskode.isNullOrEmpty()) {
                        parameter("disk", diskresjonskode)
                    }
                }
                if (diskresjonskode == null && geografiskOmraade != null) {
                    cache.put(geografiskOmraade, enhet)
                }
                return@retry enhet
            } catch (e: Exception) {
                if (e is ClientRequestException && e.response.status == HttpStatusCode.NotFound) {
                    log.info(
                        "Fant ikke lokalt NAV-kontor for geografisk tilhørighet: $geografiskOmraade, setter da NAV-kontor oppfølging utland som lokalt navkontor: $NAV_OPPFOLGING_UTLAND_KONTOR_NR, {}",
                        StructuredArguments.fields(loggingMeta)
                    )
                    Enhet(NAV_OPPFOLGING_UTLAND_KONTOR_NR)
                } else {
                    log.error(
                        "Noe gikk galt ved henting av lokalkontor fra norg: ${e.message}, {}",
                        StructuredArguments.fields(loggingMeta)
                    )
                    throw e
                }
            }
        }
    }
}

data class Enhet(
    val enhetNr: String
)
