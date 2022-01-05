package no.nav.syfo.services

import no.nav.syfo.client.norg.Norg2Client
import no.nav.syfo.pdl.model.getDiskresjonskode
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.util.LoggingMeta

class FinnNAVKontorService(
    private val pdlPersonService: PdlPersonService,
    private val norg2Client: Norg2Client
) {
    suspend fun finnLokaltNavkontor(fnr: String, loggingMeta: LoggingMeta): String {
        val pdlPerson = pdlPersonService.getPerson(fnr, loggingMeta)
        return norg2Client.getLocalNAVOffice(pdlPerson.gt, pdlPerson.getDiskresjonskode(), loggingMeta).enhetNr
    }
}
