package no.nav.syfo.services

import io.opentelemetry.instrumentation.annotations.WithSpan
import io.valkey.exceptions.JedisConnectionException
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.syfo.InfotrygdForespAndHealthInformation
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.daysBetween
import no.nav.syfo.erUtenlandskSykmelding
import no.nav.syfo.log
import no.nav.syfo.metrics.OVERLAPPENDE_PERIODER_IKKE_OPPRETT_OPPGAVE
import no.nav.syfo.metrics.OVERLAPPER_PERIODER_COUNTER
import no.nav.syfo.model.sykmelding.ReceivedSykmelding
import no.nav.syfo.model.sykmelding.RuleInfo
import no.nav.syfo.model.sykmelding.Status
import no.nav.syfo.model.sykmelding.ValidationResult
import no.nav.syfo.rules.validation.sortedPeriodeFOMDate
import no.nav.syfo.rules.validation.sortedPeriodeTOMDate
import no.nav.syfo.services.updateinfotrygd.INFOTRYGD
import no.nav.syfo.services.updateinfotrygd.createInfotrygdBlokk
import no.nav.syfo.services.updateinfotrygd.findArbeidsKategori
import no.nav.syfo.sortedFOMDate
import no.nav.syfo.util.LoggingMeta

class ManuellBehandlingService(
    private val behandlingsutfallService: BehandlingsutfallService,
    private val valkeyService: ValkeyService,
    private val oppgaveService: OppgaveService,
    private val applicationState: ApplicationState,
    private val sykmeldingService: SykmeldingService,
) {
    @WithSpan
    fun produceManualTaskAndSendValidationResults(
        receivedSykmelding: ReceivedSykmelding,
        validationResult: ValidationResult,
        behandletAvManuell: Boolean,
        loggingMeta: LoggingMeta,
    ) {
        behandlingsutfallService.sendRuleCheckValidationResult(
            receivedSykmelding.sykmelding.id,
            validationResult,
            loggingMeta,
        )
        oppgaveService.opprettOppgave(
            receivedSykmelding,
            validationResult,
            behandletAvManuell,
            loggingMeta
        )
    }

    @WithSpan(value = "produceManualTaskAndSendValidationResultsWithHelsepersonellKategori")
    suspend fun produceManualTaskAndSendValidationResults(
        receivedSykmelding: ReceivedSykmelding,
        validationResult: ValidationResult,
        loggingMeta: LoggingMeta,
        itfh: InfotrygdForespAndHealthInformation,
        helsepersonellKategoriVerdi: String,
        behandletAvManuell: Boolean,
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
                        identDato = forsteFravaersDag,
                        behandletAvManuell = behandletAvManuell,
                        utenlandskSykmelding = receivedSykmelding.erUtenlandskSykmelding(),
                        operasjonstypeKode = 1,
                    ),
                )

            val duplikatInfotrygdOppdatering = valkeyService.erIRedis(sha256String)

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
                    itfh.infotrygdForesp
                )
            skalIkkeProdusereManuellOppgave(receivedSykmelding, validationResult)
            when {
                duplikatInfotrygdOppdatering -> {
                    behandlingsutfallService.sendRuleCheckValidationResult(
                        receivedSykmelding.sykmelding.id,
                        ValidationResult(
                            Status.OK,
                            emptyList(),
                        ),
                        loggingMeta,
                    )
                    log.warn(
                        "Melding er infotrygd duplikat, ikke opprett manuelloppgave {}",
                        StructuredArguments.fields(loggingMeta),
                    )
                }
                skalIkkeOppdatereInfotrygd -> {
                    OVERLAPPENDE_PERIODER_IKKE_OPPRETT_OPPGAVE.inc()
                    behandlingsutfallService.sendRuleCheckValidationResult(
                        receivedSykmelding.sykmelding.id,
                        ValidationResult(
                            Status.OK,
                            emptyList(),
                        ),
                        loggingMeta,
                    )
                    log.warn(
                        "Sykmelding overlapper, trenger ikke å opprette manuell oppgave for {}",
                        StructuredArguments.fields(loggingMeta)
                    )
                }
                else -> {
                    behandlingsutfallService.sendRuleCheckValidationResult(
                        receivedSykmelding.sykmelding.id,
                        validationResult,
                        loggingMeta
                    )
                    oppgaveService.opprettOppgave(
                        receivedSykmelding,
                        validationResult,
                        behandletAvManuell,
                        loggingMeta
                    )
                    valkeyService.oppdaterValkey(
                        sha256String,
                        sha256String,
                        TimeUnit.DAYS.toSeconds(60).toInt(),
                        loggingMeta
                    )
                }
            }
        } catch (connectionException: JedisConnectionException) {
            log.error(
                "Fikk ikkje opprettet kontakt med redis, kaster exception",
                connectionException
            )
            throw connectionException
        }
    }

    private suspend fun skalIkkeOppdatereInfotrygdNewCheck(
        receivedSykmelding: ReceivedSykmelding,
        validationResult: ValidationResult,
        infotrygdForesp: InfotrygdForesp,
    ): Boolean {
        val delvisOverlappendeSykmeldingRule =
            validationResult.ruleHits.isNotEmpty() &&
                validationResult.ruleHits.any {
                    (it.ruleName ==
                        "PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE")
                }
        if (delvisOverlappendeSykmeldingRule) {
            return harOverlappendePerioder(receivedSykmelding, infotrygdForesp)
        }
        return false
    }

    suspend fun harOverlappendePerioder(
        receivedSykmelding: ReceivedSykmelding,
        infotrygdForesp: InfotrygdForesp
    ): Boolean {
        val overlapperFraRegisteret =
            sykmeldingService.hasOverlappingPeriodsFromRegister(receivedSykmelding)
        if (overlapperFraRegisteret) {
            OVERLAPPER_PERIODER_COUNTER.labels("smregister").inc()
        }
        val overlapperFraInfotrygd =
            sykmeldingService.hasOverlappingPeriodsFromInfotrygd(
                receivedSykmelding,
                infotrygdForesp
            )
        if (overlapperFraInfotrygd) {
            OVERLAPPER_PERIODER_COUNTER.labels("infotrygd").inc()
        }
        log.info(
            "overlappendet perioder fra smregister: $overlapperFraRegisteret, infotrygd: $overlapperFraInfotrygd"
        )
        return overlapperFraRegisteret && overlapperFraInfotrygd
    }
}

