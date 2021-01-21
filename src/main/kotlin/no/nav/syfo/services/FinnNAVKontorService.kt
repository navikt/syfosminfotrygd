package no.nav.syfo.services

import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.client.Norg2Client
import no.nav.syfo.log
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.util.LoggingMeta

@KtorExperimentalAPI
class FinnNAVKontorService(
    private val pdlPersonService: PdlPersonService,
    private val norg2Client: Norg2Client
) {
    suspend fun finnLokaltNavkontor(fnr: String, loggingMeta: LoggingMeta): String {
        val pdlPerson = pdlPersonService.getPerson(fnr, loggingMeta)
        log.info("Fant GT=${pdlPerson.gt} og diskresjonskode ${pdlPerson.adressebeskyttelse}")
        return norg2Client.getLocalNAVOffice(pdlPerson.gt, pdlPerson.adressebeskyttelse).enhetNr
    }
}
