package no.nav.syfo.services

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.SyfosmreglerClient
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.receivedSykmelding
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object OpprettOppgaveTest : Spek({
    val loggingMeta = LoggingMeta("", "", "", "")
    val validationResults = ValidationResult(Status.MANUAL_PROCESSING, ruleHits = listOf(RuleInfo(
        "NUMBER_OF_TREATMENT_DAYS_SET",
        "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
        "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
        Status.MANUAL_PROCESSING
    )))
    val syfosmreglerClient = mockk<SyfosmreglerClient>()

    beforeEachTest {
        clearAllMocks()
        coEvery { syfosmreglerClient.executeRuleValidation(any(), any()) } returns ValidationResult(Status.OK, emptyList())
    }

    describe("Oppretter manuelle oppgaver med riktige parametre") {
        it("Behandlingstype er ae0256 hvis sykmelding treffer manuell-regler") {
            coEvery { syfosmreglerClient.executeRuleValidation(any(), any()) } returns ValidationResult(Status.MANUAL_PROCESSING, listOf(
                RuleInfo("TILBAKEDATERT_MER_ENN_8_DAGER_FORSTE_SYKMELDING_MED_BEGRUNNELSE", "message for sender", "message for user", Status.MANUAL_PROCESSING)))
            val receivedSykmelding = receivedSykmelding(UUID.randomUUID().toString())

            runBlocking {
                val oppgave = opprettProduceTask(syfosmreglerClient, receivedSykmelding, validationResults, "prod-fss", LocalDate.of(2021, 1, 10), loggingMeta)

                oppgave.behandlingstype shouldEqual "ae0256"
            }
        }
        it("Behandlingstype er ANY hvis sykmelding ikke treffer manuell-regler") {
            coEvery { syfosmreglerClient.executeRuleValidation(any(), any()) } returns ValidationResult(Status.MANUAL_PROCESSING, listOf(
                RuleInfo("ANNEN_REGEL", "message for sender", "message for user", Status.MANUAL_PROCESSING)))
            val receivedSykmelding = receivedSykmelding(UUID.randomUUID().toString())

            runBlocking {
                val oppgave = opprettProduceTask(syfosmreglerClient, receivedSykmelding, validationResults, "prod-fss", LocalDate.of(2021, 1, 10), loggingMeta)

                oppgave.behandlingstype shouldEqual "ANY"
            }
        }
    }
})