fun skalIkkeProdusereManuellOppgave(
    receivedSykmelding: ReceivedSykmelding,
    validationResult: ValidationResult,
): Boolean {
    val delvisOverlappendeSykmeldingRule =
        validationResult.ruleHits.isNotEmpty() &&
            validationResult.ruleHits.any {
                (it.ruleName ==
                    "PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE")
            } &&
            receivedSykmelding.sykmelding.perioder.sortedPeriodeFOMDate().lastOrNull() != null &&
            receivedSykmelding.sykmelding.perioder.sortedPeriodeTOMDate().lastOrNull() != null &&
            (receivedSykmelding.sykmelding.perioder
                    .sortedPeriodeFOMDate()
                    .last()..receivedSykmelding.sykmelding.perioder.sortedPeriodeTOMDate().last())
                .daysBetween() <= 3

    if (delvisOverlappendeSykmeldingRule) {
        OVERLAPPER_PERIODER_COUNTER.labels("old").inc()
    }
    return delvisOverlappendeSykmeldingRule
}

fun errorFromInfotrygd(rules: List<RuleInfo>): Boolean =
    rules.any { ruleInfo ->
        ruleInfo.ruleName == "ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING" ||
            ruleInfo.ruleName == "ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING" ||
            ruleInfo.ruleName == "ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING" ||
            ruleInfo.ruleName == "ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING" ||
            ruleInfo.ruleName == "ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING"
    }
