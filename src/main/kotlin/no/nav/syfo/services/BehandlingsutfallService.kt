package no.nav.syfo.services

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class BehandlingsutfallService(
    private val kafkaAivenProducerBehandlingsutfall: KafkaProducer<String, ValidationResult>,
    private val behandlingsUtfallTopic: String
) {

    fun sendRuleCheckValidationResult(
        receivedSykmelding: ReceivedSykmelding,
        validationResult: ValidationResult,
        loggingMeta: LoggingMeta
    ) {
        try {
            kafkaAivenProducerBehandlingsutfall.send(
                ProducerRecord(behandlingsUtfallTopic, receivedSykmelding.sykmelding.id, validationResult)
            ).get()
            log.info(
                "Validation results send to aiven kafka {} $loggingMeta", behandlingsUtfallTopic,
                StructuredArguments.fields(loggingMeta)
            )
        } catch (ex: Exception) {
            log.error(
                "Error writing validationResult to aiven kafka for sykmelding {} {}",
                loggingMeta.sykmeldingId,
                loggingMeta
            )
            throw ex
        }
    }
}
