package no.nav.syfo.pdl.service

import java.time.LocalDateTime
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.log
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.HentGeografiskTilknytning
import no.nav.syfo.pdl.client.model.Kontaktadresse
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.util.LoggingMeta

class PdlPersonService(
    private val pdlClient: PdlClient,
    private val accessTokenClientV2: AccessTokenClientV2,
    private val pdlScope: String,
) {
    suspend fun getPerson(fnr: String, loggingMeta: LoggingMeta): PdlPerson {
        val accessToken = accessTokenClientV2.getAccessTokenV2(pdlScope)
        val pdlResponse = pdlClient.getPerson(fnr, accessToken)

        if (pdlResponse.errors != null) {
            pdlResponse.errors.forEach {
                log.error(
                    "PDL returnerte error {}, {}",
                    it,
                    StructuredArguments.fields(loggingMeta)
                )
            }
        }
        if (pdlResponse.data.hentPerson == null) {
            log.error("Fant ikke person i PDL {}", StructuredArguments.fields(loggingMeta))
            throw RuntimeException("Fant ikke person i PDL")
        }
        if (pdlResponse.data.hentGeografiskTilknytning == null) {
            log.warn("Fant ikke GT for person i PDL {}", StructuredArguments.fields(loggingMeta))
        }

        return PdlPerson(
            gt = pdlResponse.data.hentGeografiskTilknytning?.finnGT(),
            adressebeskyttelse =
                pdlResponse.data.hentPerson.adressebeskyttelse?.firstOrNull()?.gradering,
            sisteKontaktAdresseIUtlandet =
                sisteKontaktAdresseIUtlandet(pdlResponse.data.hentPerson.kontaktadresse),
        )
    }
}

fun sisteKontaktAdresseIUtlandet(kontaktadresser: List<Kontaktadresse>?): Boolean {
    if (kontaktadresser.isNullOrEmpty()) {
        return false
    }
    val lastKontaktAdresse =
        kontaktadresser
            .filter { it.gyldigFraOgMed != null }
            .sortedByDescending { LocalDateTime.parse(it.gyldigFraOgMed) }
            .firstOrNull()

    return if (lastKontaktAdresse != null) {
        lastKontaktAdresse.type == "Utland"
    } else {
        false
    }
}

fun HentGeografiskTilknytning.finnGT(): String? {
    if (gtType == "BYDEL" && !gtBydel.isNullOrEmpty()) {
        return gtBydel
    } else if (gtType == "KOMMUNE" && !gtKommune.isNullOrEmpty()) {
        return gtKommune
    } else if (gtType == "UTLAND" && !gtLand.isNullOrEmpty()) {
        return gtLand
    } else {
        gtKommune?.let {
            return it
        }
        gtBydel?.let {
            return it
        }
        gtLand?.let {
            return it
        }
        return null
    }
}
