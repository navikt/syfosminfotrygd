package no.nav.syfo.tss

data class SmtssResponse(val samhandlerAvd125: SamhandlerAvd125)

data class SamhandlerAvd125(val samhAvd: List<SamhandlerAvd>)

data class SamhandlerAvd(
    val avdNr: String,
    val avdNavn: String,
    val typeAvd: String,
    val beskrTypeAvd: String,
    val datoAvdFom: String,
    val datoAvdTom: String,
    val gyldigAvd: String,
    val idOffTSS: String,
    val offNrAvd: String,
    val kilde: String,
    val brukerId: String,
    val tidReg: String,
)
