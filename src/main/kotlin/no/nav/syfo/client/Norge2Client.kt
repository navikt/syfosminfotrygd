package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.helpers.retry

@KtorExperimentalAPI
class Norge2Client(
    private val endpointUrl: String
) {
    private val client = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
    }

    suspend fun getLocalNAVOffice(geografiskOmraade: String, diskresjonskode: String?): Enhet =
            retry("find_local_nav_office") {
                client.get<Enhet>("$endpointUrl/enhet/navkontor/$geografiskOmraade") {
                    accept(ContentType.Application.Json)
                    if (!diskresjonskode.isNullOrEmpty()) {
                        parameter("disk", diskresjonskode)
                    }
                }
            }
}

data class Enhet(
    val enhetId: String,
    val navn: String,
    val enhetNr: String,
    val antallRessurser: String,
    val status: String,
    val orgNivaa: String,
    val organisasjonsnummer: String,
    val underEtableringDato: String,
    val aktiveringsdato: String,
    val underAvviklingDato: String?,
    val nedleggelsesdato: String?,
    val oppgavebehandler: String?,
    val versjon: String,
    val sosialeTjenester: String,
    val kanalstrategi: String?,
    val orgNrTilKommunaltNavKontor: String?
)
