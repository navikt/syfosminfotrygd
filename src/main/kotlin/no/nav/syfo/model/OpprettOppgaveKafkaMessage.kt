package no.nav.syfo.model

import no.nav.syfo.sak.avro.PrioritetType

data class OpprettOppgaveKafkaMessage(
    private val messageId: String,
    private val aktoerId: String,
    private val tildeltEnhetsnr: String,
    private val opprettetAvEnhetsnr: String,
    private val behandlesAvApplikasjon: String,
    private val orgnr: String,
    private val beskrivelse: String,
    private val temagruppe: String,
    private val tema: String,
    private val behandlingstema: String,
    private val oppgavetype: String,
    private val behandlingstype: String,
    private val mappeId: Int,
    private val aktivDato: String,
    private val fristFerdigstillelse: String,
    private val prioritet: PrioritetType,
    private val metadata: Map<String, String>?
)
