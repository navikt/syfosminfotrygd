package no.nav.syfo.services

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class BehandlingsutfallService(
    private val kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    private val sm2013BehandlingsUtfallTopic: String,
    private val kafkaAivenProducerBehandlingsutfall: KafkaProducer<String, ValidationResult>,
    private val behandlingsUtfallTopic: String
) {

    fun sendRuleCheckValidationResult(
        receivedSykmelding: ReceivedSykmelding,
        validationResult: ValidationResult,
        loggingMeta: LoggingMeta,
        source: String
    ) {
        when (source) {
            "on-prem" -> {
                try {
                    kafkaproducervalidationResult.send(
                        ProducerRecord(sm2013BehandlingsUtfallTopic, receivedSykmelding.sykmelding.id, validationResult)
                    ).get()
                    log.info(
                        "Validation results send to kafka on-prem {} $loggingMeta", sm2013BehandlingsUtfallTopic,
                        StructuredArguments.fields(loggingMeta)
                    )
                } catch (ex: Exception) {
                    log.error(
                        "Error writing validationResult to kafka on-prem for sykmelding {} {}",
                        loggingMeta.sykmeldingId,
                        loggingMeta
                    )
                    throw ex
                }
            }
            "aiven" -> {
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
            else -> {
                log.error("Mottok ukjent source: $source")
                throw IllegalStateException("Source må være enten on-prem eller aiven")
            }
        }
    }
}
