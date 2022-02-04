package no.nav.syfo.rules

import no.nav.syfo.model.RuleMetadata
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime

object TssRuleChainSpek : Spek({
    fun ruleMetadata(
        receivedDate: LocalDateTime = LocalDateTime.now(),
        signatureDate: LocalDateTime = LocalDateTime.now(),
        patientPersonNumber: String = "1234567891",
        rulesetVersion: String = "1",
        legekontorOrgNr: String = "123456789",
        tssid: String? = "1314445"
    ): RuleMetadata =
        RuleMetadata(signatureDate, receivedDate, patientPersonNumber, rulesetVersion, legekontorOrgNr, tssid)

    describe("Testing infotrygd rules and checking the rule outcomes") {
        it("TSS_IDENT_MANGLER should trigger on when tssid is null") {
            TssRuleChain(ruleMetadata(tssid = null)).getRuleByName("TSS_IDENT_MANGLER")
                .executeRule().result shouldBeEqualTo true
        }

        it("TSS_IDENT_MANGLER should trigger on when tssid is blank") {
            TssRuleChain(ruleMetadata(tssid = "")).getRuleByName("TSS_IDENT_MANGLER")
                .executeRule().result shouldBeEqualTo true
        }

        it("TSS_IDENT_MANGLER should not trigger on when orgnr is 9") {
            TssRuleChain(ruleMetadata(tssid = "1234567890")).getRuleByName("TSS_IDENT_MANGLER")
                .executeRule().result shouldBeEqualTo false
        }
    }
})
