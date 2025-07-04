package no.nav.syfo.services

import io.opentelemetry.instrumentation.annotations.WithSpan
import jakarta.jms.MessageProducer
import jakarta.jms.Session
import java.io.StringReader
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Objects.isNull
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.sm2013.CV
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.InfotrygdForespAndHealthInformation
import no.nav.syfo.client.Behandler
import no.nav.syfo.client.Godkjenning
import no.nav.syfo.client.Kode
import no.nav.syfo.client.ManuellClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.erUtenlandskSykmelding
import no.nav.syfo.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.log
import no.nav.syfo.metrics.ANNEN_FRAVERS_ARSKAK_CHANGE_TO_A99_COUNTER
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.model.HelsepersonellKategori
import no.nav.syfo.model.sykmelding.Periode
import no.nav.syfo.model.sykmelding.ReceivedSykmelding
import no.nav.syfo.model.sykmelding.RuleInfo
import no.nav.syfo.model.sykmelding.Status
import no.nav.syfo.model.sykmelding.ValidationResult
import no.nav.syfo.rules.ruleCheck
import no.nav.syfo.services.updateinfotrygd.UpdateInfotrygdService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.fellesformatUnmarshaller
import org.slf4j.LoggerFactory

val sikkerlogg = LoggerFactory.getLogger("securelog")

