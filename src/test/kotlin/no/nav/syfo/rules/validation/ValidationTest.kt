package no.nav.syfo.rules.validation

import io.kotest.core.spec.style.FunSpec
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.StatusType
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.syfo.generatePeriode
import no.nav.syfo.generatePrognose
import no.nav.syfo.generateSykmelding
import no.nav.syfo.model.Gradert
import no.nav.syfo.model.RuleMetadata
import no.nav.syfo.model.Status
import no.nav.syfo.receivedSykmelding
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDate
import java.util.UUID

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

        test("should trigger rule PATIENT_NOT_IN_IP, should be MANUAL_PROCESSING") {
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
                pasient = InfotrygdForesp.Pasient().apply {
                    isFinnes = false
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
                ValidationRules.PATIENT_NOT_IN_IP to true
            )

            mapOf(
                "perioder" to sykmelding.perioder,
                "pasient" to ruleMetadata.infotrygdForesp.pasient
            ) shouldBeEqualTo result.first.ruleInputs

            result.first.treeResult.ruleHit shouldBeEqualTo ValidationRuleHit.PATIENT_NOT_IN_IP.ruleHit

            result.first.treeResult.status shouldBeEqualTo Status.MANUAL_PROCESSING
        }

        test("should trigger rule PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE, should be MANUAL_PROCESSING") {
            val generateSykmelding = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2019, 1, 1),
                        tom = LocalDate.of(2019, 1, 5)
                    )
                )
            )

            val infotrygdForespResponse = InfotrygdForesp().apply {
                hovedStatus = StatusType().apply {
                    kodeMelding = "00"
                }
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.of(2019, 1, 2)
                                arbufoerTOM = LocalDate.of(2019, 1, 5)
                            }
                        }
                    )
                }
            }

            val receivedSykmelding = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = generateSykmelding,
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

            val result = ruleTree.runRules(generateSykmelding, ruleMetadata)

            result.first.rulePath.map { it.rule to it.ruleResult } shouldBeEqualTo listOf(
                ValidationRules.NUMBER_OF_TREATMENT_DAYS_SET to false,
                ValidationRules.GRADERT_REISETILSKUDD_ER_OPPGITT to false,
                ValidationRules.TRAVEL_SUBSIDY_SPECIFIED to false,
                ValidationRules.PATIENT_NOT_IN_IP to false,
                ValidationRules.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE to true
            )

            mapOf(
                "perioder" to generateSykmelding.perioder,
                "pasient" to ruleMetadata.infotrygdForesp.pasient,
                "infotrygdSykmelding" to ruleMetadata.infotrygdForesp.sMhistorikk?.sykmelding,
                "sykmeldingPerioder" to generateSykmelding.perioder
            ) shouldBeEqualTo result.first.ruleInputs

            result.first.treeResult.ruleHit shouldBeEqualTo ValidationRuleHit.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE.ruleHit

            result.first.treeResult.status shouldBeEqualTo Status.MANUAL_PROCESSING
        }

        test("should trigger rule SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1, should be MANUAL_PROCESSING") {
            val generateSykmelding = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2018, 1, 2),
                        tom = LocalDate.of(2018, 1, 15)
                    )
                )
            )

            val infotrygdForespResponse = InfotrygdForesp().apply {
                hovedStatus = StatusType().apply {
                    kodeMelding = "00"
                }
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.of(2018, 1, 1)
                                friskKode = "J"
                                hovedDiagnosekode = "001"
                                utbetTOM = LocalDate.of(2018, 1, 3)
                            }
                        }
                    )
                }
            }

            val receivedSykmelding = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = generateSykmelding,
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

            val result = ruleTree.runRules(generateSykmelding, ruleMetadata)

            result.first.rulePath.map { it.rule to it.ruleResult } shouldBeEqualTo listOf(
                ValidationRules.NUMBER_OF_TREATMENT_DAYS_SET to false,
                ValidationRules.GRADERT_REISETILSKUDD_ER_OPPGITT to false,
                ValidationRules.TRAVEL_SUBSIDY_SPECIFIED to false,
                ValidationRules.PATIENT_NOT_IN_IP to false,
                ValidationRules.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE to false,
                ValidationRules.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1 to true
            )

            mapOf(
                "perioder" to generateSykmelding.perioder,
                "pasient" to ruleMetadata.infotrygdForesp.pasient,
                "infotrygdSykmelding" to ruleMetadata.infotrygdForesp.sMhistorikk?.sykmelding,
                "sykmeldingPerioder" to generateSykmelding.perioder
            ) shouldBeEqualTo result.first.ruleInputs

            result.first.treeResult.ruleHit shouldBeEqualTo ValidationRuleHit.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1.ruleHit

            result.first.treeResult.status shouldBeEqualTo Status.MANUAL_PROCESSING
        }

        test("should trigger rule SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2, should be MANUAL_PROCESSING") {
            val generateSykmelding = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2018, 1, 4),
                        tom = LocalDate.now().plusMonths(3).plusDays(1)
                    )
                )
            )

            val infotrygdForespResponse = InfotrygdForesp().apply {
                hovedStatus = StatusType().apply {
                    kodeMelding = "00"
                }
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                friskKode = "J"
                                hovedDiagnosekode = "001"
                                utbetTOM = LocalDate.of(2018, 1, 3)
                            }
                        }
                    )
                }
            }

            val receivedSykmelding = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = generateSykmelding,
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

            val result = ruleTree.runRules(generateSykmelding, ruleMetadata)

            result.first.rulePath.map { it.rule to it.ruleResult } shouldBeEqualTo listOf(
                ValidationRules.NUMBER_OF_TREATMENT_DAYS_SET to false,
                ValidationRules.GRADERT_REISETILSKUDD_ER_OPPGITT to false,
                ValidationRules.TRAVEL_SUBSIDY_SPECIFIED to false,
                ValidationRules.PATIENT_NOT_IN_IP to false,
                ValidationRules.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE to false,
                ValidationRules.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1 to false,
                ValidationRules.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2 to true
            )

            mapOf(
                "perioder" to generateSykmelding.perioder,
                "pasient" to ruleMetadata.infotrygdForesp.pasient,
                "infotrygdSykmelding" to ruleMetadata.infotrygdForesp.sMhistorikk?.sykmelding,
                "sykmeldingPerioder" to generateSykmelding.perioder
            ) shouldBeEqualTo result.first.ruleInputs

            result.first.treeResult.ruleHit shouldBeEqualTo ValidationRuleHit.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2.ruleHit

            result.first.treeResult.status shouldBeEqualTo Status.MANUAL_PROCESSING
        }

        test("should trigger rule SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3, should be MANUAL_PROCESSING") {
            val generateSykmelding = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2018, 1, 7),
                        tom = LocalDate.now().plusMonths(3).plusDays(1)
                    )
                )
            )

            val infotrygdForespResponse = InfotrygdForesp().apply {
                hovedStatus = StatusType().apply {
                    kodeMelding = "00"
                }
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                friskKode = "J"
                                hovedDiagnosekode = "001"
                                utbetTOM = LocalDate.of(2018, 1, 5)
                            }
                        }
                    )
                }
            }

            val receivedSykmelding = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = generateSykmelding,
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

            val result = ruleTree.runRules(generateSykmelding, ruleMetadata)

            result.first.rulePath.map { it.rule to it.ruleResult } shouldBeEqualTo listOf(
                ValidationRules.NUMBER_OF_TREATMENT_DAYS_SET to false,
                ValidationRules.GRADERT_REISETILSKUDD_ER_OPPGITT to false,
                ValidationRules.TRAVEL_SUBSIDY_SPECIFIED to false,
                ValidationRules.PATIENT_NOT_IN_IP to false,
                ValidationRules.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE to false,
                ValidationRules.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1 to false,
                ValidationRules.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2 to false,
                ValidationRules.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3 to true
            )

            mapOf(
                "perioder" to generateSykmelding.perioder,
                "pasient" to ruleMetadata.infotrygdForesp.pasient,
                "infotrygdSykmelding" to ruleMetadata.infotrygdForesp.sMhistorikk?.sykmelding,
                "sykmeldingPerioder" to generateSykmelding.perioder
            ) shouldBeEqualTo result.first.ruleInputs

            result.first.treeResult.ruleHit shouldBeEqualTo ValidationRuleHit.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3.ruleHit

            result.first.treeResult.status shouldBeEqualTo Status.MANUAL_PROCESSING
        }

        test("should trigger rule NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT, should be MANUAL_PROCESSING") {
            val generateSykmelding = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        tom = LocalDate.of(2017, 1, 2)
                    )
                )
            )

            val infotrygdForespResponse = InfotrygdForesp().apply {
                hovedStatus = StatusType().apply {
                    kodeMelding = "00"
                }
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                friskmeldtDato = LocalDate.of(2017, 1, 1)
                                utbetTOM = LocalDate.of(2018, 1, 1)
                            }
                        }
                    )
                }
            }

            val receivedSykmelding = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = generateSykmelding,
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

            val result = ruleTree.runRules(generateSykmelding, ruleMetadata)

            result.first.rulePath.map { it.rule to it.ruleResult } shouldBeEqualTo listOf(
                ValidationRules.NUMBER_OF_TREATMENT_DAYS_SET to false,
                ValidationRules.GRADERT_REISETILSKUDD_ER_OPPGITT to false,
                ValidationRules.TRAVEL_SUBSIDY_SPECIFIED to false,
                ValidationRules.PATIENT_NOT_IN_IP to false,
                ValidationRules.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE to false,
                ValidationRules.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1 to false,
                ValidationRules.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2 to false,
                ValidationRules.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3 to false,
                ValidationRules.NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT to true
            )

            mapOf(
                "perioder" to generateSykmelding.perioder,
                "pasient" to ruleMetadata.infotrygdForesp.pasient,
                "infotrygdSykmelding" to ruleMetadata.infotrygdForesp.sMhistorikk?.sykmelding,
                "sykmeldingPerioder" to generateSykmelding.perioder,
                "sykmeldingPrognose" to generateSykmelding.prognose
            ) shouldBeEqualTo result.first.ruleInputs

            result.first.treeResult.ruleHit shouldBeEqualTo ValidationRuleHit.NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT.ruleHit

            result.first.treeResult.status shouldBeEqualTo Status.MANUAL_PROCESSING
        }

        test("should trigger rule NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE, should be MANUAL_PROCESSING") {
            val generateSykmelding = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        tom = LocalDate.of(2017, 1, 2)
                    )
                ),
                prognose = generatePrognose(arbeidsforEtterPeriode = true)
            )

            val infotrygdForespResponse = InfotrygdForesp().apply {
                hovedStatus = StatusType().apply {
                    kodeMelding = "00"
                }
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                friskmeldtDato = LocalDate.of(2018, 1, 3)
                            }
                        }
                    )
                }
            }

            val receivedSykmelding = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = generateSykmelding,
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

            val result = ruleTree.runRules(generateSykmelding, ruleMetadata)

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
                ValidationRules.NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE to true
            )

            mapOf(
                "perioder" to generateSykmelding.perioder,
                "pasient" to ruleMetadata.infotrygdForesp.pasient,
                "infotrygdSykmelding" to ruleMetadata.infotrygdForesp.sMhistorikk?.sykmelding,
                "sykmeldingPerioder" to generateSykmelding.perioder,
                "sykmeldingPrognose" to generateSykmelding.prognose
            ) shouldBeEqualTo result.first.ruleInputs

            result.first.treeResult.ruleHit shouldBeEqualTo ValidationRuleHit.NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE.ruleHit

            result.first.treeResult.status shouldBeEqualTo Status.MANUAL_PROCESSING
        }

        test("should trigger rule EXTANION_OVER_FA, should be MANUAL_PROCESSING") {
            val generateSykmelding = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2018, 1, 1),
                        tom = LocalDate.of(2018, 2, 1)
                    )
                )
            )

            val infotrygdForespResponse = InfotrygdForesp().apply {
                hovedStatus = StatusType().apply {
                    kodeMelding = "00"
                }
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            historikk.add(
                                TypeSMinfo.Historikk().apply {
                                    tilltak = TypeSMinfo.Historikk.Tilltak().apply {
                                        type = "FA"
                                        fom = LocalDate.of(2018, 1, 20)
                                        tom = LocalDate.of(2018, 1, 26)
                                    }
                                }
                            )
                        }
                    )
                }
            }

            val receivedSykmelding = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = generateSykmelding,
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

            val result = ruleTree.runRules(generateSykmelding, ruleMetadata)

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
                ValidationRules.EXTANION_OVER_FA to true
            )

            mapOf(
                "perioder" to generateSykmelding.perioder,
                "pasient" to ruleMetadata.infotrygdForesp.pasient,
                "infotrygdSykmelding" to ruleMetadata.infotrygdForesp.sMhistorikk?.sykmelding,
                "sykmeldingPerioder" to generateSykmelding.perioder,
                "sykmeldingPrognose" to generateSykmelding.prognose
            ) shouldBeEqualTo result.first.ruleInputs

            result.first.treeResult.ruleHit shouldBeEqualTo ValidationRuleHit.EXTANION_OVER_FA.ruleHit

            result.first.treeResult.status shouldBeEqualTo Status.MANUAL_PROCESSING
        }

        test("should trigger rule PERSON_MOVING_KODE_FL, should be MANUAL_PROCESSING") {
            val generateSykmelding = generateSykmelding()

            val infotrygdForespResponse = InfotrygdForesp().apply {
                hovedStatus = StatusType().apply {
                    kodeMelding = "00"
                }
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                stans = "FL"
                                arbufoerFOM = LocalDate.of(2017, 2, 1)
                            }
                        }
                    )
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.of(2017, 1, 1)
                            }
                        }
                    )
                }
            }

            val receivedSykmelding = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = generateSykmelding,
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

            val result = ruleTree.runRules(generateSykmelding, ruleMetadata)

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
                ValidationRules.PERSON_MOVING_KODE_FL to true
            )

            mapOf(
                "perioder" to generateSykmelding.perioder,
                "pasient" to ruleMetadata.infotrygdForesp.pasient,
                "infotrygdSykmelding" to ruleMetadata.infotrygdForesp.sMhistorikk?.sykmelding,
                "sykmeldingPerioder" to generateSykmelding.perioder,
                "sykmeldingPrognose" to generateSykmelding.prognose
            ) shouldBeEqualTo result.first.ruleInputs

            result.first.treeResult.ruleHit shouldBeEqualTo ValidationRuleHit.PERSON_MOVING_KODE_FL.ruleHit

            result.first.treeResult.status shouldBeEqualTo Status.MANUAL_PROCESSING
        }

        test("should trigger rule PERIOD_FOR_AA_ENDED, should be MANUAL_PROCESSING") {
            val generateSykmelding = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2017, 2, 1),
                        tom = LocalDate.now().plusMonths(3).plusDays(1)
                    )
                )
            )

            val infotrygdForespResponse = InfotrygdForesp().apply {
                hovedStatus = StatusType().apply {
                    kodeMelding = "00"
                }
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                stans = "AA"
                                arbufoerTOM = LocalDate.of(2017, 2, 2)
                            }
                        }
                    )
                }
            }

            val receivedSykmelding = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = generateSykmelding,
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

            val result = ruleTree.runRules(generateSykmelding, ruleMetadata)

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
                ValidationRules.PERIOD_FOR_AA_ENDED to true
            )

            mapOf(
                "perioder" to generateSykmelding.perioder,
                "pasient" to ruleMetadata.infotrygdForesp.pasient,
                "infotrygdSykmelding" to ruleMetadata.infotrygdForesp.sMhistorikk?.sykmelding,
                "sykmeldingPerioder" to generateSykmelding.perioder,
                "sykmeldingPrognose" to generateSykmelding.prognose
            ) shouldBeEqualTo result.first.ruleInputs

            result.first.treeResult.ruleHit shouldBeEqualTo ValidationRuleHit.PERIOD_FOR_AA_ENDED.ruleHit

            result.first.treeResult.status shouldBeEqualTo Status.MANUAL_PROCESSING
        }

        test("should trigger rule PERIOD_IS_AF, should be MANUAL_PROCESSING") {
            val generateSykmelding = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2017, 2, 1),
                        tom = LocalDate.now().plusMonths(3).plusDays(1)
                    )
                )
            )

            val infotrygdForespResponse = InfotrygdForesp().apply {
                hovedStatus = StatusType().apply {
                    kodeMelding = "00"
                }
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                stans = "AF"
                                arbufoerTOM = LocalDate.of(2017, 2, 2)
                            }
                        }
                    )
                }
            }

            val receivedSykmelding = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = generateSykmelding,
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

            val result = ruleTree.runRules(generateSykmelding, ruleMetadata)

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
                ValidationRules.PERIOD_IS_AF to true
            )

            mapOf(
                "perioder" to generateSykmelding.perioder,
                "pasient" to ruleMetadata.infotrygdForesp.pasient,
                "infotrygdSykmelding" to ruleMetadata.infotrygdForesp.sMhistorikk?.sykmelding,
                "sykmeldingPerioder" to generateSykmelding.perioder,
                "sykmeldingPrognose" to generateSykmelding.prognose,
                "sykmelding" to generateSykmelding
            ) shouldBeEqualTo result.first.ruleInputs

            result.first.treeResult.ruleHit shouldBeEqualTo ValidationRuleHit.PERIOD_IS_AF.ruleHit

            result.first.treeResult.status shouldBeEqualTo Status.MANUAL_PROCESSING
        }

        test("should trigger rule MAX_SICK_LEAVE_PAYOUT, should be MANUAL_PROCESSING") {
            val generateSykmelding = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2017, 2, 1),
                        tom = LocalDate.of(2017, 2, 10)
                    )
                )
            )

            val infotrygdForespResponse = InfotrygdForesp().apply {
                hovedStatus = StatusType().apply {
                    kodeMelding = "00"
                }
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerTOM = LocalDate.of(2017, 2, 2)
                                stans = "MAX"
                            }
                        }
                    )
                }
            }

            val receivedSykmelding = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = generateSykmelding,
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

            val result = ruleTree.runRules(generateSykmelding, ruleMetadata)

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
                ValidationRules.MAX_SICK_LEAVE_PAYOUT to true
            )

            mapOf(
                "perioder" to generateSykmelding.perioder,
                "pasient" to ruleMetadata.infotrygdForesp.pasient,
                "infotrygdSykmelding" to ruleMetadata.infotrygdForesp.sMhistorikk?.sykmelding,
                "sykmeldingPerioder" to generateSykmelding.perioder,
                "sykmeldingPrognose" to generateSykmelding.prognose,
                "sykmelding" to generateSykmelding
            ) shouldBeEqualTo result.first.ruleInputs

            result.first.treeResult.ruleHit shouldBeEqualTo ValidationRuleHit.MAX_SICK_LEAVE_PAYOUT.ruleHit

            result.first.treeResult.status shouldBeEqualTo Status.MANUAL_PROCESSING
        }

        test("should trigger rule ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING, should be MANUAL_PROCESSING") {
            val generateSykmelding = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2017, 2, 1),
                        tom = LocalDate.of(2017, 2, 10)
                    )
                )
            )

            val infotrygdForespResponse = InfotrygdForesp().apply {
                hovedStatus = StatusType().apply {
                    kodeMelding = "5"
                }
            }

            val receivedSykmelding = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = generateSykmelding,
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

            val result = ruleTree.runRules(generateSykmelding, ruleMetadata)

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
                ValidationRules.ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING to true
            )

            mapOf(
                "perioder" to generateSykmelding.perioder,
                "pasient" to ruleMetadata.infotrygdForesp.pasient,
                "infotrygdSykmelding" to "",
                "sykmeldingPerioder" to generateSykmelding.perioder,
                "sykmeldingPrognose" to generateSykmelding.prognose,
                "sykmelding" to generateSykmelding,
                "hovedStatusKodeMelding" to "5"
            ) shouldBeEqualTo result.first.ruleInputs

            result.first.treeResult.ruleHit shouldBeEqualTo ValidationRuleHit.ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING.ruleHit

            result.first.treeResult.status shouldBeEqualTo Status.MANUAL_PROCESSING
        }

        test("should trigger rule ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING, should be MANUAL_PROCESSING") {
            val generateSykmelding = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2017, 2, 1),
                        tom = LocalDate.of(2017, 2, 10)
                    )
                )
            )

            val infotrygdForespResponse = InfotrygdForesp().apply {
                hovedStatus = StatusType().apply {
                    kodeMelding = "00"
                }
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    status = StatusType().apply {
                        kodeMelding = "5"
                    }
                }
            }

            val receivedSykmelding = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = generateSykmelding,
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

            val result = ruleTree.runRules(generateSykmelding, ruleMetadata)

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
                ValidationRules.ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING to true
            )

            mapOf(
                "perioder" to generateSykmelding.perioder,
                "pasient" to ruleMetadata.infotrygdForesp.pasient,
                "infotrygdSykmelding" to ruleMetadata.infotrygdForesp.sMhistorikk?.sykmelding,
                "sykmeldingPerioder" to generateSykmelding.perioder,
                "sykmeldingPrognose" to generateSykmelding.prognose,
                "sykmelding" to generateSykmelding,
                "hovedStatusKodeMelding" to "00",
                "smHistorikkKodeMelding" to "5"
            ) shouldBeEqualTo result.first.ruleInputs

            result.first.treeResult.ruleHit shouldBeEqualTo ValidationRuleHit.ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING.ruleHit

            result.first.treeResult.status shouldBeEqualTo Status.MANUAL_PROCESSING
        }

        test("should trigger rule ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING, should be MANUAL_PROCESSING") {
            val generateSykmelding = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2017, 2, 1),
                        tom = LocalDate.of(2017, 2, 10)
                    )
                )
            )

            val infotrygdForespResponse = InfotrygdForesp().apply {
                hovedStatus = StatusType().apply {
                    kodeMelding = "00"
                }
                parallelleYtelser = InfotrygdForesp.ParallelleYtelser().apply {
                    status = StatusType().apply {
                        kodeMelding = "5"
                    }
                }
            }

            val receivedSykmelding = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = generateSykmelding,
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

            val result = ruleTree.runRules(generateSykmelding, ruleMetadata)

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
                ValidationRules.ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING to true
            )

            mapOf(
                "perioder" to generateSykmelding.perioder,
                "pasient" to ruleMetadata.infotrygdForesp.pasient,
                "infotrygdSykmelding" to "",
                "sykmeldingPerioder" to generateSykmelding.perioder,
                "sykmeldingPrognose" to generateSykmelding.prognose,
                "sykmelding" to generateSykmelding,
                "hovedStatusKodeMelding" to "00",
                "smHistorikkKodeMelding" to "",
                "parallelleYtelsesKodeMelding" to "5"
            ) shouldBeEqualTo result.first.ruleInputs

            result.first.treeResult.ruleHit shouldBeEqualTo ValidationRuleHit.ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING.ruleHit

            result.first.treeResult.status shouldBeEqualTo Status.MANUAL_PROCESSING
        }

        test("should trigger rule ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING, should be MANUAL_PROCESSING") {
            val generateSykmelding = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2017, 2, 1),
                        tom = LocalDate.of(2017, 2, 10)
                    )
                )
            )

            val infotrygdForespResponse = InfotrygdForesp().apply {
                hovedStatus = StatusType().apply {
                    kodeMelding = "00"
                }
                diagnosekodeOK = InfotrygdForesp.DiagnosekodeOK().apply {
                    status = StatusType().apply {
                        kodeMelding = "5"
                    }
                }
            }

            val receivedSykmelding = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = generateSykmelding,
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

            val result = ruleTree.runRules(generateSykmelding, ruleMetadata)

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
                ValidationRules.ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING to true
            )

            mapOf(
                "perioder" to generateSykmelding.perioder,
                "pasient" to ruleMetadata.infotrygdForesp.pasient,
                "infotrygdSykmelding" to "",
                "sykmeldingPerioder" to generateSykmelding.perioder,
                "sykmeldingPrognose" to generateSykmelding.prognose,
                "sykmelding" to generateSykmelding,
                "hovedStatusKodeMelding" to "00",
                "smHistorikkKodeMelding" to "",
                "parallelleYtelsesKodeMelding" to "",
                "diagnoseKodeKodeMelding" to "5"
            ) shouldBeEqualTo result.first.ruleInputs

            result.first.treeResult.ruleHit shouldBeEqualTo ValidationRuleHit.ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING.ruleHit

            result.first.treeResult.status shouldBeEqualTo Status.MANUAL_PROCESSING
        }

        test("should trigger rule ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING, should be MANUAL_PROCESSING") {
            val generateSykmelding = generateSykmelding()

            val infotrygdForespResponse = InfotrygdForesp().apply {
                hovedStatus = StatusType().apply {
                    kodeMelding = "00"
                }
                pasient = InfotrygdForesp.Pasient().apply {
                    isFinnes = true
                    status = StatusType().apply {
                        kodeMelding = "5"
                    }
                }
            }

            val receivedSykmelding = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = generateSykmelding,
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

            val result = ruleTree.runRules(generateSykmelding, ruleMetadata)

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
                ValidationRules.ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING to true
            )

            mapOf(
                "perioder" to generateSykmelding.perioder,
                "pasient" to ruleMetadata.infotrygdForesp.pasient,
                "infotrygdSykmelding" to "",
                "sykmeldingPerioder" to generateSykmelding.perioder,
                "sykmeldingPrognose" to generateSykmelding.prognose,
                "sykmelding" to generateSykmelding,
                "hovedStatusKodeMelding" to "00",
                "smHistorikkKodeMelding" to "",
                "parallelleYtelsesKodeMelding" to "",
                "diagnoseKodeKodeMelding" to "",
                "pasientStatusKodeMelding" to "5"
            ) shouldBeEqualTo result.first.ruleInputs

            result.first.treeResult.ruleHit shouldBeEqualTo ValidationRuleHit.ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING.ruleHit

            result.first.treeResult.status shouldBeEqualTo Status.MANUAL_PROCESSING
        }

        test("should trigger rule ARBEIDUFORETOM_MANGLER, should be MANUAL_PROCESSING") {
            val generateSykmelding = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2019, 6, 27),
                        tom = LocalDate.of(2019, 6, 28)
                    )
                )
            )

            val statusType = StatusType().apply {
                kodeMelding = "00"
            }

            val infotrygdForespResponse = InfotrygdForesp().apply {
                hovedStatus = StatusType().apply {
                    kodeMelding = "00"
                }
                sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                    sykmelding.add(
                        TypeSMinfo().apply {
                            periode = TypeSMinfo.Periode().apply {
                                arbufoerFOM = LocalDate.of(2019, 6, 24)
                            }
                        }
                    )
                    status = statusType
                }
            }

            val receivedSykmelding = receivedSykmelding(
                id = UUID.randomUUID().toString(),
                sykmelding = generateSykmelding,
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

            val result = ruleTree.runRules(generateSykmelding, ruleMetadata)

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
                ValidationRules.ARBEIDUFORETOM_MANGLER to true
            )

            mapOf(
                "perioder" to generateSykmelding.perioder,
                "pasient" to ruleMetadata.infotrygdForesp.pasient,
                "infotrygdSykmelding" to ruleMetadata.infotrygdForesp.sMhistorikk?.sykmelding,
                "sykmeldingPerioder" to generateSykmelding.perioder,
                "sykmeldingPrognose" to generateSykmelding.prognose,
                "sykmelding" to generateSykmelding,
                "hovedStatusKodeMelding" to "00",
                "smHistorikkKodeMelding" to "00",
                "parallelleYtelsesKodeMelding" to "",
                "diagnoseKodeKodeMelding" to "",
                "pasientStatusKodeMelding" to "",
                "sykmelding" to ruleMetadata.infotrygdForesp.sMhistorikk?.sykmelding,
                "status" to statusType
            ) shouldBeEqualTo result.first.ruleInputs

            result.first.treeResult.ruleHit shouldBeEqualTo ValidationRuleHit.ARBEIDUFORETOM_MANGLER.ruleHit

            result.first.treeResult.status shouldBeEqualTo Status.MANUAL_PROCESSING
        }
    }
})
