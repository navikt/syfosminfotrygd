package no.nav.syfo

import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.model.Merknad
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

            updateInfotrygdService.skalIkkeOppdatereInfotrygd(receivedSykmelding, validationResult) shouldBeEqualTo true
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

            updateInfotrygdService.skalIkkeOppdatereInfotrygd(receivedSykmelding, validationResult) shouldBeEqualTo false
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

            updateInfotrygdService.skalIkkeOppdatereInfotrygd(receivedSykmelding, validationResult) shouldBeEqualTo false
        }

        it("Skal ikke oppdatere infotrygd fordi sykmeldingen har merknad UGYLDIG_TILBAKEDATERING") {

            val validationResult = ValidationResult(
                status = Status.OK,
                ruleHits = emptyList()
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
                ),
                merknader = listOf(Merknad(type = "UGYLDIG_TILBAKEDATERING", beskrivelse = "Litt av en beskrivelse"))
            )

            updateInfotrygdService.skalIkkeOppdatereInfotrygd(receivedSykmelding, validationResult) shouldBeEqualTo true
        }

        it("Skal ikke oppdatere infotrygd fordi sykmeldingen har merknad TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER") {

            val validationResult = ValidationResult(
                status = Status.OK,
                ruleHits = emptyList()
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
                ),
                merknader = listOf(Merknad(type = "TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER", beskrivelse = "Litt av en beskrivelse"))
            )

            updateInfotrygdService.skalIkkeOppdatereInfotrygd(receivedSykmelding, validationResult) shouldBeEqualTo true
        }

        it("Skal oppdatere infotrygd fordi sykmeldingen har en ukjent merknad") {

            val validationResult = ValidationResult(
                status = Status.OK,
                ruleHits = emptyList()
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
                ),
                merknader = listOf(Merknad(type = "Litt av en type", beskrivelse = "Litt av en beskrivelse"))
            )

            updateInfotrygdService.skalIkkeOppdatereInfotrygd(receivedSykmelding, validationResult) shouldBeEqualTo false
        }
    }
})
