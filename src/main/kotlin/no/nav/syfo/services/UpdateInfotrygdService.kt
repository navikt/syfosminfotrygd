package no.nav.syfo.services

import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.InfotrygdForespAndHealthInformation
import no.nav.syfo.LoggingMeta
import no.nav.syfo.fetchDoctor
import no.nav.syfo.finnAktivHelsepersonellAutorisasjons
import no.nav.syfo.log
import no.nav.syfo.metrics.RULE_HIT_STATUS_COUNTER
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.produceManualTask
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.sendInfotrygdOppdatering
import no.nhn.schemas.reg.hprv2.IHPR2Service
import no.nhn.schemas.reg.hprv2.IHPR2ServiceHentPersonMedPersonnummerGenericFaultFaultFaultMessage
import org.apache.kafka.clients.producer.KafkaProducer
import redis.clients.jedis.Jedis

class UpdateInfotrygdService(
    val receivedSykmelding: ReceivedSykmelding,
    val helsepersonellv1: IHPR2Service,
    val validationResult: ValidationResult,
    val infotrygdOppdateringProducer: MessageProducer,
    val kafkaproducerCreateTask: KafkaProducer<String, ProduceTask>,
    val navKontorManuellOppgave: String,
    val navKontorLokalKontor: String,
    val loggingMeta: LoggingMeta,
    val session: Session,
    val infotrygdForespResponse: InfotrygdForesp,
    val healthInformation: HelseOpplysningerArbeidsuforhet,
    val jedis: Jedis
) {

    suspend fun updateInfotrygd() {
        try {
            val doctor = fetchDoctor(helsepersonellv1, receivedSykmelding.personNrLege)

            val helsepersonellKategoriVerdi = finnAktivHelsepersonellAutorisasjons(doctor)

            when {
                validationResult.status in arrayOf(Status.MANUAL_PROCESSING) ->
                    produceManualTask(kafkaproducerCreateTask, receivedSykmelding, validationResult, navKontorManuellOppgave, loggingMeta)
                else -> sendInfotrygdOppdatering(
                        infotrygdOppdateringProducer,
                        session,
                        loggingMeta,
                        InfotrygdForespAndHealthInformation(infotrygdForespResponse, healthInformation),
                        receivedSykmelding,
                        helsepersonellKategoriVerdi,
                        navKontorLokalKontor,
                        jedis)
            }

            log.info("Message(${StructuredArguments.fields(loggingMeta)}) got outcome {}, {}, processing took {}s",
                    StructuredArguments.keyValue("status", validationResult.status),
                    StructuredArguments.keyValue("ruleHits", validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName }))
        } catch (e: IHPR2ServiceHentPersonMedPersonnummerGenericFaultFaultFaultMessage) {
            val validationResultBehandler = ValidationResult(
                    status = Status.MANUAL_PROCESSING,
                    ruleHits = listOf(RuleInfo(
                            ruleName = "BEHANDLER_NOT_IN_HPR",
                            messageForSender = "Den som har skrevet sykmeldingen din har ikke autorisasjon til dette.",
                            messageForUser = "Behandler er ikke register i HPR"))
            )
            RULE_HIT_STATUS_COUNTER.labels(validationResultBehandler.status.name).inc()
            log.warn("Behandler er ikke register i HPR")
            produceManualTask(kafkaproducerCreateTask, receivedSykmelding, validationResultBehandler, navKontorManuellOppgave, loggingMeta)
        }
    }
}