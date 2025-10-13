package no.nav.syfo.model

import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.syfo.services.updateinfotrygd.Operasjonstype

data class RuleMetadata(
    val signatureDate: LocalDateTime,
    val receivedDate: LocalDateTime,
    val patientPersonNumber: String,
    val rulesetVersion: String?,
    val legekontorOrgnr: String?,
    val tssid: String?,
    val infotrygdForesp: InfotrygdForesp,
    val operasjonstypeAndFom: Pair<Operasjonstype, LocalDate>,
)
