package no.nav.syfo.pdl.service

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.OidcToken
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.Adressebeskyttelse
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.HentGeografiskTilknytning
import no.nav.syfo.pdl.client.model.HentPerson
import no.nav.syfo.pdl.client.model.ResponseData
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object PdlPersonServiceTest : Spek({
    val pdlClient = mockk<PdlClient>()
    val stsOidcClient = mockk<StsOidcClient>()
    val pdlPersonService = PdlPersonService(pdlClient, stsOidcClient)

    val loggingMeta = LoggingMeta("mottakid", "orgnr", "msgid", "sykmeldingid")

    beforeEachTest {
        clearAllMocks()
        coEvery { stsOidcClient.oidcToken() } returns OidcToken("Token", "JWT", 1L)
    }

    describe("PdlPersonService") {
        it("Henter GT og adressebeskyttelse fra PDL") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentGeografiskTilknytning = HentGeografiskTilknytning(gtType = "BYDEL", gtKommune = null, gtBydel = "030102", gtLand = null),
                    hentPerson = HentPerson(adressebeskyttelse = listOf(Adressebeskyttelse("FORTROLIG")))
                ),
                errors = null)

            runBlocking {
                val person = pdlPersonService.getPerson("fnr", loggingMeta)

                person.gt shouldEqual "030102"
                person.adressebeskyttelse shouldEqual "FORTROLIG"
            }
        }
        it("Skal feile hvis person ikke finnes i PDL") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentGeografiskTilknytning = HentGeografiskTilknytning(gtType = "BYDEL", gtKommune = null, gtBydel = "030102", gtLand = null),
                    hentPerson = null
                ),
                errors = null)

            assertFailsWith<RuntimeException> {
                runBlocking {
                    pdlPersonService.getPerson("fnr", loggingMeta)
                }
            }
        }
        it("Skal feile hvis geografiskTilknytning for person ikke finnes i PDL") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentGeografiskTilknytning = null,
                    hentPerson = HentPerson(emptyList())
                ),
                errors = null)

            assertFailsWith<RuntimeException> {
                runBlocking {
                    pdlPersonService.getPerson("fnr", loggingMeta)
                }
            }
        }
    }

    describe("Finn GT fra PDL-respons") {
        it("Bruker gtKommune hvis type er kommune og gtKommune er satt") {
            val hentGeografiskTilknytning = HentGeografiskTilknytning(gtType = "KOMMUNE", gtKommune = "0301", gtBydel = null, gtLand = null)

            hentGeografiskTilknytning.finnGT() shouldEqual "0301"
        }
        it("Bruker gtKommune uavhengig av type hvis kun gtKommune er satt") {
            val hentGeografiskTilknytning = HentGeografiskTilknytning(gtType = "UTLAND", gtKommune = "0301", gtBydel = null, gtLand = null)

            hentGeografiskTilknytning.finnGT() shouldEqual "0301"
        }
        it("Returnerer null hvis ingen GT er satt") {
            val hentGeografiskTilknytning = HentGeografiskTilknytning(gtType = "UDEFINERT", gtKommune = null, gtBydel = null, gtLand = null)

            hentGeografiskTilknytning.finnGT() shouldEqual null
        }
    }
})