class MottattSykmeldingService(
    private val updateInfotrygdService: UpdateInfotrygdService,
    private val finnNAVKontorService: FinnNAVKontorService,
    private val manuellClient: ManuellClient,
    private val manuellBehandlingService: ManuellBehandlingService,
    private val norskHelsenettClient: NorskHelsenettClient,
    private val cluster: String,
) {

    @WithSpan
    suspend fun handleMessage(
        receivedSykmelding: ReceivedSykmelding,
        infotrygdOppdateringProducer: MessageProducer,
        infotrygdSporringProducer: MessageProducer,
        session: Session,
        loggingMeta: LoggingMeta,
        tsmProcessingTarget: Boolean,
    ) {
        when (skalOppdatereInfotrygd(receivedSykmelding, cluster)) {
            true -> {
                log.info("Received a SM2013, {}", StructuredArguments.fields(loggingMeta))
                val requestLatency = REQUEST_TIME.startTimer()

                val fellesformat =
                    fellesformatUnmarshaller.unmarshal(
                        StringReader(receivedSykmelding.fellesformat),
                    ) as XMLEIFellesformat

                val healthInformation =
                    setHovedDiagnoseToA99IfhovedDiagnoseIsNullAndAnnenFraversArsakIsSet(
                        extractHelseOpplysningerArbeidsuforhet(fellesformat),
                    )
                val behandletAvManuell =
                    manuellClient.behandletAvManuell(
                        receivedSykmelding.sykmelding.id,
                        loggingMeta,
                    )

                val receivedSykmeldingCopyTssId =
                    if (receivedSykmelding.tssid.isNullOrBlank()) {
                        receivedSykmelding.copy(
                            tssid =
                                if (receivedSykmelding.erUtenlandskSykmelding()) {
                                    "0"
                                } else {
                                    receivedSykmelding.tssid
                                },
                        )
                    } else {
                        receivedSykmelding
                    }

                val validationResultForMottattSykmelding =
                    validerMottattSykmelding(healthInformation)
                if (validationResultForMottattSykmelding.status == Status.MANUAL_PROCESSING) {
                    log.info(
                        "Mottatt sykmelding kan ikke legges inn i infotrygd automatisk, oppretter oppgave, {}",
                        StructuredArguments.fields(loggingMeta),
                    )
                    manuellBehandlingService.produceManualTaskAndSendValidationResults(
                        receivedSykmelding = receivedSykmeldingCopyTssId,
                        validationResult = validationResultForMottattSykmelding,
                        behandletAvManuell = behandletAvManuell,
                        loggingMeta = loggingMeta,
                    )
                } else {
                    val localtKontor =
                        finnNAVKontorService.finnLokaltNavkontor(
                            receivedSykmeldingCopyTssId.personNrPasient,
                            loggingMeta,
                        )
                    val navKontorNr =
                        setNavKontorNr(
                            receivedSykmeldingCopyTssId,
                            localtKontor,
                        )
                    if (receivedSykmeldingCopyTssId.utenlandskSykmelding?.erAdresseUtland == true) {
                        log.info(
                            "Skal gjøre updateInfotrygd() og sjekker lokaltnavkontor der vi forventer 2101. NAV-kontor er: $navKontorNr, " +
                                "for sykmelding med sykmeldingsId: ${receivedSykmeldingCopyTssId.sykmelding.id}. \n der erAdresseUtland = ${receivedSykmeldingCopyTssId.utenlandskSykmelding.erAdresseUtland}",
                        )
                    }
                    val infotrygdForespResponse =
                        fetchInfotrygdForesp(
                            receivedSykmeldingCopyTssId,
                            infotrygdSporringProducer,
                            session,
                            healthInformation,
                            navKontorNr
                        )

                    val validationResult =
                        ruleCheck(
                            receivedSykmeldingCopyTssId,
                            infotrygdForespResponse,
                            loggingMeta,
                            RuleExecutionService(),
                        )

                    val helsepersonell = getHelsepersonell(receivedSykmeldingCopyTssId)
                    if (helsepersonell == null) {
                        handleBehandlerNotInHpr(
                            receivedSykmelding = receivedSykmeldingCopyTssId,
                            itfh =
                                InfotrygdForespAndHealthInformation(
                                    infotrygdForespResponse,
                                    healthInformation,
                                ),
                            behandletAvManuell = behandletAvManuell,
                            loggingMeta = loggingMeta,
                            tsmProcessingTarget = tsmProcessingTarget
                        )
                    } else {
                        val helsepersonellKategoriVerdi =
                            finnAktivHelsepersonellAutorisasjons(helsepersonell)
                        when (validationResult.status) {
                            in arrayOf(Status.MANUAL_PROCESSING) ->
                                manuellBehandlingService.produceManualTaskAndSendValidationResults(
                                    receivedSykmeldingCopyTssId,
                                    validationResult,
                                    loggingMeta,
                                    InfotrygdForespAndHealthInformation(
                                        infotrygdForespResponse,
                                        healthInformation,
                                    ),
                                    helsepersonellKategoriVerdi,
                                    behandletAvManuell,
                                )
                            else -> {

                                updateInfotrygdService.updateInfotrygd(
                                    producer = infotrygdOppdateringProducer,
                                    session = session,
                                    loggingMeta = loggingMeta,
                                    itfh =
                                        InfotrygdForespAndHealthInformation(
                                            infotrygdForespResponse,
                                            healthInformation,
                                        ),
                                    receivedSykmelding = receivedSykmeldingCopyTssId,
                                    behandlerKode = helsepersonellKategoriVerdi,
                                    navKontorNr = navKontorNr,
                                    validationResult = validationResult,
                                    behandletAvManuell = behandletAvManuell,
                                    tsmProcessingtarget = tsmProcessingTarget
                                )
                            }
                        }
                        log.info(
                            "Message(${StructuredArguments.fields(loggingMeta)}) got outcome {}, {}, processing took {}s",
                            StructuredArguments.keyValue("status", validationResult.status),
                            StructuredArguments.keyValue(
                                "ruleHits",
                                validationResult.ruleHits.joinToString(", ", "(", ")") {
                                    it.ruleName
                                },
                            ),
                        )
                    }
                }
                val currentRequestLatency = requestLatency.observeDuration()
                log.info(
                    "Message processing took {}s, for message {}",
                    currentRequestLatency.toString(),
                    StructuredArguments.fields(loggingMeta),
                )
            }
            else -> {
                log.info(
                    "Oppdaterer ikke infotrygd for sykmelding med merknad eller reisetilskudd, {}",
                    StructuredArguments.fields(loggingMeta),
                )
            }
        }
    }

    private fun setNavKontorNr(
        receivedSykmelding: ReceivedSykmelding,
        lokaltNavkontor: String,
    ): String {
        log.info(
            "folkeRegistertAdresseErBrakkeEllerTilsvarende: {}, og erAdresseUtland: {} \n med sykmeldingsId={}",
            receivedSykmelding.utenlandskSykmelding?.folkeRegistertAdresseErBrakkeEllerTilsvarende
                ?: "mangler folkeRegistertAdresseErBrakkeEllerTilsvarende",
            receivedSykmelding.utenlandskSykmelding?.erAdresseUtland ?: "mangler erAdresseUtland",
            receivedSykmelding.sykmelding.id,
        )

        if (
            receivedSykmelding.utenlandskSykmelding
                ?.folkeRegistertAdresseErBrakkeEllerTilsvarende == true ||
                receivedSykmelding.utenlandskSykmelding?.erAdresseUtland == true
        ) {
            log.info(
                "Sykmelding er utenlandsk med utenlandsk adresse, sender til 2101. SykmeldingsId: ${receivedSykmelding.sykmelding.id} . Navkontor: $lokaltNavkontor",
            )
            return "2101"
        } else {
            log.info(
                "Sender til lokalt NAV-kontor: $lokaltNavkontor. SykmeldingsId: ${receivedSykmelding.sykmelding.id} . receivedsykmelding.utenlandsksykmelding er null?: ${
                    isNull(
                        receivedSykmelding.utenlandskSykmelding,
                    )
                }",
            )
            return lokaltNavkontor
        }
    }

    private suspend fun getHelsepersonell(receivedSykmelding: ReceivedSykmelding): Behandler? {
        return if (erEgenmeldt(receivedSykmelding) || receivedSykmelding.erUtenlandskSykmelding()) {
            Behandler(
                listOf(
                    Godkjenning(
                        helsepersonellkategori =
                            Kode(
                                aktiv = true,
                                oid = 0,
                                verdi = HelsepersonellKategori.LEGE.verdi,
                            ),
                        autorisasjon = Kode(aktiv = true, oid = 0, verdi = ""),
                    ),
                ),
            )
        } else {
            norskHelsenettClient.finnBehandler(
                receivedSykmelding.personNrLege,
                receivedSykmelding.msgId,
            )
        }
    }

    private suspend fun handleBehandlerNotInHpr(
        receivedSykmelding: ReceivedSykmelding,
        itfh: InfotrygdForespAndHealthInformation,
        behandletAvManuell: Boolean,
        loggingMeta: LoggingMeta,
        tsmProcessingTarget: Boolean,
    ) {
        val validationResultBehandler =
            ValidationResult(
                status = Status.MANUAL_PROCESSING,
                ruleHits =
                    listOf(
                        RuleInfo(
                            ruleName = "BEHANDLER_NOT_IN_HPR",
                            messageForSender =
                                "Den som har skrevet sykmeldingen din har ikke autorisasjon til dette.",
                            messageForUser = "Behandler er ikke registert i HPR",
                            ruleStatus = Status.MANUAL_PROCESSING,
                        ),
                    ),
            )

        log.warn("Behandler er ikke registert i HPR")
        manuellBehandlingService.produceManualTaskAndSendValidationResults(
            receivedSykmelding,
            validationResultBehandler,
            loggingMeta,
            itfh,
            HelsepersonellKategori.LEGE.verdi,
            behandletAvManuell,
        )
    }

    private fun erEgenmeldt(receivedSykmelding: ReceivedSykmelding): Boolean =
        receivedSykmelding.sykmelding.avsenderSystem.navn == "Egenmeldt"
}

