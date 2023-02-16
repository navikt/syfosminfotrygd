package no.nav.syfo.services

import no.nav.syfo.NAV_OPPFOLGING_UTLAND_KONTOR_NR
import no.nav.syfo.NAV_VIKAFOSSEN_KONTOR_NR
import no.nav.syfo.client.norg.Norg2Client
import no.nav.syfo.pdl.model.getDiskresjonskode
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.util.LoggingMeta

class FinnNAVKontorService(
    private val pdlPersonService: PdlPersonService,
    private val norg2Client: Norg2Client
) {
    suspend fun finnLokaltNavkontor(fnr: String, loggingMeta: LoggingMeta, utenlandskSykmelding: Boolean): String {
        val pdlPerson = pdlPersonService.getPerson(fnr, loggingMeta)
        val enhetNr = norg2Client.getLocalNAVOffice(pdlPerson.gt, pdlPerson.getDiskresjonskode(), loggingMeta).enhetNr
        return if (enhetNr != NAV_VIKAFOSSEN_KONTOR_NR && utenlandskSykmelding) NAV_OPPFOLGING_UTLAND_KONTOR_NR else enhetNr
    }
}
