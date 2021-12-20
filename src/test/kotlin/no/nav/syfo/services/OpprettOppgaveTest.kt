package no.nav.syfo.services

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.ManuellClient
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

object OpprettOppgaveTest : Spek({
    val loggingMeta = LoggingMeta("", "", "", "")
    val manuellClient = mockk<ManuellClient>()

    beforeEachTest {
        clearMocks(manuellClient)
    }

    describe("Oppretter manuelle oppgaver med riktige parametre") {
        it("Behandlingstype er ae0256 hvis sykmelding har blitt behandlet av manuell") {
            val validationResults = ValidationResult(Status.MANUAL_PROCESSING, listOf(RuleInfo("TILBAKEDATERT_MER_ENN_8_DAGER_FORSTE_SYKMELDING_MED_BEGRUNNELSE", "message for sender", "message for user", Status.MANUAL_PROCESSING)))
            coEvery { manuellClient.behandletAvManuell(any(), any()) } returns true
            val receivedSykmelding = receivedSykmelding(UUID.randomUUID().toString())

            runBlocking {
                val oppgave = opprettProduceTask(manuellClient, receivedSykmelding, validationResults, loggingMeta)

                oppgave.behandlingstype shouldBeEqualTo "ae0256"
            }
        }
        it("fristFerdigstillelse er i dag hvis sykmelding har blitt behandlet av manuell") {
            val validationResults = ValidationResult(Status.MANUAL_PROCESSING, listOf(RuleInfo("TILBAKEDATERT_MER_ENN_8_DAGER_FORSTE_SYKMELDING_MED_BEGRUNNELSE", "message for sender", "message for user", Status.MANUAL_PROCESSING)))
            coEvery { manuellClient.behandletAvManuell(any(), any()) } returns true
            val receivedSykmelding = receivedSykmelding(UUID.randomUUID().toString())

            runBlocking {
                val oppgave = opprettProduceTask(manuellClient, receivedSykmelding, validationResults, loggingMeta)

                oppgave.fristFerdigstillelse shouldBeEqualTo DateTimeFormatter.ISO_DATE.format(LocalDate.now())
            }
        }
        it("Behandlingstype er ANY hvis sykmelding ikke har blitt behandlet av manuell") {
            val validationResults = ValidationResult(Status.MANUAL_PROCESSING, listOf(RuleInfo("ANNEN_REGEL", "message for sender", "message for user", Status.MANUAL_PROCESSING)))
            coEvery { manuellClient.behandletAvManuell(any(), any()) } returns false
            val receivedSykmelding = receivedSykmelding(UUID.randomUUID().toString())

            runBlocking {
                val oppgave = opprettProduceTask(manuellClient, receivedSykmelding, validationResults, loggingMeta)

                oppgave.behandlingstype shouldBeEqualTo "ANY"
            }
        }
    }
})
