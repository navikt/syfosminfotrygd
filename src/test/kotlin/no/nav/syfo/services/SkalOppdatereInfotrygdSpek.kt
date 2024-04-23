package no.nav.syfo.services

import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.generatePeriode
import no.nav.syfo.generateSykmelding
import no.nav.syfo.model.sykmelding.Gradert
import no.nav.syfo.model.sykmelding.Merknad
import no.nav.syfo.model.sykmelding.ReceivedSykmelding
import org.amshove.kluent.shouldBeEqualTo

class SkalOppdatereInfotrygdSpek :
    FunSpec({
        context("Skal ikke oppdatere infotrygd") {
            val sm = mockk<ReceivedSykmelding>()
            test("Skal oppdater infotrygd ved ingen merknader") {
                every { sm.merknader } returns emptyList()
                every { sm.sykmelding.perioder } returns emptyList()
                skalOppdatereInfotrygd(sm) shouldBeEqualTo true
            }
            test("Skal oppdater infotrygd ved ingen merknader") {
                every { sm.merknader } returns null
                every { sm.sykmelding.perioder } returns emptyList()
                skalOppdatereInfotrygd(sm) shouldBeEqualTo true
            }
            test("Skal ikke oppdatere infotrygd ved merknader UGYLDIG_TILBAKEDATERING") {
                every { sm.merknader } returns listOf(Merknad("UGYLDIG_TILBAKEDATERING", null))
                every { sm.sykmelding.perioder } returns emptyList()
                skalOppdatereInfotrygd(sm) shouldBeEqualTo false
            }
            test(
                "Skal ikke oppdatere infotrygd ved merknader TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER"
            ) {
                every { sm.merknader } returns
                    listOf(Merknad("TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER", null))
                every { sm.sykmelding.perioder } returns emptyList()
                skalOppdatereInfotrygd(sm) shouldBeEqualTo false
            }
            test("Skal ikke oppdatere infotrygd ved merknader TILBAKEDATERT_PAPIRSYKMELDING") {
                every { sm.merknader } returns
                    listOf(Merknad("TILBAKEDATERT_PAPIRSYKMELDING", null))
                every { sm.sykmelding.perioder } returns emptyList()
                skalOppdatereInfotrygd(sm) shouldBeEqualTo false
            }
            test("Skal ikke oppdatere infotrygd ved merknader UNDER_BEHANDLING") {
                every { sm.merknader } returns listOf(Merknad("UNDER_BEHANDLING", null))
                every { sm.sykmelding.perioder } returns emptyList()
                skalOppdatereInfotrygd(sm) shouldBeEqualTo false
            }
            test("Skal ikke oppdatere infotrygd ved merknader DELVIS_GODKJENT") {
                every { sm.merknader } returns listOf(Merknad("DELVIS_GODKJENT", null))
                every { sm.sykmelding.perioder } returns emptyList()
                skalOppdatereInfotrygd(sm) shouldBeEqualTo true
            }
            test("Skal oppdatere infotrygd ved annen merknader ANNEN_MERKNAD") {
                every { sm.merknader } returns listOf(Merknad("ANNEN_MERKNAD", null))
                every { sm.sykmelding.perioder } returns emptyList()
                skalOppdatereInfotrygd(sm) shouldBeEqualTo true
            }

            test("Skal ikke oppdatere infotrygd hvis sykmeldingen inneholder reisetilskudd") {
                every { sm.merknader } returns emptyList()
                every { sm.sykmelding } returns
                    generateSykmelding(
                        perioder =
                            listOf(
                                generatePeriode(
                                    reisetilskudd = true,
                                ),
                            ),
                    )
                skalOppdatereInfotrygd(sm) shouldBeEqualTo false
            }

            test(
                "Skal ikke oppdatere infotrygd hvis sykmeldingen inneholder gradert sykmelding med reisetilskudd"
            ) {
                every { sm.merknader } returns emptyList()
                every { sm.sykmelding } returns
                    generateSykmelding(
                        perioder =
                            listOf(
                                generatePeriode(
                                    reisetilskudd = false,
                                    gradert = Gradert(true, 50),
                                ),
                            ),
                    )
                skalOppdatereInfotrygd(sm) shouldBeEqualTo false
            }
        }
    })
