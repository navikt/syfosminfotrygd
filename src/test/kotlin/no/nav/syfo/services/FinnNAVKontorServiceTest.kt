package no.nav.syfo.services

import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.NAV_OPPFOLGING_UTLAND_KONTOR_NR
import no.nav.syfo.NAV_VIKAFOSSEN_KONTOR_NR
import no.nav.syfo.client.norg.Enhet
import no.nav.syfo.client.norg.Norg2Client
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo

class FinnNAVKontorServiceTest :
    FunSpec({
        val pdlService = mockk<PdlPersonService>()
        val norg2Client = mockk<Norg2Client>()
        val navKontorService = FinnNAVKontorService(pdlService, norg2Client)
        val loggingMeta = LoggingMeta("1", "1", "1", "1")
        test("Get utenlands enhetsnr") {
            coEvery { pdlService.getPerson(any(), any()) } returns PdlPerson("gt", null, false)
            coEvery { norg2Client.getLocalNAVOffice(any(), null, loggingMeta) } returns
                Enhet("0393")
            val enhetsnr = navKontorService.finnLokaltNavkontor("1234567890", loggingMeta)
            enhetsnr shouldBeEqualTo NAV_OPPFOLGING_UTLAND_KONTOR_NR
        }

        test("Get vikafossen enhetsnr") {
            coEvery { pdlService.getPerson(any(), any()) } returns PdlPerson("gt", null, false)
            coEvery { norg2Client.getLocalNAVOffice(any(), null, loggingMeta) } returns
                Enhet(NAV_VIKAFOSSEN_KONTOR_NR)
            val enhetsnr = navKontorService.finnLokaltNavkontor("1234567890", loggingMeta)
            enhetsnr shouldBeEqualTo NAV_VIKAFOSSEN_KONTOR_NR
        }

        test("Get lokalt navkontor enhetsnr") {
            coEvery { pdlService.getPerson(any(), any()) } returns PdlPerson("gt", null, false)
            coEvery { norg2Client.getLocalNAVOffice(any(), null, loggingMeta) } returns
                Enhet("1234")
            val enhetsnr = navKontorService.finnLokaltNavkontor("1234567890", loggingMeta)
            enhetsnr shouldBeEqualTo "1234"
        }
    })
