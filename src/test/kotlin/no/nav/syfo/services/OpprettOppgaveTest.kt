package no.nav.syfo.services

import java.time.LocalDate
import java.util.UUID
import no.nav.syfo.generatePeriode
import no.nav.syfo.generateSykmelding
import no.nav.syfo.model.Gradert
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.receivedSykmelding
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object OpprettOppgaveTest : Spek({
    val loggingMeta = LoggingMeta("", "", "", "")
    val validationResults = ValidationResult(Status.MANUAL_PROCESSING, ruleHits = listOf(RuleInfo(
        "NUMBER_OF_TREATMENT_DAYS_SET",
        "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
        "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
        Status.MANUAL_PROCESSING
    )))
    val receivedSykmeldingMedBehandlingsdager = receivedSykmelding(
        id = UUID.randomUUID().toString(),
        sykmelding = generateSykmelding(perioder = listOf(
            generatePeriode(
                fom = LocalDate.now(),
                tom = LocalDate.now().plusMonths(3).plusDays(1),
                behandlingsdager = 1
            )
        )))
    val receivedSykmeldingMedReisetilskudd = receivedSykmelding(
        id = UUID.randomUUID().toString(),
        sykmelding = generateSykmelding(perioder = listOf(
            generatePeriode(
                fom = LocalDate.now(),
                tom = LocalDate.now().plusMonths(3).plusDays(1),
                reisetilskudd = true
            )
        )))

    describe("Oppretter manuelle oppgaver med riktige parametre") {
        it("Behandlingstema er ANY hvis sykmelding ikke har behandlingsdager eller reisetilskudd") {
            val receivedSykmelding = receivedSykmelding(UUID.randomUUID().toString())

            val oppgave = opprettProduceTask(receivedSykmelding, validationResults, "dev-fss", LocalDate.of(2021, 1, 10), loggingMeta)

            oppgave.behandlingstema shouldEqual "ANY"
        }
        it("Behandlingstema er ANY hvis sykmelding har behandlingsdager og miljø er prod og nå er før 31/12 2020") {
            val oppgave = opprettProduceTask(receivedSykmeldingMedBehandlingsdager, validationResults, "prod-fss", LocalDate.of(2020, 12, 10), loggingMeta)

            oppgave.behandlingstema shouldEqual "ANY"
        }
        it("Behandlingstema er ab0351 hvis sykmelding har behandlingsdager og miljø er prod og nå er etter 31/12 2020") {
            val oppgave = opprettProduceTask(receivedSykmeldingMedBehandlingsdager, validationResults, "prod-fss", LocalDate.of(2021, 1, 1), loggingMeta)

            oppgave.behandlingstema shouldEqual "ab0351"
        }
        it("Behandlingstema er ab0351 hvis sykmelding har behandlingsdager og miljø er dev") {
            val oppgave = opprettProduceTask(receivedSykmeldingMedBehandlingsdager, validationResults, "dev-fss", LocalDate.of(2020, 12, 10), loggingMeta)

            oppgave.behandlingstema shouldEqual "ab0351"
        }
        it("Behandlingstema er ANY hvis sykmelding har reisetilskudd og miljø er prod og nå er før 31/12 2020") {
            val oppgave = opprettProduceTask(receivedSykmeldingMedReisetilskudd, validationResults, "prod-fss", LocalDate.of(2020, 12, 10), loggingMeta)

            oppgave.behandlingstema shouldEqual "ANY"
        }
        it("Behandlingstema er ab0237 hvis sykmelding har reisetilskudd og miljø er prod og nå er etter 31/12 2020") {
            val oppgave = opprettProduceTask(receivedSykmeldingMedReisetilskudd, validationResults, "prod-fss", LocalDate.of(2021, 1, 1), loggingMeta)

            oppgave.behandlingstema shouldEqual "ab0237"
        }
        it("Behandlingstema er ab0237 hvis sykmelding har reisetilskudd og miljø er dev") {
            val oppgave = opprettProduceTask(receivedSykmeldingMedReisetilskudd, validationResults, "dev-fss", LocalDate.of(2020, 12, 10), loggingMeta)

            oppgave.behandlingstema shouldEqual "ab0237"
        }
        it("Behandlingstema er ab0237 hvis sykmelding er gradert med reisetilskudd og miljø er prod og nå er etter 31/12 2020") {
            val receivedSykmeldingGradertMedReisetilskudd = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = generateSykmelding(perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.now(),
                        tom = LocalDate.now().plusMonths(3).plusDays(1),
                        gradert = Gradert(
                            reisetilskudd = true,
                            grad = 90
                        )
                    )
                )))

            val oppgave = opprettProduceTask(receivedSykmeldingGradertMedReisetilskudd, validationResults, "prod-fss", LocalDate.of(2021, 1, 1), loggingMeta)

            oppgave.behandlingstema shouldEqual "ab0237"
        }
    }
})
