package no.nav.syfo.rules.tss

import io.kotest.core.spec.style.FunSpec
import java.time.LocalDate
import java.util.UUID
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.StatusType
import no.nav.syfo.generatePeriode
import no.nav.syfo.generateSykmelding
import no.nav.syfo.model.RuleMetadata
import no.nav.syfo.model.sykmelding.Status
import no.nav.syfo.receivedSykmelding
import no.nav.syfo.services.updateinfotrygd.Operasjonstype
import org.amshove.kluent.shouldBeEqualTo

class TSSTest :
    FunSpec({
        val ruleTree = TSSRulesExecution()

        context("Testing tss rules and checking the rule outcomes") {
            test("tssid is set, should be OK") {
                val sykmelding =
                    generateSykmelding(
                        perioder =
                            listOf(
                                generatePeriode(
                                    fom = LocalDate.now(),
                                    tom = LocalDate.now().plusDays(10),
                                ),
                            ),
                    )

                val infotrygdForespResponse =
                    InfotrygdForesp().apply {
                        hovedStatus = StatusType().apply { kodeMelding = "00" }
                    }

                val receivedSykmelding =
                    receivedSykmelding(
                        id = UUID.randomUUID().toString(),
                        sykmelding = sykmelding,
                        fellesformat = "",
                        tssid = "124344",
                    )

                val ruleMetadata =
                    RuleMetadata(
                        signatureDate = receivedSykmelding.sykmelding.signaturDato,
                        receivedDate = receivedSykmelding.mottattDato,
                        patientPersonNumber = receivedSykmelding.personNrPasient,
                        rulesetVersion = receivedSykmelding.rulesetVersion,
                        legekontorOrgnr = receivedSykmelding.legekontorOrgNr,
                        tssid = receivedSykmelding.tssid,
                        infotrygdForesp = infotrygdForespResponse,
                        Operasjonstype.NY to LocalDate.now(),
                    )

                val result = ruleTree.runRules(sykmelding, ruleMetadata)

                result.first.rulePath.map { it.rule to it.ruleResult } shouldBeEqualTo
                    listOf(
                        TSSRules.TSS_IDENT_MANGLER to false,
                    )
                mapOf(
                    "tssId" to receivedSykmelding.tssid,
                ) shouldBeEqualTo result.first.ruleInputs

                result.first.treeResult.ruleHit shouldBeEqualTo null

                result.first.treeResult.status shouldBeEqualTo Status.OK
            }

            test("tssid is not set, should be MANUAL_PROCESSING") {
                val sykmelding =
                    generateSykmelding(
                        perioder =
                            listOf(
                                generatePeriode(
                                    fom = LocalDate.now(),
                                    tom = LocalDate.now().plusDays(10),
                                ),
                            ),
                    )

                val infotrygdForespResponse =
                    InfotrygdForesp().apply {
                        hovedStatus = StatusType().apply { kodeMelding = "00" }
                    }

                val receivedSykmelding =
                    receivedSykmelding(
                        id = UUID.randomUUID().toString(),
                        sykmelding = sykmelding,
                        fellesformat = "",
                    )

                val ruleMetadata =
                    RuleMetadata(
                        signatureDate = receivedSykmelding.sykmelding.signaturDato,
                        receivedDate = receivedSykmelding.mottattDato,
                        patientPersonNumber = receivedSykmelding.personNrPasient,
                        rulesetVersion = receivedSykmelding.rulesetVersion,
                        legekontorOrgnr = receivedSykmelding.legekontorOrgNr,
                        tssid = null,
                        infotrygdForesp = infotrygdForespResponse,
                        Operasjonstype.NY to LocalDate.now(),
                    )

                val result = ruleTree.runRules(sykmelding, ruleMetadata)

                result.first.treeResult.status shouldBeEqualTo Status.MANUAL_PROCESSING
                result.first.rulePath.map { it.rule to it.ruleResult } shouldBeEqualTo
                    listOf(
                        TSSRules.TSS_IDENT_MANGLER to true,
                    )
                mapOf(
                    "tssId" to "",
                ) shouldBeEqualTo result.first.ruleInputs

                result.first.treeResult.ruleHit shouldBeEqualTo TSSRuleHit.TSS_IDENT_MANGLER.ruleHit

                result.first.treeResult.status shouldBeEqualTo Status.MANUAL_PROCESSING
            }
        }
    })
