package no.nav.syfo

import io.ktor.util.KtorExperimentalAPI
import java.time.LocalDate
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.rules.ValidationRuleChain
import no.nav.syfo.services.UpdateInfotrygdService
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object SkalIkkeOppdatereInfotrygdSpek : Spek({

    describe("Testing av metoden skalIkkeOppdatereInfotrygd") {
        val updateInfotrygdService = UpdateInfotrygdService()

        it("Skal ikkje oppdatere infotrygd, pga lik eller under 3 dager i sykmeldings peridene totalt") {
            val validationResult = ValidationResult(status = Status.MANUAL_PROCESSING, ruleHits = listOf(
                    RuleInfo(ruleName = ValidationRuleChain.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE.name,
                            messageForUser = "",
                            messageForSender = "",
                            ruleStatus = Status.MANUAL_PROCESSING
                    ),
                    RuleInfo(ruleName = "NUMBER_OF_TREATMENT_DAYS_SET",
                            messageForUser = "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
                            messageForSender = "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
                            ruleStatus = Status.MANUAL_PROCESSING
                    )))

            val receivedSykmelding = receivedSykmelding("1", generateSykmelding(perioder = listOf(
                    generatePeriode(
                            fom = LocalDate.of(2019, 1, 1),
                            tom = LocalDate.of(2019, 1, 4)
                    )
            )
            )
            )

            updateInfotrygdService.skalIkkeOppdatereInfotrygd(receivedSykmelding, validationResult) shouldEqual true
        }

        it("Skal oppdatere infotrygd, pga større enn 3 dager i sykmeldings peridene totalt") {

            val validationResult = ValidationResult(status = Status.MANUAL_PROCESSING, ruleHits = listOf(
                    RuleInfo(ruleName = ValidationRuleChain.PERIOD_IS_AF.name,
                            messageForUser = "",
                            messageForSender = "",
                            ruleStatus = Status.MANUAL_PROCESSING
                    ),
                    RuleInfo(ruleName = ValidationRuleChain.TRAVEL_SUBSIDY_SPECIFIED.name,
                            messageForUser = "",
                            messageForSender = "",
                            ruleStatus = Status.MANUAL_PROCESSING
                    )))

            val receivedSykmelding = receivedSykmelding("1", generateSykmelding(perioder = listOf(
                    generatePeriode(
                            fom = LocalDate.of(2019, 1, 1),
                            tom = LocalDate.of(2019, 1, 5)
                    )
            )
            )
            )

            updateInfotrygdService.skalIkkeOppdatereInfotrygd(receivedSykmelding, validationResult) shouldEqual false
        }

        it("Skal oppdatere infotrygd, pga større enn 3 dager i sykmeldings peridene totalt") {

            val validationResult = ValidationResult(status = Status.MANUAL_PROCESSING, ruleHits = listOf(
                    RuleInfo(ruleName = ValidationRuleChain.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE.name,
                            messageForUser = "",
                            messageForSender = "",
                            ruleStatus = Status.MANUAL_PROCESSING
                    ),
                    RuleInfo(ruleName = ValidationRuleChain.TRAVEL_SUBSIDY_SPECIFIED.name,
                            messageForUser = "",
                            messageForSender = "",
                            ruleStatus = Status.MANUAL_PROCESSING
                    )))

            val receivedSykmelding = receivedSykmelding("1", generateSykmelding(perioder = listOf(
                    generatePeriode(
                            fom = LocalDate.of(2019, 1, 1),
                            tom = LocalDate.of(2019, 1, 5)
                        )
                    )
                )
            )

            updateInfotrygdService.skalIkkeOppdatereInfotrygd(receivedSykmelding, validationResult) shouldEqual false
        }
    }
})