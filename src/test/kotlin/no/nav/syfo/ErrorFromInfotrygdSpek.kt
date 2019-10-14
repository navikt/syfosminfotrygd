package no.nav.syfo

import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.rules.ValidationRuleChain
import no.nav.syfo.services.UpdateInfotrygdService
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object ErrorFromInfotrygdSpek : Spek({
    describe("Tester at vi fanger opp der infotrygd gir mye error") {

        it("Should set errorFromInfotrygd to true") {
            val updateInfotrygdService = UpdateInfotrygdService()

            val rules = listOf(RuleInfo(
                            ruleName = ValidationRuleChain.ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING.name,
                            messageForSender = ValidationRuleChain.ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING.messageForSender,
                            messageForUser = ValidationRuleChain.ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING.messageForUser,
                            ruleStatus = Status.MANUAL_PROCESSING),
                    RuleInfo(
                            ruleName = ValidationRuleChain.ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING.name,
                            messageForSender = ValidationRuleChain.ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING.messageForSender,
                            messageForUser = ValidationRuleChain.ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING.messageForUser,
                            ruleStatus = Status.MANUAL_PROCESSING)
                    )

            updateInfotrygdService.errorFromInfotrygd(rules) shouldEqual true
        }

        it("Should set errorFromInfotrygd to true") {
            val updateInfotrygdService = UpdateInfotrygdService()

            val rules = listOf(RuleInfo(
                    ruleName = ValidationRuleChain.TRAVEL_SUBSIDY_SPECIFIED.name,
                    messageForSender = ValidationRuleChain.TRAVEL_SUBSIDY_SPECIFIED.messageForSender,
                    messageForUser = ValidationRuleChain.TRAVEL_SUBSIDY_SPECIFIED.messageForUser,
                    ruleStatus = Status.MANUAL_PROCESSING),
                    RuleInfo(
                            ruleName = ValidationRuleChain.PERIOD_IS_AF.name,
                            messageForSender = ValidationRuleChain.PERIOD_IS_AF.messageForSender,
                            messageForUser = ValidationRuleChain.PERIOD_IS_AF.messageForUser,
                            ruleStatus = Status.MANUAL_PROCESSING)
            )

            updateInfotrygdService.errorFromInfotrygd(rules) shouldEqual false
        }
    }
})
