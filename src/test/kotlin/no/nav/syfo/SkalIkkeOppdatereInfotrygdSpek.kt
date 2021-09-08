package no.nav.syfo

import io.ktor.util.KtorExperimentalAPI
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.model.Merknad
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.rules.ValidationRuleChain
import no.nav.syfo.services.UpdateInfotrygdService
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

@KtorExperimentalAPI
object SkalIkkeOppdatereInfotrygdSpek : Spek({
    describe("Skal ikke oppdatere infotrygd") {
        val sm = mockk<ReceivedSykmelding>()
        it("Skal oppdater infotrygd ved ingen merknader") {
            every { sm.merknader } returns emptyList()
            skalOppdatereInfotrygd(sm) shouldBeEqualTo true
        }
        it("Skal oppdater infotrygd ved ingen merknader") {
            every { sm.merknader } returns null
            skalOppdatereInfotrygd(sm) shouldBeEqualTo true
        }
        it("Skal ikke oppdatere infotrygd ved merknader UGYLDIG_TILBAKEDATERING") {
            every { sm.merknader } returns listOf(Merknad("UGYLDIG_TILBAKEDATERING", null))
            skalOppdatereInfotrygd(sm) shouldBeEqualTo false
        }
        it("Skal ikke oppdatere infotrygd ved merknader TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER") {
            every { sm.merknader } returns listOf(Merknad("TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER", null))
            skalOppdatereInfotrygd(sm) shouldBeEqualTo false
        }
        it("Skal ikke oppdatere infotrygd ved merknader TILBAKEDATERT_PAPIRSYKMELDING") {
            every { sm.merknader } returns listOf(Merknad("TILBAKEDATERT_PAPIRSYKMELDING", null))
            skalOppdatereInfotrygd(sm) shouldBeEqualTo false
        }
        it("Skal ikke oppdatere infotrygd ved merknader UNDER_BEHANDLING") {
            every { sm.merknader } returns listOf(Merknad("UNDER_BEHANDLING", null))
            skalOppdatereInfotrygd(sm) shouldBeEqualTo false
        }
        it("Skal oppdatere infotrygd ved annen merknader ANNEN_MERKNAD") {
            every { sm.merknader } returns listOf(Merknad("ANNEN_MERKNAD", null))
            skalOppdatereInfotrygd(sm) shouldBeEqualTo true
        }
    }
    describe("Testing av metoden skalIkkeOppdatereInfotrygd") {
        val updateInfotrygdService = UpdateInfotrygdService()

        it("Skal ikkje oppdatere infotrygd, pga lik eller under 3 dager i sykmeldings peridene totalt") {
            val validationResult = ValidationResult(
                status = Status.MANUAL_PROCESSING,
                ruleHits = listOf(
                    RuleInfo(
                        ruleName = ValidationRuleChain.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE.name,
                        messageForUser = "",
                        messageForSender = "",
                        ruleStatus = Status.MANUAL_PROCESSING
                    ),
                    RuleInfo(
                        ruleName = "NUMBER_OF_TREATMENT_DAYS_SET",
                        messageForUser = "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
                        messageForSender = "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
                        ruleStatus = Status.MANUAL_PROCESSING
                    )
                )
            )

            val receivedSykmelding = receivedSykmelding(
                "1",
                generateSykmelding(
                    perioder = listOf(
                        generatePeriode(
                            fom = LocalDate.of(2019, 1, 1),
                            tom = LocalDate.of(2019, 1, 4)
                        )
                    )
                )
            )

            updateInfotrygdService.skalIkkeProdusereManuellOppgave(receivedSykmelding, validationResult) shouldBeEqualTo true
        }

        it("Skal oppdatere infotrygd, pga større enn 3 dager i sykmeldings peridene totalt") {

            val validationResult = ValidationResult(
                status = Status.MANUAL_PROCESSING,
                ruleHits = listOf(
                    RuleInfo(
                        ruleName = ValidationRuleChain.PERIOD_IS_AF.name,
                        messageForUser = "",
                        messageForSender = "",
                        ruleStatus = Status.MANUAL_PROCESSING
                    ),
                    RuleInfo(
                        ruleName = ValidationRuleChain.TRAVEL_SUBSIDY_SPECIFIED.name,
                        messageForUser = "",
                        messageForSender = "",
                        ruleStatus = Status.MANUAL_PROCESSING
                    )
                )
            )

            val receivedSykmelding = receivedSykmelding(
                "1",
                generateSykmelding(
                    perioder = listOf(
                        generatePeriode(
                            fom = LocalDate.of(2019, 1, 1),
                            tom = LocalDate.of(2019, 1, 5)
                        )
                    )
                )
            )

            updateInfotrygdService.skalIkkeProdusereManuellOppgave(receivedSykmelding, validationResult) shouldBeEqualTo false
        }

        it("Skal oppdatere infotrygd, pga større enn 3 dager i sykmeldings peridene totalt") {

            val validationResult = ValidationResult(
                status = Status.MANUAL_PROCESSING,
                ruleHits = listOf(
                    RuleInfo(
                        ruleName = ValidationRuleChain.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE.name,
                        messageForUser = "",
                        messageForSender = "",
                        ruleStatus = Status.MANUAL_PROCESSING
                    ),
                    RuleInfo(
                        ruleName = ValidationRuleChain.TRAVEL_SUBSIDY_SPECIFIED.name,
                        messageForUser = "",
                        messageForSender = "",
                        ruleStatus = Status.MANUAL_PROCESSING
                    )
                )
            )

            val receivedSykmelding = receivedSykmelding(
                "1",
                generateSykmelding(
                    perioder = listOf(
                        generatePeriode(
                            fom = LocalDate.of(2019, 1, 1),
                            tom = LocalDate.of(2019, 1, 5)
                        )
                    )
                )
            )

            updateInfotrygdService.skalIkkeProdusereManuellOppgave(receivedSykmelding, validationResult) shouldBeEqualTo false
        }
    }
})
