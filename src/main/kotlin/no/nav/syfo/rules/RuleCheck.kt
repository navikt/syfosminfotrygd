package no.nav.syfo.rules

import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.syfo.log
import no.nav.syfo.metrics.RULE_HIT_COUNTER
import no.nav.syfo.metrics.RULE_HIT_STATUS_COUNTER
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.RuleMetadata
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.util.LoggingMeta

fun ruleCheck(
    receivedSykmelding: ReceivedSykmelding,
    infotrygdForespResponse: InfotrygdForesp,
    loggingMeta: LoggingMeta
): ValidationResult {

    log.info("Going through rules {}", StructuredArguments.fields(loggingMeta))

    val results = listOf(
        ValidationRuleChain(receivedSykmelding.sykmelding, infotrygdForespResponse).executeRules(),
        TssRuleChain(
            RuleMetadata(
                receivedDate = receivedSykmelding.mottattDato,
                signatureDate = receivedSykmelding.sykmelding.signaturDato,
                patientPersonNumber = receivedSykmelding.personNrPasient,
                rulesetVersion = receivedSykmelding.rulesetVersion,
                legekontorOrgnr = receivedSykmelding.legekontorOrgNr,
                tssid = receivedSykmelding.tssid
            )
        ).executeRules(),
    ).flatten().filter { it.result }

    logRuleResultMetrics(results)

    log.info(
        "Rules hit {}, $loggingMeta", results.map { ruleResult -> ruleResult.rule.name },
        StructuredArguments.fields(loggingMeta)
    )

    val validationResult = validationResult(results)
    RULE_HIT_STATUS_COUNTER.labels(validationResult.status.name).inc()
    return validationResult
}

fun validationResult(results: List<RuleResult<*>>): ValidationResult =
    ValidationResult(
        status = results
            .map { it.rule.status }.let {
                it.firstOrNull { status -> status == Status.MANUAL_PROCESSING }
                    ?: Status.OK
            },
        ruleHits = results.map { ruleReuslt ->
            RuleInfo(
                ruleReuslt.rule.name,
                ruleReuslt.rule.messageForUser,
                ruleReuslt.rule.messageForSender,
                ruleReuslt.rule.status
            )
        }
    )

private fun logRuleResultMetrics(result: List<RuleResult<*>>) {
    result
        .forEach {
            RULE_HIT_COUNTER.labels(it.rule.name).inc()
        }
}
