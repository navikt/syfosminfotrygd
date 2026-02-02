package no.nav.syfo.services

import io.opentelemetry.instrumentation.annotations.WithSpan
import io.valkey.exceptions.JedisConnectionException
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.syfo.InfotrygdForespAndHealthInformation
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.erUtenlandskSykmelding
import no.nav.syfo.log
import no.nav.syfo.metrics.OVERLAPPENDE_PERIODER_IKKE_OPPRETT_OPPGAVE
import no.nav.syfo.metrics.OVERLAPPENDE_PERIODE_SKIP_OPPGAVE
import no.nav.syfo.metrics.OVERLAPPER_PERIODER_COUNTER
import no.nav.syfo.model.sykmelding.ReceivedSykmelding
import no.nav.syfo.model.sykmelding.RuleInfo
import no.nav.syfo.model.sykmelding.ValidationResult
import no.nav.syfo.services.updateinfotrygd.INFOTRYGD
import no.nav.syfo.services.updateinfotrygd.Operasjonstype
import no.nav.syfo.services.updateinfotrygd.createInfotrygdBlokk
import no.nav.syfo.services.updateinfotrygd.findArbeidsKategori
import no.nav.syfo.services.updateinfotrygd.getInfotrygdPerioder
import no.nav.syfo.sortedFOMDate
import no.nav.syfo.util.LoggingMeta

