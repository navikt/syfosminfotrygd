package no.nav.syfo.services

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.LoggingMeta
import no.nav.syfo.NAV_OPPFOLGING_UTLAND_KONTOR_NR
import no.nav.syfo.client.Norg2Client
import no.nav.syfo.fetchBehandlendeEnhet
import no.nav.syfo.fetchDiskresjonsKode
import no.nav.syfo.fetchGeografiskTilknytningAsync
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.binding.ArbeidsfordelingV1
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3

class FindNAVKontorService(
    val receivedSykmelding: ReceivedSykmelding,
    val personV3: PersonV3,
    val norg2Client: Norg2Client,
    val arbeidsfordelingV1: ArbeidsfordelingV1,
    val loggingMeta: LoggingMeta
) {

    suspend fun finnBehandlendeEnhet(): String {
        val geografiskTilknytning = fetchGeografiskTilknytningAsync(personV3, receivedSykmelding)
        val patientDiskresjonsKode = fetchDiskresjonsKode(personV3, receivedSykmelding)
        val finnBehandlendeEnhetListeResponse = fetchBehandlendeEnhet(arbeidsfordelingV1, geografiskTilknytning.geografiskTilknytning, patientDiskresjonsKode)
        if (finnBehandlendeEnhetListeResponse?.behandlendeEnhetListe?.firstOrNull()?.enhetId == null) {
            log.error("arbeidsfordeling fant ingen nav-enheter {}", StructuredArguments.fields(loggingMeta))
        }
        return finnBehandlendeEnhetListeResponse?.behandlendeEnhetListe?.firstOrNull()?.enhetId ?: NAV_OPPFOLGING_UTLAND_KONTOR_NR
    }

    @KtorExperimentalAPI
    suspend fun finnLokaltNavkontor(): String {
        val geografiskTilknytning = fetchGeografiskTilknytningAsync(personV3, receivedSykmelding)
        val patientDiskresjonsKode = fetchDiskresjonsKode(personV3, receivedSykmelding)
        return if (geografiskTilknytning.geografiskTilknytning?.geografiskTilknytning.isNullOrEmpty()) {
            log.error("GeografiskTilknytning er tomt eller null, benytter nav oppfolings utland nr:$NAV_OPPFOLGING_UTLAND_KONTOR_NR,  {}", StructuredArguments.fields(loggingMeta))
            NAV_OPPFOLGING_UTLAND_KONTOR_NR
        } else {
            norg2Client.getLocalNAVOffice(geografiskTilknytning.geografiskTilknytning.geografiskTilknytning, patientDiskresjonsKode).enhetNr
        }
    }
}
