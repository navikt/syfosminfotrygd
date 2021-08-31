package no.nav.syfo.pdl.service

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.Adressebeskyttelse
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.HentGeografiskTilknytning
import no.nav.syfo.pdl.client.model.HentPerson
import no.nav.syfo.pdl.client.model.ResponseData
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertFailsWith

@KtorExperimentalAPI
object PdlPersonServiceTest : Spek({
    val pdlClient = mockk<PdlClient>()
    val accessTokenClient = mockkClass(AccessTokenClientV2::class)
    val pdlPersonService = PdlPersonService(pdlClient, accessTokenClient, "pdlscope")

    val loggingMeta = LoggingMeta("mottakid", "orgnr", "msgid", "sykmeldingid")

    beforeEachTest {
        clearAllMocks()
    }

    describe("PdlPersonService") {
        it("Henter GT og adressebeskyttelse fra PDL") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentGeografiskTilknytning = HentGeografiskTilknytning(gtType = "BYDEL", gtKommune = null, gtBydel = "030102", gtLand = null),
                    hentPerson = HentPerson(adressebeskyttelse = listOf(Adressebeskyttelse("FORTROLIG")))
                ),
                errors = null
            )

            coEvery { accessTokenClient.getAccessTokenV2(any()) } returns "token"

            runBlocking {
                val person = pdlPersonService.getPerson("fnr", loggingMeta)

                person.gt shouldBeEqualTo "030102"
                person.adressebeskyttelse shouldBeEqualTo "FORTROLIG"
            }
        }
        it("Skal feile hvis person ikke finnes i PDL") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentGeografiskTilknytning = HentGeografiskTilknytning(gtType = "BYDEL", gtKommune = null, gtBydel = "030102", gtLand = null),
                    hentPerson = null
                ),
                errors = null
            )

            coEvery { accessTokenClient.getAccessTokenV2(any()) } returns "token"

            assertFailsWith<RuntimeException> {
                runBlocking {
                    pdlPersonService.getPerson("fnr", loggingMeta)
                }
            }
        }
        it("Setter GT=null hvis geografiskTilknytning for person ikke finnes i PDL") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentGeografiskTilknytning = null,
                    hentPerson = HentPerson(emptyList())
                ),
                errors = null
            )

            coEvery { accessTokenClient.getAccessTokenV2(any()) } returns "token"

            runBlocking {
                val person = pdlPersonService.getPerson("fnr", loggingMeta)

                person.gt shouldBeEqualTo null
            }
        }
    }

    describe("Finn GT fra PDL-respons") {
        it("Bruker gtKommune hvis type er kommune og gtKommune er satt") {
            val hentGeografiskTilknytning = HentGeografiskTilknytning(gtType = "KOMMUNE", gtKommune = "0301", gtBydel = null, gtLand = null)

            hentGeografiskTilknytning.finnGT() shouldBeEqualTo "0301"
        }
        it("Bruker gtKommune uavhengig av type hvis kun gtKommune er satt") {
            val hentGeografiskTilknytning = HentGeografiskTilknytning(gtType = "UTLAND", gtKommune = "0301", gtBydel = null, gtLand = null)

            hentGeografiskTilknytning.finnGT() shouldBeEqualTo "0301"
        }
        it("Returnerer null hvis ingen GT er satt") {
            val hentGeografiskTilknytning = HentGeografiskTilknytning(gtType = "UDEFINERT", gtKommune = null, gtBydel = null, gtLand = null)

            hentGeografiskTilknytning.finnGT() shouldBeEqualTo null
        }
    }
})
