package no.nav.syfo.model

import java.time.LocalDateTime
import no.nav.helse.infotrygd.foresp.InfotrygdForesp

data class RuleMetadata(
    val signatureDate: LocalDateTime,
    val receivedDate: LocalDateTime,
    val patientPersonNumber: String,
    val rulesetVersion: String?,
    val legekontorOrgnr: String?,
    val tssid: String?,
    val infotrygdForesp: InfotrygdForesp,
)
