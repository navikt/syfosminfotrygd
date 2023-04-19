package no.nav.syfo.pdl.model

data class PdlPerson(
    val gt: String?,
    val adressebeskyttelse: String?,
)

fun PdlPerson.getDiskresjonskode(): String? {
    return when (adressebeskyttelse) {
        "STRENGT_FORTROLIG" -> "SPSF"
        "FORTROLIG" -> "SPFO"
        else -> null
    }
}
