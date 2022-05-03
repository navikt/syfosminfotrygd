package no.nav.syfo.rules

import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.model.RuleMetadata
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDateTime

class TssRuleChainSpek : FunSpec({
    fun ruleMetadata(
        receivedDate: LocalDateTime = LocalDateTime.now(),
        signatureDate: LocalDateTime = LocalDateTime.now(),
        patientPersonNumber: String = "1234567891",
        rulesetVersion: String = "1",
        legekontorOrgNr: String = "123456789",
        tssid: String? = "1314445"
    ): RuleMetadata =
        RuleMetadata(signatureDate, receivedDate, patientPersonNumber, rulesetVersion, legekontorOrgNr, tssid)

    context("Testing infotrygd rules and checking the rule outcomes") {
        test("TSS_IDENT_MANGLER should trigger on when tssid is null") {
            TssRuleChain(ruleMetadata(tssid = null)).getRuleByName("TSS_IDENT_MANGLER")
                .executeRule().result shouldBeEqualTo true
        }

        test("TSS_IDENT_MANGLER should trigger on when tssid is blank") {
            TssRuleChain(ruleMetadata(tssid = "")).getRuleByName("TSS_IDENT_MANGLER")
                .executeRule().result shouldBeEqualTo true
        }

        test("TSS_IDENT_MANGLER should not trigger on when orgnr is 9") {
            TssRuleChain(ruleMetadata(tssid = "1234567890")).getRuleByName("TSS_IDENT_MANGLER")
                .executeRule().result shouldBeEqualTo false
        }
    }
})