fun skalOppdatereInfotrygd(receivedSykmelding: ReceivedSykmelding, cluster: String): Boolean {
    if (cluster == "dev-gcp") {
        try {
            val fellesformat =
                fellesformatUnmarshaller.unmarshal(
                    StringReader(receivedSykmelding.fellesformat),
                ) as XMLEIFellesformat

            val healthInformation =
                setHovedDiagnoseToA99IfhovedDiagnoseIsNullAndAnnenFraversArsakIsSet(
                    extractHelseOpplysningerArbeidsuforhet(fellesformat),
                )
            if (
                healthInformation.medisinskVurdering.biDiagnoser?.diagnosekode?.firstOrNull()?.s ==
                    "icd10"
            ) {
                return false
            }
        } catch (exception: Exception) {
            log.error("Throwing exception $exception.message")
            return false
        }
    }
    // Vi skal ikke oppdatere Infotrygd hvis sykmeldingen inneholder en av de angitte merknadene
    val merknad =
        receivedSykmelding.merknader?.none {
            it.type == "UGYLDIG_TILBAKEDATERING" ||
                it.type == "TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER" ||
                it.type == "TILBAKEDATERT_PAPIRSYKMELDING" ||
                it.type == "UNDER_BEHANDLING"
        }
            ?: true

    // Vi skal ikke oppdatere infotrygd hvis sykmeldingen inneholder reisetilskudd
    val reisetilskudd =
        receivedSykmelding.sykmelding.perioder.none {
            it.reisetilskudd || (it.gradert?.reisetilskudd == true)
        }

    return merknad && reisetilskudd
}

