package no.nav.syfo.services

import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import org.amshove.kluent.shouldBeEqualTo

class ErrorFromInfotrygdSpek : FunSpec({
    context("Tester at vi fanger opp der infotrygd gir mye error") {
        test("Should set errorFromInfotrygd to true") {
            val rules = listOf(
                RuleInfo(
                    ruleName = "ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING",
                    messageForSender = "messageForSender",
                    messageForUser = "messageforUser ",
                    ruleStatus = Status.MANUAL_PROCESSING,
                ),
                RuleInfo(
                    ruleName = "ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING",
                    messageForSender = "messageForSender",
                    messageForUser = "messageForUser",
                    ruleStatus = Status.MANUAL_PROCESSING,
                ),
            )

            errorFromInfotrygd(rules) shouldBeEqualTo true
        }

        test("Should set errorFromInfotrygd to false") {
            val rules = listOf(
                RuleInfo(
                    ruleName = "TRAVEL_SUBSIDY_SPECIFIED",
                    messageForSender = "messageForSender",
                    messageForUser = "messageForUser",
                    ruleStatus = Status.MANUAL_PROCESSING,
                ),
                RuleInfo(
                    ruleName = "PERIOD_IS_AF",
                    messageForSender = "messageForSender",
                    messageForUser = "messageForUser",
                    ruleStatus = Status.MANUAL_PROCESSING,
                ),
            )

            errorFromInfotrygd(rules) shouldBeEqualTo false
        }

        test("Should set errorFromInfotrygd to true") {
            val rules = listOf(
                RuleInfo(
                    ruleName = "TRAVEL_SUBSIDY_SPECIFIED",
                    messageForSender = "messageForSender",
                    messageForUser = "messageForUser",
                    ruleStatus = Status.MANUAL_PROCESSING,
                ),
                RuleInfo(
                    ruleName = "ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING",
                    messageForSender = "messageForSender",
                    messageForUser = "messageForUser",
                    ruleStatus = Status.MANUAL_PROCESSING,
                ),
            )

            errorFromInfotrygd(rules) shouldBeEqualTo true
        }
    }
})
