package no.nav.syfo.rules

import no.nav.syfo.generateSykmelding
import no.nav.syfo.model.RuleMetadata
import no.nav.syfo.model.Sykmelding
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime

object TssRuleChainSpek : Spek({
    fun ruleData(
        healthInformation: Sykmelding,
        receivedDate: LocalDateTime = LocalDateTime.now(),
        signatureDate: LocalDateTime = LocalDateTime.now(),
        patientPersonNumber: String = "1234567891",
        rulesetVersion: String = "1",
        legekontorOrgNr: String = "123456789",
        tssid: String? = "1314445"
    ): RuleData<RuleMetadata> = RuleData(healthInformation, RuleMetadata(signatureDate, receivedDate, patientPersonNumber, rulesetVersion, legekontorOrgNr, tssid))

    describe("Testing infotrygd rules and checking the rule outcomes") {

        it("TSS_IDENT_MANGLER should trigger on when tssid is null") {
            val healthInformation = generateSykmelding()

            TssRuleChain.TSS_IDENT_MANGLER(ruleData(healthInformation, tssid = null)) shouldBeEqualTo true
        }

        it("TSS_IDENT_MANGLER should trigger on when tssid is blank") {
            val healthInformation = generateSykmelding()

            TssRuleChain.TSS_IDENT_MANGLER(ruleData(healthInformation, tssid = "")) shouldBeEqualTo true
        }

        it("TSS_IDENT_MANGLER should not trigger on when orgnr is 9") {
            val healthInformation = generateSykmelding()

            TssRuleChain.TSS_IDENT_MANGLER(ruleData(healthInformation, tssid = "1234567890")) shouldBeEqualTo false
        }
    }
})
