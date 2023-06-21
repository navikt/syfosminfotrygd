package no.nav.syfo.rules

import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.syfo.log
import no.nav.syfo.metrics.RULE_NODE_RULE_HIT_COUNTER
import no.nav.syfo.metrics.RULE_NODE_RULE_PATH_COUNTER
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.RuleMetadata
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.rules.common.RuleResult
import no.nav.syfo.rules.dsl.TreeOutput
import no.nav.syfo.rules.dsl.printRulePath
import no.nav.syfo.services.RuleExecutionService
import no.nav.syfo.util.LoggingMeta

fun ruleCheck(
    receivedSykmelding: ReceivedSykmelding,
    infotrygdForespResponse: InfotrygdForesp,
    loggingMeta: LoggingMeta,
    ruleExecutionService: RuleExecutionService,
): ValidationResult {
    log.info("Going through rules {}", StructuredArguments.fields(loggingMeta))

    val ruleMetadata =
        RuleMetadata(
            receivedDate = receivedSykmelding.mottattDato,
            signatureDate = receivedSykmelding.sykmelding.signaturDato,
            patientPersonNumber = receivedSykmelding.personNrPasient,
            rulesetVersion = receivedSykmelding.rulesetVersion,
            legekontorOrgnr = receivedSykmelding.legekontorOrgNr,
            tssid = receivedSykmelding.tssid,
            infotrygdForesp = infotrygdForespResponse,
        )

    val result = ruleExecutionService.runRules(receivedSykmelding.sykmelding, ruleMetadata)
    result.forEach {
        RULE_NODE_RULE_PATH_COUNTER.labels(
                it.first.printRulePath(),
            )
            .inc()
    }

    val validationResult = validationResult(result.map { it.first })
    RULE_NODE_RULE_HIT_COUNTER.labels(
            validationResult.status.name,
            validationResult.ruleHits.firstOrNull()?.ruleName ?: validationResult.status.name,
        )
        .inc()
    return validationResult
}

fun validationResult(results: List<TreeOutput<out Enum<*>, RuleResult>>): ValidationResult =
    ValidationResult(
        status =
            results
                .map { result -> result.treeResult.status }
                .let {
                    it.firstOrNull { status -> status == Status.INVALID }
                        ?: it.firstOrNull { status -> status == Status.MANUAL_PROCESSING }
                            ?: Status.OK
                },
        ruleHits =
            results
                .mapNotNull { it.treeResult.ruleHit }
                .map { result ->
                    RuleInfo(
                        result.rule,
                        result.messageForSender,
                        result.messageForUser,
                        result.status,
                    )
                },
    )
