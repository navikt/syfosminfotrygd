package no.nav.syfo.services.tss

import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.TypeNavn
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.syfo.model.UtenlandskSykmelding
import no.nav.syfo.receivedSykmelding
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDate
import java.util.UUID
import javax.jms.MessageProducer
import javax.jms.Session

class TssServiceKtTest : FunSpec({
    val tssProducer = mockk<MessageProducer>(relaxed = true)
    val session = mockk<Session>(relaxed = true)
    val loggingMeta = LoggingMeta("", "", "", "")

    beforeTest {
        mockkStatic("no.nav.syfo.services.tss.GetTssSamhandlerDataServiceKt")
    }

    context("GetTssId") {
        test("Bruker TSS-ident fra Infotrygdrespons hvis den finnes") {
            val infotrygdForespResponse = InfotrygdForesp().apply {
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.now()
                                arbufoerTOM = LocalDate.now().plusDays(2)
                                legeNavn = TypeNavn().apply {
                                    fornavn = "Fornavn"
                                    etternavn = "Etternavnsen"
                                }
                                legeInstNr = "9898".toBigInteger()
                            }
                        }
                    )
                }
            }
            val receivedSykmelding = receivedSykmelding(UUID.randomUUID().toString()).copy(tssid = null)

            val tssId = getTssId(infotrygdForespResponse, receivedSykmelding, tssProducer, session, loggingMeta)

            tssId shouldBeEqualTo "9898"
        }
        test("Henter TSS-ident fra TSS hvis den ikke finnes i infotrygdresponsen") {
            coEvery { fetchTssSamhandlerInfo(any(), any(), any()) } returns "1234"
            val infotrygdForespResponse = InfotrygdForesp()
            val receivedSykmelding = receivedSykmelding(UUID.randomUUID().toString()).copy(tssid = null)

            val tssId = getTssId(infotrygdForespResponse, receivedSykmelding, tssProducer, session, loggingMeta)

            tssId shouldBeEqualTo "1234"
        }
        test("Returnerer null hvis kall mot TSS feiler") {
            coEvery { fetchTssSamhandlerInfo(any(), any(), any()) } throws RuntimeException("Noe gikk galt")
            val infotrygdForespResponse = InfotrygdForesp()
            val receivedSykmelding = receivedSykmelding(UUID.randomUUID().toString()).copy(tssid = null)

            val tssId = getTssId(infotrygdForespResponse, receivedSykmelding, tssProducer, session, loggingMeta)

            tssId shouldBeEqualTo null
        }
        test("Returnerer 0 hvis sykmeldingen er utenlandsk") {
            val infotrygdForespResponse = InfotrygdForesp().apply {
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.now()
                                arbufoerTOM = LocalDate.now().plusDays(2)
                                legeNavn = TypeNavn().apply {
                                    fornavn = "Fornavn"
                                    etternavn = "Etternavnsen"
                                }
                                legeInstNr = "9898".toBigInteger()
                            }
                        }
                    )
                }
            }
            val receivedSykmelding = receivedSykmelding(UUID.randomUUID().toString()).copy(
                tssid = null,
                utenlandskSykmelding = UtenlandskSykmelding("POL", false)
            )

            val tssId = getTssId(infotrygdForespResponse, receivedSykmelding, tssProducer, session, loggingMeta)

            tssId shouldBeEqualTo "0"
        }
    }
})
