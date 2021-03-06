package no.nav.syfo.services

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.SyfosmreglerClient
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.receivedSykmelding
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@KtorExperimentalAPI
object OpprettOppgaveTest : Spek({
    val loggingMeta = LoggingMeta("", "", "", "")
    val syfosmreglerClient = mockk<SyfosmreglerClient>()

    beforeEachTest {
        clearAllMocks()
        coEvery { syfosmreglerClient.executeRuleValidation(any(), any()) } returns ValidationResult(Status.OK, emptyList())
    }

    describe("Oppretter manuelle oppgaver med riktige parametre") {
        it("Behandlingstype er ae0256 hvis sykmelding treffer manuell-regler") {
            val validationResults = ValidationResult(Status.MANUAL_PROCESSING, listOf(RuleInfo("TILBAKEDATERT_MER_ENN_8_DAGER_FORSTE_SYKMELDING_MED_BEGRUNNELSE", "message for sender", "message for user", Status.MANUAL_PROCESSING)))
            coEvery { syfosmreglerClient.executeRuleValidation(any(), any()) } returns validationResults
            val receivedSykmelding = receivedSykmelding(UUID.randomUUID().toString())

            runBlocking {
                val oppgave = opprettProduceTask(syfosmreglerClient, receivedSykmelding, validationResults, loggingMeta)

                oppgave.behandlingstype shouldBeEqualTo "ae0256"
            }
        }
        it("Behandlingstype er ae0256 hvis sykmelding treffer manuell-regler (forlengelse)") {
            val validationResults = ValidationResult(Status.MANUAL_PROCESSING, listOf(RuleInfo("TILBAKEDATERT_MED_BEGRUNNELSE_FORLENGELSE", "message for sender", "message for user", Status.MANUAL_PROCESSING)))
            coEvery { syfosmreglerClient.executeRuleValidation(any(), any()) } returns validationResults
            val receivedSykmelding = receivedSykmelding(UUID.randomUUID().toString())

            runBlocking {
                val oppgave = opprettProduceTask(syfosmreglerClient, receivedSykmelding, validationResults, loggingMeta)

                oppgave.behandlingstype shouldBeEqualTo "ae0256"
            }
        }
        it("fristFerdigstillelse er i dag hvis sykmelding treffer manuell-regler") {
            val validationResults = ValidationResult(Status.MANUAL_PROCESSING, listOf(RuleInfo("TILBAKEDATERT_MER_ENN_8_DAGER_FORSTE_SYKMELDING_MED_BEGRUNNELSE", "message for sender", "message for user", Status.MANUAL_PROCESSING)))
            coEvery { syfosmreglerClient.executeRuleValidation(any(), any()) } returns validationResults
            val receivedSykmelding = receivedSykmelding(UUID.randomUUID().toString())

            runBlocking {
                val oppgave = opprettProduceTask(syfosmreglerClient, receivedSykmelding, validationResults, loggingMeta)

                oppgave.fristFerdigstillelse shouldBeEqualTo DateTimeFormatter.ISO_DATE.format(LocalDate.now())
            }
        }
        it("Behandlingstype er ANY hvis sykmelding ikke treffer manuell-regler") {
            val validationResults = ValidationResult(Status.MANUAL_PROCESSING, listOf(RuleInfo("ANNEN_REGEL", "message for sender", "message for user", Status.MANUAL_PROCESSING)))
            coEvery { syfosmreglerClient.executeRuleValidation(any(), any()) } returns validationResults
            val receivedSykmelding = receivedSykmelding(UUID.randomUUID().toString())

            runBlocking {
                val oppgave = opprettProduceTask(syfosmreglerClient, receivedSykmelding, validationResults, loggingMeta)

                oppgave.behandlingstype shouldBeEqualTo "ANY"
            }
        }
    }
})