class ManuellBehandlingService(
    private val valkeyService: ValkeyService,
    private val oppgaveService: OppgaveService,
    private val applicationState: ApplicationState,
) {
    @WithSpan
    fun produceManualTaskAndSendValidationResults(
        receivedSykmelding: ReceivedSykmelding,
        validationResult: ValidationResult,
        behandletAvManuell: Boolean,
        loggingMeta: LoggingMeta,
    ) {
        oppgaveService.opprettOppgave(
            receivedSykmelding,
            validationResult,
            behandletAvManuell,
            loggingMeta
        )
    }

    @WithSpan(value = "produceManualTaskAndSendValidationResultsWithHelsepersonellKategori")
    fun produceManualTaskAndSendValidationResults(
        receivedSykmelding: ReceivedSykmelding,
        validationResult: ValidationResult,
        loggingMeta: LoggingMeta,
        itfh: InfotrygdForespAndHealthInformation,
        helsepersonellKategoriVerdi: String,
        behandletAvManuell: Boolean,
        operasjonstypeAndFom: Pair<Operasjonstype, LocalDate>,
        skipDuplicationCheck: Boolean = false,
    ) {
        try {
            val perioder = itfh.healthInformation.aktivitet.periode.sortedBy { it.periodeFOMDato }
            val forsteFravaersDag = itfh.healthInformation.aktivitet.periode.sortedFOMDate().first()
            val tssid =
                if (!receivedSykmelding.tssid.isNullOrBlank()) {
                    receivedSykmelding.tssid
                } else {
                    "0"
                }

            if (itfh.infotrygdForesp.hovedDiagnosekode == null) {
                itfh.infotrygdForesp.diagnosekodeOK = null
            }

            val sha256String =
                sha256hashstring(
                    createInfotrygdBlokk(
                        itfh = itfh,
                        periode = perioder.first(),
                        personNrPasient = receivedSykmelding.personNrPasient,
                        signaturDato = LocalDate.of(2019, 1, 1),
                        helsepersonellKategoriVerdi = helsepersonellKategoriVerdi,
                        tssid = tssid,
                        loggingMeta = loggingMeta,
                        navKontorNr = "",
                        navnArbeidsgiver =
                            findArbeidsKategori(
                                itfh.healthInformation.arbeidsgiver?.navnArbeidsgiver
                            ),
                        behandletAvManuell = behandletAvManuell,
                        utenlandskSykmelding = receivedSykmelding.erUtenlandskSykmelding(),
                        operasjonstypeAndFom = operasjonstypeAndFom.copy(first = Operasjonstype.NY),
                    ),
                )
            val duplikatInfotrygdOppdatering =
                if (skipDuplicationCheck) {
                    false
                } else {
                    valkeyService.erIValkey(sha256String)
                }

            if (errorFromInfotrygd(validationResult.ruleHits)) {
                valkeyService.oppdaterAntallErrorIInfotrygd(
                    INFOTRYGD,
                    "1",
                    TimeUnit.MINUTES.toSeconds(1).toInt(),
                    loggingMeta
                )
            }

            val antallErrorFraInfotrygd =
                valkeyService.antallErrorIInfotrygd(INFOTRYGD, loggingMeta)

            if (antallErrorFraInfotrygd > 50) {
                log.error("Setter applicationState.ready til false")
                applicationState.ready = false
            }
            val skalIkkeOppdatereInfotrygd =
                skalIkkeOppdatereInfotrygdNewCheck(
                    receivedSykmelding,
                    validationResult,
                    itfh.infotrygdForesp,
                    operasjonstypeAndFom,
                )

            when {
                duplikatInfotrygdOppdatering -> {
                    log.warn(
                        "Melding er infotrygd duplikat, ikke opprett manuelloppgave {}",
                        StructuredArguments.fields(loggingMeta),
                    )
                }
                skalIkkeOppdatereInfotrygd -> {
                    OVERLAPPENDE_PERIODER_IKKE_OPPRETT_OPPGAVE.inc()
                    log.info(
                        "Sykmelding overlapper, trenger ikke å opprette manuell oppgave for {}",
                        StructuredArguments.fields(loggingMeta)
                    )
                }
                else -> {
                    oppgaveService.opprettOppgave(
                        receivedSykmelding,
                        validationResult,
                        behandletAvManuell,
                        loggingMeta
                    )
                    if (!skipDuplicationCheck) {
                        valkeyService.oppdaterValkey(
                            sha256String,
                            sha256String,
                            TimeUnit.DAYS.toSeconds(60).toInt(),
                            loggingMeta
                        )
                    }
                }
            }
        } catch (connectionException: JedisConnectionException) {
            log.error(
                "Fikk ikkje opprettet kontakt med valkey, kaster exception",
                connectionException
            )
            throw connectionException
        }
    }

    private fun skalIkkeOppdatereInfotrygdNewCheck(
        receivedSykmelding: ReceivedSykmelding,
        validationResult: ValidationResult,
        infotrygdForesp: InfotrygdForesp,
        operasjonstypeAndFom: Pair<Operasjonstype, LocalDate>,
    ): Boolean {
        val delvisOverlappendeSykmeldingRule =
            validationResult.ruleHits.isNotEmpty() &&
                validationResult.ruleHits.any {
                    (it.ruleName ==
                        "PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE")
                }
        if (delvisOverlappendeSykmeldingRule) {
            val overlappendePerioder = harOverlappendePerioder(receivedSykmelding, infotrygdForesp)
            val isUendretOperasjonstype = operasjonstypeAndFom.first == Operasjonstype.UENDRET
            if (overlappendePerioder) {
                OVERLAPPENDE_PERIODE_SKIP_OPPGAVE.labels("old").inc()
            }
            if (isUendretOperasjonstype) {
                OVERLAPPENDE_PERIODE_SKIP_OPPGAVE.labels("new").inc()
            }
            if (overlappendePerioder != isUendretOperasjonstype) {
                log.info(
                    "Overlappende perioder is different from isUendretOperasjonstype for sykmeldingid: ${receivedSykmelding.sykmelding.id}"
                )
            }
            return isUendretOperasjonstype || overlappendePerioder
        }
        return false
    }

    fun harOverlappendePerioder(
        receivedSykmelding: ReceivedSykmelding,
        infotrygdForesp: InfotrygdForesp,
    ): Boolean {
        val infotrygdSykmelding = infotrygdForesp.getInfotrygdPerioder()
        val overlapperFraInfotrygd =
            hasOverlappingPeriodsFromInfotrygd(
                receivedSykmelding.sykmelding.perioder.firstFom(),
                receivedSykmelding.sykmelding.perioder.lastTom(),
                infotrygdSykmelding.filter {
                    it.periode.arbufoerFOM != null && it.periode.arbufoerTOM != null
                }
            )
        if (overlapperFraInfotrygd) {
            OVERLAPPER_PERIODER_COUNTER.labels("infotrygd").inc()
        }
        log.info("overlappendet perioder fra infotrygd: $overlapperFraInfotrygd")
        return overlapperFraInfotrygd
    }
}

fun errorFromInfotrygd(rules: List<RuleInfo>): Boolean =
    rules.any { ruleInfo ->
        ruleInfo.ruleName == "ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING" ||
            ruleInfo.ruleName == "ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING" ||
            ruleInfo.ruleName == "ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING" ||
            ruleInfo.ruleName == "ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING" ||
            ruleInfo.ruleName == "ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING"
    }