fun setHovedDiagnoseToA99IfhovedDiagnoseIsNullAndAnnenFraversArsakIsSet(
    helseOpplysningerArbeidsuforhet: HelseOpplysningerArbeidsuforhet,
): HelseOpplysningerArbeidsuforhet {
    if (
        helseOpplysningerArbeidsuforhet.medisinskVurdering.hovedDiagnose == null &&
            helseOpplysningerArbeidsuforhet.medisinskVurdering.annenFraversArsak != null
    ) {
        helseOpplysningerArbeidsuforhet.medisinskVurdering.hovedDiagnose =
            HelseOpplysningerArbeidsuforhet.MedisinskVurdering.HovedDiagnose().apply {
                diagnosekode =
                    CV().apply {
                        dn = "Helseproblem/sykdom IKA"
                        s = "2.16.578.1.12.4.1.1.7170"
                        v = "A99"
                    }
            }
        ANNEN_FRAVERS_ARSKAK_CHANGE_TO_A99_COUNTER.inc()
        return helseOpplysningerArbeidsuforhet
    } else {
        return helseOpplysningerArbeidsuforhet
    }
}

fun validerMottattSykmelding(
    helseOpplysningerArbeidsuforhet: HelseOpplysningerArbeidsuforhet
): ValidationResult {
    return if (helseOpplysningerArbeidsuforhet.medisinskVurdering.hovedDiagnose == null) {
        log.warn("Sykmelding mangler hoveddiagnose")
        ValidationResult(
            Status.MANUAL_PROCESSING,
            listOf(
                RuleInfo(
                    "HOVEDDIAGNOSE_MANGLER",
                    "Sykmeldingen inneholder ingen hoveddiagnose, vi kan ikke automatisk oppdatere Infotrygd",
                    "Sykmeldingen inneholder ingen hoveddiagnose, vi kan ikke automatisk oppdatere Infotrygd",
                    Status.MANUAL_PROCESSING,
                ),
            ),
        )
    } else {
        ValidationResult(Status.OK, emptyList())
    }
}

fun List<Periode>.sortedFOMDate(): List<LocalDate> = map { it.fom }.sorted()

fun List<Periode>.sortedTOMDate(): List<LocalDate> = map { it.tom }.sorted()

fun ClosedRange<LocalDate>.daysBetween(): Long = ChronoUnit.DAYS.between(start, endInclusive)
