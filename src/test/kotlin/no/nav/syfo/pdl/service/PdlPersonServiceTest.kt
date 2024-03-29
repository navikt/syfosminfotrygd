package no.nav.syfo.pdl.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkClass
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.Adressebeskyttelse
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.HentGeografiskTilknytning
import no.nav.syfo.pdl.client.model.HentPerson
import no.nav.syfo.pdl.client.model.Kontaktadresse
import no.nav.syfo.pdl.client.model.ResponseData
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo

class PdlPersonServiceTest :
    FunSpec({
        val pdlClient = mockk<PdlClient>()
        val accessTokenClient = mockkClass(AccessTokenClientV2::class)
        val pdlPersonService = PdlPersonService(pdlClient, accessTokenClient, "pdlscope")

        val loggingMeta = LoggingMeta("mottakid", "orgnr", "msgid", "sykmeldingid")

        beforeTest { clearAllMocks() }

        context("PdlPersonService") {
            test("Henter GT og adressebeskyttelse fra PDL") {
                coEvery { pdlClient.getPerson(any(), any()) } returns
                    GetPersonResponse(
                        ResponseData(
                            hentGeografiskTilknytning =
                                HentGeografiskTilknytning(
                                    gtType = "BYDEL",
                                    gtKommune = null,
                                    gtBydel = "030102",
                                    gtLand = null
                                ),
                            hentPerson =
                                HentPerson(
                                    adressebeskyttelse = listOf(Adressebeskyttelse("FORTROLIG")),
                                    kontaktadresse =
                                        listOf(
                                            Kontaktadresse(
                                                type = "Innland",
                                                gyldigFraOgMed = "2023-01-01T00:00",
                                                gyldigTilOgMed = null,
                                            ),
                                        ),
                                ),
                        ),
                        errors = null,
                    )

                coEvery { accessTokenClient.getAccessTokenV2(any()) } returns "token"

                val person = pdlPersonService.getPerson("fnr", loggingMeta)

                person.gt shouldBeEqualTo "030102"
                person.adressebeskyttelse shouldBeEqualTo "FORTROLIG"
            }
            test("Skal feile hvis person ikke finnes i PDL") {
                coEvery { pdlClient.getPerson(any(), any()) } returns
                    GetPersonResponse(
                        ResponseData(
                            hentGeografiskTilknytning =
                                HentGeografiskTilknytning(
                                    gtType = "BYDEL",
                                    gtKommune = null,
                                    gtBydel = "030102",
                                    gtLand = null
                                ),
                            hentPerson = null,
                        ),
                        errors = null,
                    )

                coEvery { accessTokenClient.getAccessTokenV2(any()) } returns "token"

                assertFailsWith<RuntimeException> {
                    runBlocking { pdlPersonService.getPerson("fnr", loggingMeta) }
                }
            }
            test("Setter GT=null hvis geografiskTilknytning for person ikke finnes i PDL") {
                coEvery { pdlClient.getPerson(any(), any()) } returns
                    GetPersonResponse(
                        ResponseData(
                            hentGeografiskTilknytning = null,
                            hentPerson = HentPerson(emptyList(), emptyList()),
                        ),
                        errors = null,
                    )

                coEvery { accessTokenClient.getAccessTokenV2(any()) } returns "token"

                val person = pdlPersonService.getPerson("fnr", loggingMeta)

                person.gt shouldBeEqualTo null
            }
        }

        context("Finn GT fra PDL-respons") {
            test("Bruker gtKommune hvis type er kommune og gtKommune er satt") {
                val hentGeografiskTilknytning =
                    HentGeografiskTilknytning(
                        gtType = "KOMMUNE",
                        gtKommune = "0301",
                        gtBydel = null,
                        gtLand = null
                    )

                hentGeografiskTilknytning.finnGT() shouldBeEqualTo "0301"
            }
            test("Bruker gtKommune uavhengig av type hvis kun gtKommune er satt") {
                val hentGeografiskTilknytning =
                    HentGeografiskTilknytning(
                        gtType = "UTLAND",
                        gtKommune = "0301",
                        gtBydel = null,
                        gtLand = null
                    )

                hentGeografiskTilknytning.finnGT() shouldBeEqualTo "0301"
            }
            test("Returnerer null hvis ingen GT er satt") {
                val hentGeografiskTilknytning =
                    HentGeografiskTilknytning(
                        gtType = "UDEFINERT",
                        gtKommune = null,
                        gtBydel = null,
                        gtLand = null
                    )

                hentGeografiskTilknytning.finnGT() shouldBeEqualTo null
            }
        }
        context("finn sisteKontaktAdresse") {
            test("Siste kontakt adresse er utlandet") {
                coEvery { pdlClient.getPerson(any(), any()) } returns
                    GetPersonResponse(
                        ResponseData(
                            hentGeografiskTilknytning = null,
                            hentPerson =
                                HentPerson(
                                    emptyList(),
                                    kontaktadresse =
                                        listOf(
                                            Kontaktadresse(
                                                type = "Utland",
                                                gyldigFraOgMed = "2023-02-02T00:00",
                                                gyldigTilOgMed = null,
                                            ),
                                            Kontaktadresse(
                                                type = "Innland",
                                                gyldigFraOgMed = "2023-01-01T00:00",
                                                gyldigTilOgMed = "2023-02-01T00:00",
                                            ),
                                        ),
                                ),
                        ),
                        errors = null,
                    )

                coEvery { accessTokenClient.getAccessTokenV2(any()) } returns "token"

                val person = pdlPersonService.getPerson("fnr", loggingMeta)

                person.sisteKontaktAdresseIUtlandet shouldBeEqualTo true
            }

            test("Siste kontakt adresse er innland") {
                coEvery { pdlClient.getPerson(any(), any()) } returns
                    GetPersonResponse(
                        ResponseData(
                            hentGeografiskTilknytning = null,
                            hentPerson =
                                HentPerson(
                                    emptyList(),
                                    kontaktadresse =
                                        listOf(
                                            Kontaktadresse(
                                                type = "Innland",
                                                gyldigFraOgMed = "2023-02-02T00:00",
                                                gyldigTilOgMed = null,
                                            ),
                                            Kontaktadresse(
                                                type = "Utland",
                                                gyldigFraOgMed = "2023-01-01T00:00",
                                                gyldigTilOgMed = "2023-02-01T00:00",
                                            ),
                                        ),
                                ),
                        ),
                        errors = null,
                    )

                coEvery { accessTokenClient.getAccessTokenV2(any()) } returns "token"

                val person = pdlPersonService.getPerson("fnr", loggingMeta)

                person.sisteKontaktAdresseIUtlandet shouldBeEqualTo false
            }
        }
    })
