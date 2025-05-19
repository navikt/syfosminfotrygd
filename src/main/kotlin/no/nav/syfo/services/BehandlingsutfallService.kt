package no.nav.syfo.services

import io.opentelemetry.instrumentation.annotations.WithSpan
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.PROCESSING_TARGET_HEADER
import no.nav.syfo.TSM_PROCESSING_TARGET_VALUE
import no.nav.syfo.log
import no.nav.syfo.model.sykmelding.ValidationResult
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class BehandlingsutfallService(
    private val kafkaAivenProducerBehandlingsutfall: KafkaProducer<String, ValidationResult>,
    private val behandlingsUtfallTopic: String,
) {

    @WithSpan
    fun sendRuleCheckValidationResult(
        sykmeldingId: String,
        validationResult: ValidationResult,
        loggingMeta: LoggingMeta,
        tsmProcessingTarget: Boolean,
    ) {
        val record = ProducerRecord(behandlingsUtfallTopic, sykmeldingId, validationResult)
        log.info("tsmprocessingtarget: $tsmProcessingTarget")
        if (tsmProcessingTarget) {
            log.info("adding tsm processing-target for validationResult for $sykmeldingId")
            record
                .headers()
                .add(
                    PROCESSING_TARGET_HEADER,
                    TSM_PROCESSING_TARGET_VALUE.toByteArray(Charsets.UTF_8)
                )
        }
        try {
            kafkaAivenProducerBehandlingsutfall
                .send(
                    record,
                )
                .get()

            log.info(
                "Message got outcome {}, {}, {}",
                StructuredArguments.keyValue("status", validationResult.status),
                StructuredArguments.keyValue(
                    "ruleHits",
                    validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName },
                ),
                StructuredArguments.fields(loggingMeta),
            )
            log.info(
                "Validation results send to aiven kafka {} $loggingMeta",
                behandlingsUtfallTopic,
                StructuredArguments.fields(loggingMeta),
            )
        } catch (ex: Exception) {
            log.error(
                "Error writing validationResult to aiven kafka for sykmelding {} {}",
                loggingMeta.sykmeldingId,
                loggingMeta,
            )
            throw ex
        }
    }
}
