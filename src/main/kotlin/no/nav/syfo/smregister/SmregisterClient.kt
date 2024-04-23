package no.nav.syfo.smregister

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import java.time.LocalDate
import java.time.OffsetDateTime
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.model.sykmelding.Periode

class SmregisterClient(
    private val smregisterEndpointURL: String,
    private val accessTokenClientV2: AccessTokenClientV2,
    private val scope: String,
    private val httpClient: HttpClient,
) {
    suspend fun getSykmeldinger(fnr: String, fom: LocalDate, tom: LocalDate): List<SykmeldingDTO> =
        httpClient
            .get("$smregisterEndpointURL/api/v2/sykmelding/sykmeldinger") {
                accept(ContentType.Application.Json)
                val accessToken = accessTokenClientV2.getAccessTokenV2(scope)
                headers {
                    append("Authorization", "Bearer $accessToken")
                    append("fnr", fnr)
                }
                parameter("fom", fom)
                parameter("tom", tom)
            }
            .body()
}

data class SykmeldingDTO(
    val id: String,
    val behandlingsutfall: BehandlingsutfallDTO,
    val sykmeldingsperioder: List<SykmeldingsperiodeDTO>,
    val behandletTidspunkt: OffsetDateTime,
    val medisinskVurdering: MedisinskVurderingDTO?,
    val merknader: List<Merknad>?,
)

data class Merknad(
    val type: String,
    val beskrivelse: String?,
)

enum class MerknadType {
    UGYLDIG_TILBAKEDATERING,
    TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER,
    UNDER_BEHANDLING,
    DELVIS_GODKJENT,
    ;

    companion object {
        fun contains(type: String): Boolean {
            return values().any { it.name == type }
        }
    }
}

data class SykmeldingsperiodeDTO(
    val fom: LocalDate,
    val tom: LocalDate,
    val gradert: GradertDTO?,
    val type: PeriodetypeDTO,
)

data class MedisinskVurderingDTO(
    val hovedDiagnose: DiagnoseDTO?,
)

data class DiagnoseDTO(
    val kode: String,
)

data class GradertDTO(
    val grad: Int,
    val reisetilskudd: Boolean,
)

enum class PeriodetypeDTO {
    AKTIVITET_IKKE_MULIG,
    AVVENTENDE,
    BEHANDLINGSDAGER,
    GRADERT,
    REISETILSKUDD,
}

data class BehandlingsutfallDTO(
    val status: RegelStatusDTO,
)

enum class RegelStatusDTO {
    OK,
    MANUAL_PROCESSING,
    INVALID
}

fun List<SykmeldingsperiodeDTO>.sortedFOMDate(): List<LocalDate> = map { it.fom }.sorted()

fun List<SykmeldingsperiodeDTO>.sortedTOMDate(): List<LocalDate> = map { it.tom }.sorted()

fun SykmeldingsperiodeDTO.periodelisteInneholderSammeType(perioder: List<Periode>): Boolean {
    val periodetyper = perioder.mapNotNull { it.tilPeriodetypeDTO() }.distinct()

    return if (periodetyper.contains(type) && type != PeriodetypeDTO.GRADERT) {
        true
    } else if (periodetyper.contains(type) && type == PeriodetypeDTO.GRADERT) {
        val sykmeldingsgrader = perioder.mapNotNull { it.gradert?.grad }
        sykmeldingsgrader.contains(gradert?.grad)
    } else {
        false
    }
}

fun Periode.tilPeriodetypeDTO(): PeriodetypeDTO? {
    return when {
        aktivitetIkkeMulig != null -> return PeriodetypeDTO.AKTIVITET_IKKE_MULIG
        gradert != null -> return PeriodetypeDTO.GRADERT
        reisetilskudd -> return PeriodetypeDTO.REISETILSKUDD
        avventendeInnspillTilArbeidsgiver != null -> return PeriodetypeDTO.AVVENTENDE
        behandlingsdager != null && behandlingsdager!! > 0 -> return PeriodetypeDTO.BEHANDLINGSDAGER
        else -> null
    }
}
