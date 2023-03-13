package no.nav.syfo.rules.validation

import io.kotest.core.spec.style.FunSpec
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.StatusType
import no.nav.syfo.generatePeriode
import no.nav.syfo.generateSykmelding
import no.nav.syfo.model.RuleMetadata
import no.nav.syfo.model.Status
import no.nav.syfo.receivedSykmelding
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDate
import java.util.UUID
import no.nav.syfo.model.Gradert

class ValidationTest : FunSpec({

    val ruleTree = ValidationRulesExecution()

    context("Testing validation rules and checking the rule outcomes") {
        test("all is ok, should be OK") {
            val sykmelding = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.now(),
                        tom = LocalDate.now().plusDays(10)
                    )
                )
            )

            val infotrygdForespResponse = InfotrygdForesp().apply {
                hovedStatus = StatusType().apply {
                    kodeMelding = "00"
                }
            }

            val receivedSykmelding = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = sykmelding,
                fellesformat = ""
            )

            val ruleMetadata = RuleMetadata(
                receivedDate = receivedSykmelding.mottattDato,
                signatureDate = receivedSykmelding.sykmelding.signaturDato,
                patientPersonNumber = receivedSykmelding.personNrPasient,
                rulesetVersion = receivedSykmelding.rulesetVersion,
                legekontorOrgnr = receivedSykmelding.legekontorOrgNr,
                tssid = receivedSykmelding.tssid,
                infotrygdForesp = infotrygdForespResponse
            )

            val result = ruleTree.runRules(sykmelding, ruleMetadata)

            result.first.rulePath.map { it.rule to it.ruleResult } shouldBeEqualTo listOf(
                ValidationRules.NUMBER_OF_TREATMENT_DAYS_SET to false,
                ValidationRules.GRADERT_REISETILSKUDD_ER_OPPGITT to false,
                ValidationRules.TRAVEL_SUBSIDY_SPECIFIED to false,
                ValidationRules.PATIENT_NOT_IN_IP to false,
                ValidationRules.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE to false,
                ValidationRules.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1 to false,
                ValidationRules.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2 to false,
                ValidationRules.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3 to false,
                ValidationRules.NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT to false,
                ValidationRules.NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE to false,
                ValidationRules.EXTANION_OVER_FA to false,
                ValidationRules.PERSON_MOVING_KODE_FL to false,
                ValidationRules.PERIOD_FOR_AA_ENDED to false,
                ValidationRules.PERIOD_IS_AF to false,
                ValidationRules.MAX_SICK_LEAVE_PAYOUT to false,
                ValidationRules.ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING to false,
                ValidationRules.ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING to false,
                ValidationRules.ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING to false,
                ValidationRules.ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING to false,
                ValidationRules.ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING to false,
                ValidationRules.ARBEIDUFORETOM_MANGLER to false
            )

            mapOf(
                "perioder" to sykmelding.perioder,
                "pasient" to ruleMetadata.infotrygdForesp.pasient,
                "infotrygdSykmelding" to "",
                "sykmeldingPerioder" to sykmelding.perioder,
                "sykmeldingPrognose" to sykmelding.prognose,
                "sykmelding" to "",
                "hovedStatusKodeMelding" to "00",
                "smHistorikkKodeMelding" to "",
                "parallelleYtelsesKodeMelding" to "",
                "diagnoseKodeKodeMelding" to "",
                "pasientStatusKodeMelding" to "",
                "status" to ""
            ) shouldBeEqualTo result.first.ruleInputs

            result.first.treeResult.ruleHit shouldBeEqualTo null

            result.first.treeResult.status shouldBeEqualTo Status.OK
        }

        test("should trigger rule NUMBER_OF_TREATMENT_DAYS_SET, should be MANUAL_PROCESSING") {
            val sykmelding = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.now(),
                        tom = LocalDate.now().plusDays(10),
                        behandlingsdager = 1
                    )
                )
            )

            val infotrygdForespResponse = InfotrygdForesp().apply {
                hovedStatus = StatusType().apply {
                    kodeMelding = "00"
                }
            }

            val receivedSykmelding = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = sykmelding,
                fellesformat = ""
            )

            val ruleMetadata = RuleMetadata(
                receivedDate = receivedSykmelding.mottattDato,
                signatureDate = receivedSykmelding.sykmelding.signaturDato,
                patientPersonNumber = receivedSykmelding.personNrPasient,
                rulesetVersion = receivedSykmelding.rulesetVersion,
                legekontorOrgnr = receivedSykmelding.legekontorOrgNr,
                tssid = receivedSykmelding.tssid,
                infotrygdForesp = infotrygdForespResponse
            )

            val result = ruleTree.runRules(sykmelding, ruleMetadata)

            result.first.rulePath.map { it.rule to it.ruleResult } shouldBeEqualTo listOf(
                ValidationRules.NUMBER_OF_TREATMENT_DAYS_SET to true
            )

            mapOf(
                "perioder" to sykmelding.perioder
            ) shouldBeEqualTo result.first.ruleInputs

            result.first.treeResult.ruleHit shouldBeEqualTo ValidationRuleHit.NUMBER_OF_TREATMENT_DAYS_SET.ruleHit

            result.first.treeResult.status shouldBeEqualTo Status.MANUAL_PROCESSING
        }

        test("should trigger rule GRADERT_REISETILSKUDD_ER_OPPGITT, should be MANUAL_PROCESSING") {
            val sykmelding = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.now(),
                        tom = LocalDate.now().plusDays(10),
                        gradert = Gradert(
                            reisetilskudd = true,
                            grad = 90
                        )
                    )
                )
            )

            val infotrygdForespResponse = InfotrygdForesp().apply {
                hovedStatus = StatusType().apply {
                    kodeMelding = "00"
                }
            }

            val receivedSykmelding = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = sykmelding,
                fellesformat = ""
            )

            val ruleMetadata = RuleMetadata(
                receivedDate = receivedSykmelding.mottattDato,
                signatureDate = receivedSykmelding.sykmelding.signaturDato,
                patientPersonNumber = receivedSykmelding.personNrPasient,
                rulesetVersion = receivedSykmelding.rulesetVersion,
                legekontorOrgnr = receivedSykmelding.legekontorOrgNr,
                tssid = receivedSykmelding.tssid,
                infotrygdForesp = infotrygdForespResponse
            )

            val result = ruleTree.runRules(sykmelding, ruleMetadata)

            result.first.rulePath.map { it.rule to it.ruleResult } shouldBeEqualTo listOf(
                ValidationRules.NUMBER_OF_TREATMENT_DAYS_SET to false,
                ValidationRules.GRADERT_REISETILSKUDD_ER_OPPGITT to true
            )

            mapOf(
                "perioder" to sykmelding.perioder
            ) shouldBeEqualTo result.first.ruleInputs

            result.first.treeResult.ruleHit shouldBeEqualTo ValidationRuleHit.GRADERT_REISETILSKUDD_ER_OPPGITT.ruleHit

            result.first.treeResult.status shouldBeEqualTo Status.MANUAL_PROCESSING
        }

        test("should trigger rule TRAVEL_SUBSIDY_SPECIFIED, should be MANUAL_PROCESSING") {
            val sykmelding = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.now(),
                        tom = LocalDate.now().plusDays(10),
                        reisetilskudd = true
                    )
                )
            )

            val infotrygdForespResponse = InfotrygdForesp().apply {
                hovedStatus = StatusType().apply {
                    kodeMelding = "00"
                }
            }

            val receivedSykmelding = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = sykmelding,
                fellesformat = ""
            )

            val ruleMetadata = RuleMetadata(
                receivedDate = receivedSykmelding.mottattDato,
                signatureDate = receivedSykmelding.sykmelding.signaturDato,
                patientPersonNumber = receivedSykmelding.personNrPasient,
                rulesetVersion = receivedSykmelding.rulesetVersion,
                legekontorOrgnr = receivedSykmelding.legekontorOrgNr,
                tssid = receivedSykmelding.tssid,
                infotrygdForesp = infotrygdForespResponse
            )

            val result = ruleTree.runRules(sykmelding, ruleMetadata)

            result.first.rulePath.map { it.rule to it.ruleResult } shouldBeEqualTo listOf(
                ValidationRules.NUMBER_OF_TREATMENT_DAYS_SET to false,
                ValidationRules.GRADERT_REISETILSKUDD_ER_OPPGITT to false,
                ValidationRules.TRAVEL_SUBSIDY_SPECIFIED to true
            )

            mapOf(
                "perioder" to sykmelding.perioder
            ) shouldBeEqualTo result.first.ruleInputs

            result.first.treeResult.ruleHit shouldBeEqualTo ValidationRuleHit.TRAVEL_SUBSIDY_SPECIFIED.ruleHit

            result.first.treeResult.status shouldBeEqualTo Status.MANUAL_PROCESSING
        }

    }
})
