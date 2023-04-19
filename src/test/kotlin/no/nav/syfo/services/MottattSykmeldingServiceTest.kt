package no.nav.syfo.services

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.StatusType
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.client.Behandler
import no.nav.syfo.client.Godkjenning
import no.nav.syfo.client.Kode
import no.nav.syfo.client.ManuellClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.createDefaultHealthInformation
import no.nav.syfo.generateSykmelding
import no.nav.syfo.model.HelsepersonellKategori
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.Merknad
import no.nav.syfo.model.Status
import no.nav.syfo.receivedSykmelding
import no.nav.syfo.services.tss.fetchTssSamhandlerInfo
import no.nav.syfo.services.updateinfotrygd.UpdateInfotrygdService
import no.nav.syfo.services.updateinfotrygd.createFellesFormat
import no.nav.syfo.toString
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.fellesformatMarshaller
import java.time.LocalDate
import java.util.UUID
import javax.jms.MessageProducer
import javax.jms.Session

class MottattSykmeldingServiceTest : FunSpec({
    val updateInfotrygdService = mockk<UpdateInfotrygdService>(relaxed = true)
    val finnNAVKontorService = mockk<FinnNAVKontorService>()
    val manuellClient = mockk<ManuellClient>()
    val manuellBehandlingService = mockk<ManuellBehandlingService>(relaxed = true)
    val behandlingsutfallService = mockk<BehandlingsutfallService>(relaxed = true)
    val norskHelsenettClient = mockk<NorskHelsenettClient>()
    val infotrygdOppdateringProducer = mockk<MessageProducer>(relaxed = true)
    val infotrygdSporringProducer = mockk<MessageProducer>(relaxed = true)
    val tssProducer = mockk<MessageProducer>(relaxed = true)
    val session = mockk<Session>(relaxed = true)
    val loggingMeta = LoggingMeta("", "", "", "")
    val mottattSykmeldingService = MottattSykmeldingService(
        updateInfotrygdService,
        finnNAVKontorService,
        manuellClient,
        manuellBehandlingService,
        behandlingsutfallService,
        norskHelsenettClient,
    )

    beforeTest {
        mockkStatic("no.nav.syfo.services.GetInfotrygdForespServiceKt")
        mockkStatic("no.nav.syfo.services.tss.GetTssSamhandlerDataServiceKt")
    }
    beforeEach {
        clearMocks(updateInfotrygdService, finnNAVKontorService, manuellClient, manuellBehandlingService, behandlingsutfallService, norskHelsenettClient)
        coEvery { manuellClient.behandletAvManuell(any(), any()) } returns false
        coEvery { norskHelsenettClient.finnBehandler(any(), any()) } returns getBehandler()
        coEvery { finnNAVKontorService.finnLokaltNavkontor(any(), any()) } returns "0101"
    }

    context("handleMessage") {
        test("Happy case") {
            coEvery { fetchInfotrygdForesp(any(), any(), any(), any()) } returns getInfotrygdForespResponse()
            coEvery { fetchTssSamhandlerInfo(any(), any(), any()) } returns "1234"
            val healthInformation = createDefaultHealthInformation()
            val fellesformat = createFellesFormat(healthInformation)

            val receivedSykmelding = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = generateSykmelding(
                    medisinskVurdering = MedisinskVurdering(hovedDiagnose = null, biDiagnoser = emptyList(), svangerskap = false, yrkesskade = false, yrkesskadeDato = null, annenFraversArsak = null),
                ),
                fellesformat = fellesformatMarshaller.toString(fellesformat),
            )

            mottattSykmeldingService.handleMessage(receivedSykmelding, infotrygdOppdateringProducer, infotrygdSporringProducer, tssProducer, session, loggingMeta)

            coVerify {
                updateInfotrygdService.updateInfotrygd(
                    infotrygdOppdateringProducer,
                    session,
                    loggingMeta,
                    any(),
                    receivedSykmelding.copy(tssid = "1234"),
                    "LE",
                    "0101",
                    match { it.status == Status.OK },
                    false,
                )
            }
            coVerify { manuellClient.behandletAvManuell(any(), any()) }
            coVerify(exactly = 0) { behandlingsutfallService.sendRuleCheckValidationResult(any(), any(), any()) }
            coVerify(exactly = 0) { manuellBehandlingService.produceManualTaskAndSendValidationResults(any(), any(), any(), any(), any(), any()) }
        }
        test("Oppdaterer ikke infotrygd hvis sykmelding har merknad") {
            val receivedSykmeldingMedMerknad = receivedSykmelding(id = UUID.randomUUID().toString(), merknader = listOf(Merknad("UNDER_BEHANDLING", "")))

            mottattSykmeldingService.handleMessage(receivedSykmeldingMedMerknad, infotrygdOppdateringProducer, infotrygdSporringProducer, tssProducer, session, loggingMeta)

            coVerify(exactly = 0) { updateInfotrygdService.updateInfotrygd(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { manuellClient.behandletAvManuell(any(), any()) }
            coVerify { behandlingsutfallService.sendRuleCheckValidationResult(any(), match { it.status == Status.OK }, loggingMeta) }
        }
        test("Går til manuell behandling hvis hoveddiagnose mangler") {
            val healthInformation = createDefaultHealthInformation()
            healthInformation.medisinskVurdering = HelseOpplysningerArbeidsuforhet.MedisinskVurdering().apply {
                hovedDiagnose = null
            }
            val fellesformat = createFellesFormat(healthInformation)

            val receivedSykmeldingUtenHoveddiagose = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = generateSykmelding(
                    medisinskVurdering = MedisinskVurdering(hovedDiagnose = null, biDiagnoser = emptyList(), svangerskap = false, yrkesskade = false, yrkesskadeDato = null, annenFraversArsak = null),
                ),
                fellesformat = fellesformatMarshaller.toString(fellesformat),
            )

            mottattSykmeldingService.handleMessage(receivedSykmeldingUtenHoveddiagose, infotrygdOppdateringProducer, infotrygdSporringProducer, tssProducer, session, loggingMeta)

            coVerify(exactly = 0) { updateInfotrygdService.updateInfotrygd(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify { manuellClient.behandletAvManuell(any(), any()) }
            coVerify(exactly = 0) { behandlingsutfallService.sendRuleCheckValidationResult(any(), any(), any()) }
            coVerify {
                manuellBehandlingService.produceManualTaskAndSendValidationResults(
                    receivedSykmeldingUtenHoveddiagose,
                    match { it.status == Status.MANUAL_PROCESSING && it.ruleHits.any { ruleInfo -> ruleInfo.ruleName == "HOVEDDIAGNOSE_MANGLER" } },
                    false,
                    loggingMeta,
                )
            }
        }
        test("Går til manuell behandling hvis vi mangler behandler") {
            coEvery { norskHelsenettClient.finnBehandler(any(), any()) } returns null
            coEvery { fetchInfotrygdForesp(any(), any(), any(), any()) } returns getInfotrygdForespResponse()
            coEvery { fetchTssSamhandlerInfo(any(), any(), any()) } returns "1234"
            val healthInformation = createDefaultHealthInformation()
            val fellesformat = createFellesFormat(healthInformation)

            val receivedSykmelding = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = generateSykmelding(
                    medisinskVurdering = MedisinskVurdering(hovedDiagnose = null, biDiagnoser = emptyList(), svangerskap = false, yrkesskade = false, yrkesskadeDato = null, annenFraversArsak = null),
                ),
                fellesformat = fellesformatMarshaller.toString(fellesformat),
            )

            mottattSykmeldingService.handleMessage(receivedSykmelding, infotrygdOppdateringProducer, infotrygdSporringProducer, tssProducer, session, loggingMeta)

            coVerify(exactly = 0) { updateInfotrygdService.updateInfotrygd(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify { manuellClient.behandletAvManuell(any(), any()) }
            coVerify(exactly = 0) { behandlingsutfallService.sendRuleCheckValidationResult(any(), any(), any()) }
            coVerify {
                manuellBehandlingService.produceManualTaskAndSendValidationResults(
                    receivedSykmelding.copy(tssid = "1234"),
                    match { it.status == Status.MANUAL_PROCESSING && it.ruleHits.any { ruleInfo -> ruleInfo.ruleName == "BEHANDLER_NOT_IN_HPR" } },
                    loggingMeta,
                    any(),
                    "LE",
                    false,
                )
            }
        }
        test("Går til manuell behandling hvis infotrygd-regler slår ut") {
            coEvery { fetchInfotrygdForesp(any(), any(), any(), any()) } returns getInfotrygdForespResponse()
            coEvery { fetchTssSamhandlerInfo(any(), any(), any()) } returns null
            val healthInformation = createDefaultHealthInformation()
            val fellesformat = createFellesFormat(healthInformation)

            val receivedSykmelding = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = generateSykmelding(
                    medisinskVurdering = MedisinskVurdering(hovedDiagnose = null, biDiagnoser = emptyList(), svangerskap = false, yrkesskade = false, yrkesskadeDato = null, annenFraversArsak = null),
                ),
                fellesformat = fellesformatMarshaller.toString(fellesformat),
            )

            mottattSykmeldingService.handleMessage(receivedSykmelding, infotrygdOppdateringProducer, infotrygdSporringProducer, tssProducer, session, loggingMeta)

            coVerify(exactly = 0) { updateInfotrygdService.updateInfotrygd(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify { manuellClient.behandletAvManuell(any(), any()) }
            coVerify(exactly = 0) { behandlingsutfallService.sendRuleCheckValidationResult(any(), any(), any()) }
            coVerify {
                manuellBehandlingService.produceManualTaskAndSendValidationResults(
                    receivedSykmelding.copy(tssid = null),
                    match { it.status == Status.MANUAL_PROCESSING && it.ruleHits.any { ruleInfo -> ruleInfo.ruleName == "TSS_IDENT_MANGLER" } },
                    loggingMeta,
                    any(),
                    "LE",
                    false,
                )
            }
        }
    }
})

private fun getBehandler() =
    Behandler(
        listOf(
            Godkjenning(
                helsepersonellkategori = Kode(
                    aktiv = true,
                    oid = 0,
                    verdi = HelsepersonellKategori.LEGE.verdi,
                ),
                autorisasjon = Kode(aktiv = true, oid = 0, verdi = ""),
            ),
        ),
    )

private fun getInfotrygdForespResponse(): InfotrygdForesp =
    InfotrygdForesp().apply {
        sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
            sykmelding.add(
                TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        arbufoerFOM = LocalDate.of(2019, 1, 1)
                        arbufoerTOM = LocalDate.of(2019, 1, 1)
                    }
                },
            )
            status = StatusType().apply {
                kodeMelding = "00"
            }
        }
    }
