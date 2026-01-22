package no.nav.syfo.services

import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import no.nav.helse.sm2013.CV
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.diagnose.ICD10
import no.nav.syfo.infotrygd.InfotrygdQuery
import no.nav.syfo.model.sykmelding.Diagnose
import no.nav.tsm.diagnoser.ICPC2B
import org.junit.jupiter.api.Assertions.*

class InfotrygdForespValuesTest :
    FunSpec({
        test("Map ICPC_2B to correct values from helseOpplysningerArbeidsuforhet") {
            val helseOpplysningerArbeidsuforhet =
                mockk<HelseOpplysningerArbeidsuforhet>(relaxed = true)
            every {
                helseOpplysningerArbeidsuforhet.medisinskVurdering.hovedDiagnose.diagnosekode
            } returns
                CV().apply {
                    s = ICPC2B.OID
                    v = ICPC2B.get("R74.0001")?.code
                }
            val values = InfotrygdForespValues.from(helseOpplysningerArbeidsuforhet)
            assertEquals("5", values.hovedDiagnosekodeverk)
            assertEquals("R74", values.hovedDiagnosekode)
            assertEquals(null, values.biDiagnosekodeverk)
            assertEquals(null, values.biDiagnoseKode)
        }

        test("Map ICPC_2B to correct values from infotrygdQueries") {
            val infotrygdQuery = mockk<InfotrygdQuery>(relaxed = true)
            every { infotrygdQuery.hoveddiagnose } returns
                Diagnose(
                    system = ICPC2B.OID,
                    kode = "R74.0001",
                    tekst = "tekst",
                )

            every { infotrygdQuery.bidiagnose } returns null
            val values = InfotrygdForespValues.from(infotrygdQuery)
            assertEquals("5", values.hovedDiagnosekodeverk)
            assertEquals("R74", values.hovedDiagnosekode)
        }

        test("Map ICPC_2B bidiagnose to correct values from infotrygdQueries") {
            val infotrygdQuery = mockk<InfotrygdQuery>(relaxed = true)
            every { infotrygdQuery.hoveddiagnose } returns
                Diagnose(
                    system = ICPC2B.OID,
                    kode = "R74.0001",
                    tekst = "tekst",
                )

            every { infotrygdQuery.bidiagnose } returns
                Diagnose(
                    system = ICPC2B.OID,
                    kode = "R74.0002",
                    tekst = "tekst",
                )
            val values = InfotrygdForespValues.from(infotrygdQuery)
            assertEquals("5", values.hovedDiagnosekodeverk)
            assertEquals("5", values.biDiagnosekodeverk)
            assertEquals("R74", values.biDiagnoseKode)
            assertEquals("R74", values.hovedDiagnosekode)
        }
        test("Map invalid ICPC_2B bidiagnose to correct values from infotrygdQueries") {
            val infotrygdQuery = mockk<InfotrygdQuery>(relaxed = true)
            every { infotrygdQuery.hoveddiagnose } returns
                Diagnose(
                    system = ICPC2B.OID,
                    kode = "R74.000111",
                    tekst = "tekst",
                )

            every { infotrygdQuery.bidiagnose } returns
                Diagnose(
                    system = ICPC2B.OID,
                    kode = "R74.000222",
                    tekst = "tekst",
                )
            val values = InfotrygdForespValues.from(infotrygdQuery)
            assertEquals(null, values.hovedDiagnosekodeverk)
            assertEquals(null, values.biDiagnosekodeverk)
            assertEquals(null, values.biDiagnoseKode)
            assertEquals(null, values.hovedDiagnosekode)
        }

        test("Map lowercase ICD10 diagnose to correct values from infotrygdQueries") {
            val infotrygdQuery = mockk<InfotrygdQuery>(relaxed = true)
            every { infotrygdQuery.hoveddiagnose } returns
                Diagnose(
                    system = no.nav.tsm.diagnoser.ICD10.OID,
                    kode = "k01.1",
                    tekst = "tekst",
                )

            val values = InfotrygdForespValues.from(infotrygdQuery)
            assertEquals("3", values.hovedDiagnosekodeverk)
            assertEquals(null, values.biDiagnosekodeverk)
            assertEquals(null, values.biDiagnoseKode)
            assertEquals("K01.1", values.hovedDiagnosekode)
        }
    })
