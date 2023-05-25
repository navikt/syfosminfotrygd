package no.nav.syfo.services

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
import no.nav.syfo.client.SyketilfelleClient
import no.nav.syfo.erUtenlandskSykmelding
import no.nav.syfo.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.log
import no.nav.syfo.metrics.ANNEN_FRAVERS_ARSKAK_CHANGE_TO_A99_COUNTER
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.model.HelsepersonellKategori
import no.nav.syfo.model.Periode
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.rules.ruleCheck
import no.nav.syfo.services.updateinfotrygd.UpdateInfotrygdService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.wrapExceptions
import org.slf4j.LoggerFactory
import java.io.StringReader
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.jms.MessageProducer
import javax.jms.Session

val sikkerlogg = LoggerFactory.getLogger("securelog")
class MottattSykmeldingService(
    private val updateInfotrygdService: UpdateInfotrygdService,
    private val finnNAVKontorService: FinnNAVKontorService,
    private val manuellClient: ManuellClient,
    private val manuellBehandlingService: ManuellBehandlingService,
    private val behandlingsutfallService: BehandlingsutfallService,
    private val norskHelsenettClient: NorskHelsenettClient,
    private val syketilfelleClient: SyketilfelleClient,
) {
    suspend fun handleMessage(
        receivedSykmelding: ReceivedSykmelding,
        infotrygdOppdateringProducer: MessageProducer,
        infotrygdSporringProducer: MessageProducer,
        session: Session,
        loggingMeta: LoggingMeta,
    ) {
        wrapExceptions(loggingMeta) {
            when (skalOppdatereInfotrygd(receivedSykmelding)) {
                true -> {
                    log.info("Received a SM2013, {}", StructuredArguments.fields(loggingMeta))
                    val requestLatency = REQUEST_TIME.startTimer()

                    val fellesformat =
                        fellesformatUnmarshaller.unmarshal(StringReader(receivedSykmelding.fellesformat)) as XMLEIFellesformat

                    val healthInformation = setHovedDiagnoseToA99IfhovedDiagnoseIsNullAndAnnenFraversArsakIsSet(
                        extractHelseOpplysningerArbeidsuforhet(fellesformat),
                    )
                    val behandletAvManuell =
                        manuellClient.behandletAvManuell(receivedSykmelding.sykmelding.id, loggingMeta)

                    val validationResultForMottattSykmelding = validerMottattSykmelding(healthInformation)
                    if (validationResultForMottattSykmelding.status == Status.MANUAL_PROCESSING) {
                        log.info(
                            "Mottatt sykmelding kan ikke legges inn i infotrygd automatisk, oppretter oppgave, {}",
                            StructuredArguments.fields(loggingMeta),
                        )
                        manuellBehandlingService.produceManualTaskAndSendValidationResults(
                            receivedSykmelding = receivedSykmelding,
                            validationResult = validationResultForMottattSykmelding,
                            behandletAvManuell = behandletAvManuell,
                            loggingMeta = loggingMeta,
                        )
                    } else {
                        val infotrygdForespResponse = fetchInfotrygdForesp(
                            receivedSykmelding,
                            infotrygdSporringProducer,
                            session,
                            healthInformation,
                        )

                        sikkerlogg.info(
                            "infotrygdForespResponse: ${objectMapper.writeValueAsString(infotrygdForespResponse)}" +
                                " for {}",
                            StructuredArguments.fields(loggingMeta),
                        )

                        val validationResult =
                            ruleCheck(receivedSykmelding, infotrygdForespResponse, loggingMeta, RuleExecutionService())

                        val lokaltNavkontor =
                            finnNAVKontorService.finnLokaltNavkontor(receivedSykmelding.personNrPasient, loggingMeta)

                        val syketilfelleStartdato = syketilfelleClient.finnStartdatoForSammenhengendeSyketilfelle(
                            receivedSykmelding.personNrPasient,
                            receivedSykmelding.sykmelding.perioder,
                            loggingMeta,
                        )

                        val helsepersonell = getHelsepersonell(receivedSykmelding)
                        if (helsepersonell == null) {
                            handleBehandlerNotInHpr(
                                receivedSykmelding = receivedSykmelding,
                                itfh = InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation),
                                behandletAvManuell = behandletAvManuell,
                                loggingMeta = loggingMeta,
                            )
                        } else {
                            val helsepersonellKategoriVerdi = finnAktivHelsepersonellAutorisasjons(helsepersonell)
                            when (validationResult.status) {
                                in arrayOf(Status.MANUAL_PROCESSING) ->
                                    manuellBehandlingService.produceManualTaskAndSendValidationResults(
                                        receivedSykmelding,
                                        validationResult,
                                        loggingMeta,
                                        InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation),
                                        helsepersonellKategoriVerdi,
                                        behandletAvManuell,
                                    )
                                else -> updateInfotrygdService.updateInfotrygd(
                                    producer = infotrygdOppdateringProducer,
                                    session = session,
                                    loggingMeta = loggingMeta,
                                    itfh = InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation),
                                    receivedSykmelding = receivedSykmelding,
                                    behandlerKode = helsepersonellKategoriVerdi,
                                    navKontorNr = setNavKontorNr(receivedSykmelding, syketilfelleStartdato, lokaltNavkontor),
                                    validationResult = validationResult,
                                    behandletAvManuell = behandletAvManuell,
                                )
                            }
                            log.info(
                                "Message(${StructuredArguments.fields(loggingMeta)}) got outcome {}, {}, processing took {}s",
                                StructuredArguments.keyValue("status", validationResult.status),
                                StructuredArguments.keyValue("ruleHits", validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName }),
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
                    handleSkalIkkeOppdatereInfotrygd(receivedSykmelding, loggingMeta)
                }
            }
        }
    }

    private fun setNavKontorNr(
        receivedSykmelding: ReceivedSykmelding,
        syketilfelleStartdato: LocalDate?,
        lokaltNavkontor: String,
    ): String {
        return if (receivedSykmelding.erUtenlandskSykmelding() &&
            sickLeavePeriodOver12Weeks(receivedSykmelding, syketilfelleStartdato) ||
            (
                receivedSykmelding.utenlandskSykmelding != null &&
                    receivedSykmelding.utenlandskSykmelding!!.folkeRegistertAdresseErBrakkeEllerTilsvarende
                )
        ) {
            "0393"
        } else {
            lokaltNavkontor
        }
    }

    private fun sickLeavePeriodOver12Weeks(
        receivedSykmelding: ReceivedSykmelding,
        syketilfelleStartdato: LocalDate?,
    ): Boolean {
        val forsteFomDato = receivedSykmelding.sykmelding.perioder.sortedFOMDate().first()
        val sisteTomDato = receivedSykmelding.sykmelding.perioder.sortedTOMDate().last()

        return (
            (forsteFomDato..sisteTomDato).daysBetween() > 84 ||
                (
                    syketilfelleStartdato != null &&
                        (syketilfelleStartdato..sisteTomDato).daysBetween() > 84
                    )
            )
    }

    private suspend fun getHelsepersonell(receivedSykmelding: ReceivedSykmelding): Behandler? {
        return if (erEgenmeldt(receivedSykmelding) || receivedSykmelding.erUtenlandskSykmelding()) {
            Behandler(listOf(Godkjenning(helsepersonellkategori = Kode(aktiv = true, oid = 0, verdi = HelsepersonellKategori.LEGE.verdi), autorisasjon = Kode(aktiv = true, oid = 0, verdi = ""))))
        } else {
            norskHelsenettClient.finnBehandler(receivedSykmelding.personNrLege, receivedSykmelding.msgId)
        }
    }

    private fun handleBehandlerNotInHpr(
        receivedSykmelding: ReceivedSykmelding,
        itfh: InfotrygdForespAndHealthInformation,
        behandletAvManuell: Boolean,
        loggingMeta: LoggingMeta,
    ) {
        val validationResultBehandler = ValidationResult(
            status = Status.MANUAL_PROCESSING,
            ruleHits = listOf(
                RuleInfo(
                    ruleName = "BEHANDLER_NOT_IN_HPR",
                    messageForSender = "Den som har skrevet sykmeldingen din har ikke autorisasjon til dette.",
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

    private fun handleSkalIkkeOppdatereInfotrygd(
        receivedSykmelding: ReceivedSykmelding,
        loggingMeta: LoggingMeta,
    ) {
        val validationResult =
            if (receivedSykmelding.merknader?.any { it.type == "UNDER_BEHANDLING" } == true) {
                ValidationResult(
                    Status.OK,
                    listOf(
                        RuleInfo(
                            "UNDER_BEHANDLING",
                            "Sykmeldingen er til manuell behandling",
                            "Sykmeldingen er til manuell behandling",
                            Status.OK,
                        ),
                    ),
                )
            } else {
                ValidationResult(Status.OK, emptyList())
            }
        behandlingsutfallService.sendRuleCheckValidationResult(
            receivedSykmelding.sykmelding.id,
            validationResult,
            loggingMeta,
        )
    }

    private fun erEgenmeldt(receivedSykmelding: ReceivedSykmelding): Boolean =
        receivedSykmelding.sykmelding.avsenderSystem.navn == "Egenmeldt"
}

fun skalOppdatereInfotrygd(receivedSykmelding: ReceivedSykmelding): Boolean {
    // Vi skal ikke oppdatere Infotrygd hvis sykmeldingen inneholder en av de angitte merknadene
    val merknad = receivedSykmelding.merknader?.none {
        it.type == "UGYLDIG_TILBAKEDATERING" ||
            it.type == "TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER" ||
            it.type == "TILBAKEDATERT_PAPIRSYKMELDING" ||
            it.type == "UNDER_BEHANDLING"
    } ?: true

    // Vi skal ikke oppdatere infotrygd hvis sykmeldingen inneholder reisetilskudd
    val reisetilskudd = receivedSykmelding.sykmelding.perioder.none {
        it.reisetilskudd || (it.gradert?.reisetilskudd == true)
    }

    return merknad && reisetilskudd
}

fun setHovedDiagnoseToA99IfhovedDiagnoseIsNullAndAnnenFraversArsakIsSet(
    helseOpplysningerArbeidsuforhet: HelseOpplysningerArbeidsuforhet,
): HelseOpplysningerArbeidsuforhet {
    if (helseOpplysningerArbeidsuforhet.medisinskVurdering.hovedDiagnose == null &&
        helseOpplysningerArbeidsuforhet.medisinskVurdering.annenFraversArsak != null
    ) {
        helseOpplysningerArbeidsuforhet.medisinskVurdering.hovedDiagnose =
            HelseOpplysningerArbeidsuforhet.MedisinskVurdering.HovedDiagnose().apply {
                diagnosekode = CV().apply {
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

fun validerMottattSykmelding(helseOpplysningerArbeidsuforhet: HelseOpplysningerArbeidsuforhet): ValidationResult {
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

fun List<Periode>.sortedFOMDate(): List<LocalDate> =
    map { it.fom }.sorted()

fun List<Periode>.sortedTOMDate(): List<LocalDate> =
    map { it.tom }.sorted()

fun ClosedRange<LocalDate>.daysBetween(): Long = ChronoUnit.DAYS.between(start, endInclusive)
