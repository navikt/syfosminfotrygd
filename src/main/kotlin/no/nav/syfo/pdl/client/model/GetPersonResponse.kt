package no.nav.syfo.pdl.client.model

data class GetPersonResponse(
    val data: ResponseData,
    val errors: List<ResponseError>?
)

data class ResponseError(
    val message: String?,
    val locations: List<ErrorLocation>?,
    val path: List<String>?,
    val extensions: ErrorExtension?
)

data class ResponseData(
    val hentGeografiskTilknytning: HentGeografiskTilknytning?,
    val hentPerson: HentPerson?
)

data class HentPerson(
    val adressebeskyttelse: List<Adressebeskyttelse>?
)

data class Adressebeskyttelse(
    val gradering: String
)

data class HentGeografiskTilknytning(
    val gtType: String,
    val gtKommune: String?,
    val gtBydel: String?,
    val gtLand: String?
)

data class ErrorLocation(
    val line: String?,
    val column: String?
)

data class ErrorExtension(
    val code: String?,
    val classification: String?
)
