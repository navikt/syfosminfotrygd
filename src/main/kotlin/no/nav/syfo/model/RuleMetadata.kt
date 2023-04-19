package no.nav.syfo.model

import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import java.time.LocalDateTime

data class RuleMetadata(
    val signatureDate: LocalDateTime,
    val receivedDate: LocalDateTime,
    val patientPersonNumber: String,
    val rulesetVersion: String?,
    val legekontorOrgnr: String?,
    val tssid: String?,
    val infotrygdForesp: InfotrygdForesp,
)
