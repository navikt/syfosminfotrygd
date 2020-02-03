package no.nav.syfo.services

import com.ctc.wstx.exc.WstxException
import io.ktor.util.KtorExperimentalAPI
import java.io.IOException
import java.lang.IllegalStateException
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.NAV_OPPFOLGING_UTLAND_KONTOR_NR
import no.nav.syfo.client.Norg2Client
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.util.LoggingMeta
import no.nav.tjeneste.virksomhet.person.v3.binding.HentGeografiskTilknytningPersonIkkeFunnet
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonPersonIkkeFunnet
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Personidenter
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningRequest
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningResponse
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonRequest

class FindNAVKontorService @KtorExperimentalAPI constructor(
    val receivedSykmelding: ReceivedSykmelding,
    val personV3: PersonV3,
    val norg2Client: Norg2Client,
    val loggingMeta: LoggingMeta
) {

    @KtorExperimentalAPI
    suspend fun finnLokaltNavkontor(): String {
        val geografiskTilknytning = fetchGeografiskTilknytning(personV3, receivedSykmelding)
        val patientDiskresjonsKode = fetchDiskresjonsKode(personV3, receivedSykmelding)
        return if (geografiskTilknytning == null || geografiskTilknytning.geografiskTilknytning?.geografiskTilknytning.isNullOrEmpty()) {
            log.warn("GeografiskTilknytning er tomt eller null, benytter nav oppfolings utland nr:$NAV_OPPFOLGING_UTLAND_KONTOR_NR,  {}", fields(loggingMeta))
            NAV_OPPFOLGING_UTLAND_KONTOR_NR
        } else {
            norg2Client.getLocalNAVOffice(geografiskTilknytning.geografiskTilknytning.geografiskTilknytning, patientDiskresjonsKode).enhetNr
        }
    }

    private suspend fun fetchDiskresjonsKode(personV3: PersonV3, receivedSykmelding: ReceivedSykmelding): String? =
            try {
                retry(callName = "tps_hent_person",
                        retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
                        legalExceptions = *arrayOf(IOException::class, WstxException::class)) {
                    personV3.hentPerson(HentPersonRequest()
                            .withAktoer(PersonIdent().withIdent(NorskIdent().withIdent(receivedSykmelding.personNrPasient)))
                    ).person?.diskresjonskode?.value
                }
            } catch (hentPersonPersonIkkeFunnet: HentPersonPersonIkkeFunnet) {
                    null
            }

    suspend fun fetchGeografiskTilknytning(
        personV3: PersonV3,
        receivedSykmelding: ReceivedSykmelding
    ): HentGeografiskTilknytningResponse? =
            try {
                retry(callName = "tps_hent_geografisktilknytning",
                        retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
                        legalExceptions = *arrayOf(IOException::class, WstxException::class, IllegalStateException::class)) {
                    personV3.hentGeografiskTilknytning(HentGeografiskTilknytningRequest().withAktoer(PersonIdent().withIdent(
                            NorskIdent()
                                    .withIdent(receivedSykmelding.personNrPasient)
                                    .withType(Personidenter().withValue("FNR")))))
                }
            } catch (hentGeografiskTilknytningPersonIkkeFunnet: HentGeografiskTilknytningPersonIkkeFunnet) {
                null
            }
}
