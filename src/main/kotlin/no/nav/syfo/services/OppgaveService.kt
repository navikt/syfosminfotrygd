package no.nav.syfo.services

import io.opentelemetry.instrumentation.annotations.WithSpan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.erTilbakedatert
import no.nav.syfo.erUtenlandskSykmelding
import no.nav.syfo.log
import no.nav.syfo.metrics.MANUELLE_OPPGAVER_COUNTER
import no.nav.syfo.model.OpprettOppgaveKafkaMessage
import no.nav.syfo.model.sykmelding.ReceivedSykmelding
import no.nav.syfo.model.sykmelding.ValidationResult
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class OppgaveService(
    private val kafkaAivenProducerOppgave: KafkaProducer<String, OpprettOppgaveKafkaMessage>,
    private val produserOppgaveTopic: String,
) {
    @WithSpan
    fun opprettOppgave(
        receivedSykmelding: ReceivedSykmelding,
        validationResult: ValidationResult,
        behandletAvManuell: Boolean,
        loggingMeta: LoggingMeta,
    ) {
        try {
            kafkaAivenProducerOppgave
                .send(
                    ProducerRecord(
                        produserOppgaveTopic,
                        receivedSykmelding.sykmelding.id,
                        opprettOpprettOppgaveKafkaMessage(
                            receivedSykmelding,
                            validationResult,
                            behandletAvManuell,
                            loggingMeta
                        ),
                    ),
                )
                .get()
            MANUELLE_OPPGAVER_COUNTER.labels(
                    validationResult.ruleHits.firstOrNull()?.ruleName
                        ?: validationResult.status.name,
                )
                .inc()
            log.info(
                "Message sendt to topic: {}, {}",
                produserOppgaveTopic,
                StructuredArguments.fields(loggingMeta)
            )
        } catch (ex: Exception) {
            log.error(
                "Error when writing to oppgave kafka topic {}",
                StructuredArguments.fields(loggingMeta)
            )
            throw ex
        }
    }

    fun opprettOpprettOppgaveKafkaMessage(
        receivedSykmelding: ReceivedSykmelding,
        validationResult: ValidationResult,
        behandletAvManuell: Boolean,
        loggingMeta: LoggingMeta
    ): OpprettOppgaveKafkaMessage {
        val oppgave =
            OpprettOppgaveKafkaMessage(
                messageId = receivedSykmelding.msgId,
                aktoerId = receivedSykmelding.sykmelding.pasientAktoerId,
                tildeltEnhetsnr = "",
                opprettetAvEnhetsnr = "9999",
                behandlesAvApplikasjon = "FS22", // Gosys
                orgnr = receivedSykmelding.legekontorOrgNr ?: "",
                beskrivelse =
                    "Manuell behandling av sykmelding grunnet følgende regler: ${validationResult.ruleHits.joinToString(", ") { it.messageForSender }}",
                temagruppe = "ANY",
                tema = "SYM",
                behandlingstema = "ANY",
                oppgavetype = "BEH_EL_SYM",
                behandlingstype =
                    if (behandletAvManuell || receivedSykmelding.erTilbakedatert()) {
                        log.info(
                            "sykmelding har vært behandlet av syfosmmanuell eller er tilbakedatert, {}",
                            StructuredArguments.fields(loggingMeta)
                        )
                        "ae0256"
                    } else if (receivedSykmelding.erUtenlandskSykmelding()) {
                        log.info(
                            "sykmelding er utenlandsk, {}",
                            StructuredArguments.fields(loggingMeta)
                        )
                        "ae0106"
                    } else {
                        "ANY"
                    },
                mappeId = 1,
                aktivDato = DateTimeFormatter.ISO_DATE.format(LocalDate.now()),
                fristFerdigstillelse =
                    if (behandletAvManuell) {
                        DateTimeFormatter.ISO_DATE.format(LocalDate.now())
                    } else if (receivedSykmelding.erUtenlandskSykmelding()) {
                        DateTimeFormatter.ISO_DATE.format(
                            finnFristForFerdigstillingAvOppgave(LocalDate.now().plusDays(1))
                        )
                    } else {
                        DateTimeFormatter.ISO_DATE.format(
                            finnFristForFerdigstillingAvOppgave(LocalDate.now().plusDays(4))
                        )
                    },
                prioritet = no.nav.syfo.model.PrioritetType.NORM,
                metadata = mapOf(),
            )
        return oppgave
    }

    private fun finnFristForFerdigstillingAvOppgave(ferdistilleDato: LocalDate): LocalDate {
        return setToWorkDay(ferdistilleDato)
    }

    private fun setToWorkDay(ferdistilleDato: LocalDate): LocalDate =
        when (ferdistilleDato.dayOfWeek) {
            DayOfWeek.SATURDAY -> ferdistilleDato.plusDays(2)
            DayOfWeek.SUNDAY -> ferdistilleDato.plusDays(1)
            else -> ferdistilleDato
        }
}
