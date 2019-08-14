package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.response.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.NAV_OPPFOLGING_UTLAND_KONTOR_NR
import no.nav.syfo.helpers.retry
import no.nav.syfo.log

@KtorExperimentalAPI
class Norg2Client(
    private val endpointUrl: String
) {
    private val client = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        expectSuccess = false
    }

    suspend fun getLocalNAVOffice(geografiskOmraade: String, diskresjonskode: String?): Enhet =
            retry("find_local_nav_office") {
                val httpResponse = client.get<HttpResponse>("$endpointUrl/enhet/navkontor/$geografiskOmraade") {
                    accept(ContentType.Application.Json)
                    contentType(ContentType.Application.Json)
                    if (!diskresjonskode.isNullOrEmpty()) {
                        parameter("disk", diskresjonskode)
                    }
                }
                if (httpResponse.status == NotFound) {
                    log.info("Fant ikke lokalt nav kontor for geografisk tilhørighet: $geografiskOmraade, setter da NAV oppfølging utland som lokalt navkontor: $NAV_OPPFOLGING_UTLAND_KONTOR_NR")
                    Enhet(NAV_OPPFOLGING_UTLAND_KONTOR_NR)
                } else {
                    httpResponse.call.response.receive<Enhet>()
                }
            }
}

data class Enhet(
    val enhetNr: String
)
