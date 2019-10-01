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
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.binding.ArbeidsfordelingV1
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.ArbeidsfordelingKriterier
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Diskresjonskoder
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Geografi
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Oppgavetyper
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Tema
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.meldinger.FinnBehandlendeEnhetListeRequest
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.meldinger.FinnBehandlendeEnhetListeResponse
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.GeografiskTilknytning
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
    val arbeidsfordelingV1: ArbeidsfordelingV1,
    val loggingMeta: LoggingMeta
) {

    suspend fun finnBehandlendeEnhet(): String {
        val geografiskTilknytning = fetchGeografiskTilknytning(personV3, receivedSykmelding)
        val patientDiskresjonsKode = fetchDiskresjonsKode(personV3, receivedSykmelding)
        val finnBehandlendeEnhetListeResponse = fetchBehandlendeEnhet(arbeidsfordelingV1, geografiskTilknytning.geografiskTilknytning, patientDiskresjonsKode)
        if (finnBehandlendeEnhetListeResponse?.behandlendeEnhetListe?.firstOrNull()?.enhetId == null) {
            log.warn("arbeidsfordeling fant ingen nav-enheter {}", fields(loggingMeta))
        }
        return finnBehandlendeEnhetListeResponse?.behandlendeEnhetListe?.firstOrNull()?.enhetId ?: NAV_OPPFOLGING_UTLAND_KONTOR_NR
    }

    @KtorExperimentalAPI
    suspend fun finnLokaltNavkontor(): String {
        val geografiskTilknytning = fetchGeografiskTilknytning(personV3, receivedSykmelding)
        val patientDiskresjonsKode = fetchDiskresjonsKode(personV3, receivedSykmelding)
        return if (geografiskTilknytning.geografiskTilknytning?.geografiskTilknytning.isNullOrEmpty()) {
            log.warn("GeografiskTilknytning er tomt eller null, benytter nav oppfolings utland nr:$NAV_OPPFOLGING_UTLAND_KONTOR_NR,  {}", fields(loggingMeta))
            NAV_OPPFOLGING_UTLAND_KONTOR_NR
        } else {
            norg2Client.getLocalNAVOffice(geografiskTilknytning.geografiskTilknytning.geografiskTilknytning, patientDiskresjonsKode).enhetNr
        }
    }

    suspend fun fetchDiskresjonsKode(personV3: PersonV3, receivedSykmelding: ReceivedSykmelding): String? =
            retry(callName = "tps_hent_person",
                    retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
                    legalExceptions = *arrayOf(IOException::class, WstxException::class)) {
                personV3.hentPerson(HentPersonRequest()
                        .withAktoer(PersonIdent().withIdent(NorskIdent().withIdent(receivedSykmelding.personNrPasient)))
                ).person?.diskresjonskode?.value
            }

    suspend fun fetchBehandlendeEnhet(arbeidsfordelingV1: ArbeidsfordelingV1, geografiskTilknytning: GeografiskTilknytning?, patientDiskresjonsKode: String?): FinnBehandlendeEnhetListeResponse? =
            retry(callName = "finn_nav_kontor",
                    retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
                    legalExceptions = *arrayOf(IOException::class, WstxException::class)) {
                arbeidsfordelingV1.finnBehandlendeEnhetListe(FinnBehandlendeEnhetListeRequest().apply {
                    val afk = ArbeidsfordelingKriterier()
                    if (geografiskTilknytning?.geografiskTilknytning != null) {
                        afk.geografiskTilknytning = Geografi().apply {
                            value = geografiskTilknytning.geografiskTilknytning
                        }
                    }
                    afk.tema = Tema().apply {
                        value = "SYM"
                    }

                    afk.oppgavetype = Oppgavetyper().apply {
                        value = "BEH_EL_SYM"
                    }

                    if (!patientDiskresjonsKode.isNullOrBlank()) {
                        afk.diskresjonskode = Diskresjonskoder().apply {
                            value = patientDiskresjonsKode
                        }
                    }

                    arbeidsfordelingKriterier = afk
                })
            }

    suspend fun fetchGeografiskTilknytning(
        personV3: PersonV3,
        receivedSykmelding: ReceivedSykmelding
    ): HentGeografiskTilknytningResponse =
            retry(callName = "tps_hent_geografisktilknytning",
                    retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
                    legalExceptions = *arrayOf(IOException::class, WstxException::class, IllegalStateException::class)) {
                personV3.hentGeografiskTilknytning(HentGeografiskTilknytningRequest().withAktoer(PersonIdent().withIdent(
                        NorskIdent()
                                .withIdent(receivedSykmelding.personNrPasient)
                                .withType(Personidenter().withValue("FNR")))))
            }
}
