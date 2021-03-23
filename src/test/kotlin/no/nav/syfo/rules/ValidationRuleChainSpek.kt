package no.nav.syfo.rules

import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.StatusType
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.syfo.generatePeriode
import no.nav.syfo.generatePrognose
import no.nav.syfo.generateSykmelding
import no.nav.syfo.model.Gradert
import no.nav.syfo.model.Sykmelding
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

object ValidationRuleChainSpek : Spek({

    fun deafaultInfotrygdForesp() = InfotrygdForesp().apply {
        hovedStatus = StatusType().apply {
            kodeMelding = "00"
        }
    }

    fun ruleData(
        healthInformation: Sykmelding,
        infotrygdForesp: InfotrygdForesp
    ): RuleData<InfotrygdForesp> = RuleData(healthInformation, infotrygdForesp)

    describe("Testing infotrygd rules and checking the rule outcomes") {

        it("Should check rule GRADERT_REISETILSKUDD_ER_OPPGITT, should trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.now(),
                        tom = LocalDate.now().plusMonths(3).plusDays(1),
                        gradert = Gradert(
                            reisetilskudd = true,
                            grad = 90
                        )
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()

            ValidationRuleChain.GRADERT_REISETILSKUDD_ER_OPPGITT(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo true
        }

        it("Should check rule GRADUAL_SYKMELDING_COMBINED_WITH_TRAVEL, should NOT trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.now(),
                        tom = LocalDate.now().plusMonths(3).plusDays(1),
                        gradert = Gradert(
                            reisetilskudd = false,
                            grad = 90
                        )
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()

            ValidationRuleChain.GRADERT_REISETILSKUDD_ER_OPPGITT(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule NUMBER_OF_TREATMENT_DAYS_SET, should trigger rule") {

            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.now(),
                        tom = LocalDate.now().plusMonths(3).plusDays(1),
                        behandlingsdager = 1
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()

            ValidationRuleChain.NUMBER_OF_TREATMENT_DAYS_SET(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo true
        }

        it("Should check rule NUMBER_OF_TREATMENT_DAYS_SET, should NOT trigger rule") {
            val healthInformation = generateSykmelding()

            val infotrygdForespResponse = deafaultInfotrygdForesp()

            ValidationRuleChain.NUMBER_OF_TREATMENT_DAYS_SET(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule GRADERT_TRAVEL_SUBSIDY_SPECIFIED, should trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.now(),
                        tom = LocalDate.now().plusMonths(3).plusDays(1),
                        gradert = Gradert(
                            reisetilskudd = true,
                            grad = 90
                        )
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()

            ValidationRuleChain.GRADERT_REISETILSKUDD_ER_OPPGITT(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo true
        }

        it("Should check rule GRADERT_TRAVEL_SUBSIDY_SPECIFIED, should NOT trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.now(),
                        tom = LocalDate.now().plusMonths(3).plusDays(1),
                        gradert = Gradert(
                            reisetilskudd = false,
                            grad = 90
                        )
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()

            ValidationRuleChain.GRADERT_REISETILSKUDD_ER_OPPGITT(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule TRAVEL_SUBSIDY_SPECIFIED, should trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.now(),
                        tom = LocalDate.now().plusMonths(3).plusDays(1),
                        reisetilskudd = true
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()

            ValidationRuleChain.TRAVEL_SUBSIDY_SPECIFIED(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo true
        }

        it("Should check rule GRADERT_TRAVEL_SUBSIDY_SPECIFIED, should NOT trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.now(),
                        tom = LocalDate.now().plusMonths(3).plusDays(1),
                        reisetilskudd = false
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()

            ValidationRuleChain.TRAVEL_SUBSIDY_SPECIFIED(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule PATIENT_NOT_IN_IP, should trigger rule") {
            val healthInformation = generateSykmelding()
            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.pasient = InfotrygdForesp.Pasient().apply {
                isFinnes = false
            }

            ValidationRuleChain.PATIENT_NOT_IN_IP(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo true
        }

        it("Should check rule PATIENT_NOT_IN_IP, should NOT trigger rule") {
            val healthInformation = generateSykmelding()
            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.pasient = InfotrygdForesp.Pasient().apply {
                isFinnes = true
            }

            ValidationRuleChain.PATIENT_NOT_IN_IP(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE, should trigger rule when fom < infotrygdFom") {
            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerFOM = LocalDate.of(2019, 1, 2)
                            arbufoerTOM = LocalDate.of(2019, 1, 5)
                        }
                    }
                )
            }

            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2019, 1, 1),
                        tom = LocalDate.of(2019, 1, 5)
                    )
                )
            )

            ValidationRuleChain.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo true
        }

        it("Should check rule PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE, should not trigger rule when FOM > infotrygds FOM && TOM > infotrygds TOM") {
            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerFOM = LocalDate.of(2019, 1, 1)
                            arbufoerTOM = LocalDate.of(2019, 1, 5)
                        }
                    }
                )
            }

            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2019, 1, 3),
                        tom = LocalDate.of(2019, 1, 10)
                    )
                )
            )

            ValidationRuleChain.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE, should not trigger rule when FOM > infotrygds FOM && TOM = infotrygds TOM") {
            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerFOM = LocalDate.of(2019, 1, 1)
                            arbufoerTOM = LocalDate.of(2019, 1, 5)
                        }
                    }
                )
            }

            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2019, 1, 2),
                        tom = LocalDate.of(2019, 1, 5)
                    )
                )
            )

            ValidationRuleChain.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE, should not trigger rule when FOM = infotrygds FOM && TOM > infotrygds TOM") {
            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerFOM = LocalDate.of(2019, 1, 1)
                            arbufoerTOM = LocalDate.of(2019, 1, 5)
                        }
                    }
                )
            }

            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2019, 1, 1),
                        tom = LocalDate.of(2019, 1, 10)
                    )
                )
            )

            ValidationRuleChain.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE, should not trigger rule when FOM = infotrygds FOM && TOM = infotrygds TOM") {
            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerFOM = LocalDate.of(2019, 1, 1)
                            arbufoerTOM = LocalDate.of(2019, 1, 5)
                        }
                    }
                )
            }

            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2019, 1, 1),
                        tom = LocalDate.of(2019, 1, 5)
                    )
                )
            )

            ValidationRuleChain.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE, should trigger rule") {

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerFOM = LocalDate.of(2019, 1, 1)
                            arbufoerTOM = LocalDate.of(2019, 1, 5)
                        }
                    }
                )
            }

            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2019, 1, 1),
                        tom = LocalDate.of(2019, 1, 4)
                    )
                )
            )

            ValidationRuleChain.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo true
        }

        it("Should check rule PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE, should not trigger rule") {

            val infotrygdForespResponse = deafaultInfotrygdForesp()

            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2019, 1, 1),
                        tom = LocalDate.of(2019, 1, 4)
                    )
                )
            )

            ValidationRuleChain.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE, should NOT trigger rule, when same periode") {

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerFOM = LocalDate.of(2018, 1, 2)
                            arbufoerTOM = LocalDate.of(2018, 1, 8)
                        }
                    }
                )
            }

            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2018, 1, 2),
                        tom = LocalDate.of(2018, 1, 8)
                    )
                )
            )

            ValidationRuleChain.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE, should NOT trigger rule, when same periode") {

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerFOM = LocalDate.of(2019, 1, 2)
                            arbufoerTOM = LocalDate.of(2019, 1, 8)
                        }
                    }
                )
            }

            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2019, 1, 2),
                        tom = LocalDate.of(2019, 1, 9)
                    )
                )
            )

            ValidationRuleChain.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE, should NOT trigger rule") {

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerFOM = LocalDate.of(2018, 1, 2)
                            arbufoerTOM = LocalDate.of(2018, 1, 8)
                        }
                    }
                )
            }

            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2018, 1, 9),
                        tom = LocalDate.of(2018, 1, 15)
                    )
                )
            )

            ValidationRuleChain.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("should not get null") {
            val sm = listOf(
                TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        arbufoerTOM = LocalDate.of(2009, 1, 1)
                    }
                },
                TypeSMinfo().apply {
                    periode = TypeSMinfo.Periode().apply {
                        arbufoerTOM = null
                    }
                }
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.addAll(sm)
            }

            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2018, 1, 9),
                        tom = LocalDate.of(2018, 1, 15)
                    )
                )
            )

            ValidationRuleChain.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("should check rule PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE, should not get null pointer") {
            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerTOM = null
                        }
                    }
                )
            }

            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2018, 1, 9),
                        tom = LocalDate.of(2018, 1, 15)
                    )
                )
            )

            ValidationRuleChain.PARTIALLY_COINCIDENT_SICK_LEAVE_PERIOD_WITH_PREVIOUSLY_REGISTERED_SICK_LEAVE(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1, should trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2018, 1, 2),
                        tom = LocalDate.of(2018, 1, 15)
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
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

            ValidationRuleChain.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo true
        }

        it("Should check rule SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1, should NOT trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2018, 1, 3),
                        tom = LocalDate.of(2018, 1, 15)
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
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

            ValidationRuleChain.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_1(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2, should trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2018, 1, 4),
                        tom = LocalDate.now().plusMonths(3).plusDays(1)
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
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

            ValidationRuleChain.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo true
        }

        it("Should check rule SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2, should NOT trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2018, 1, 4),
                        tom = LocalDate.now().plusMonths(3).plusDays(1)
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            friskKode = "J"
                            hovedDiagnosekode = "001"
                            utbetTOM = LocalDate.of(2018, 1, 4)
                        }
                    }
                )
            }

            ValidationRuleChain.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_2(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3, should trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2018, 1, 6),
                        tom = LocalDate.now().plusMonths(3).plusDays(1)
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
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

            ValidationRuleChain.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo true
        }

        it("Should check rule SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3, should NOT trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2018, 1, 6),
                        tom = LocalDate.now().plusMonths(3).plusDays(1)
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            friskKode = "J"
                            hovedDiagnosekode = "001"
                            utbetTOM = LocalDate.of(2018, 1, 6)
                        }
                    }
                )
            }

            ValidationRuleChain.SICKLEAVE_EXTENTION_FROM_DIFFRENT_NAV_OFFICE_3(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT, should trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        tom = LocalDate.of(2017, 1, 2)
                    )
                ),
                prognose = generatePrognose(arbeidsforEtterPeriode = true)
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            friskmeldtDato = LocalDate.of(2017, 1, 1)
                            utbetTOM = LocalDate.of(2018, 1, 1)
                        }
                    }
                )
            }

            ValidationRuleChain.NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo true
        }

        it("Should check rule NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT, should NOT trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        tom = LocalDate.of(2017, 1, 3)
                    )
                ),
                prognose = generatePrognose(arbeidsforEtterPeriode = true)
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            friskmeldtDato = LocalDate.of(2017, 1, 1)
                            utbetTOM = LocalDate.of(2017, 1, 2)
                        }
                    }
                )
            }

            ValidationRuleChain.NEW_CLEAN_BILL_DATE_BEFORE_PAYOUT(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE, should trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        tom = LocalDate.of(2018, 1, 2)
                    )
                ),
                prognose = generatePrognose(arbeidsforEtterPeriode = true)
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            friskmeldtDato = LocalDate.of(2018, 1, 3)
                        }
                    }
                )
            }
            ValidationRuleChain.NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo true
        }

        it("Should check rule NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE, should NOT trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2018, 1, 2),
                        tom = LocalDate.now().plusMonths(3).plusDays(1)
                    )
                ),
                prognose = generatePrognose(arbeidsforEtterPeriode = true)
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            friskmeldtDato = LocalDate.of(2018, 1, 1)
                        }
                    }
                )
            }
            ValidationRuleChain.NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE, should NOT trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2018, 1, 2),
                        tom = LocalDate.now().plusMonths(3).plusDays(1)
                    )
                ),
                prognose = generatePrognose(arbeidsforEtterPeriode = true)
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                    }
                )
            }
            ValidationRuleChain.NEW_CLEAN_BILL_DATE_BEFORE_REGISTERD_CLEAN_BILL_DATE(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule EXTANION_OVER_FA, should trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2018, 1, 1),
                        tom = LocalDate.of(2018, 2, 1)
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
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
            ValidationRuleChain.EXTANION_OVER_FA(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo true
        }

        it("Should check rule EXTANION_OVER_FA, should NOT trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2018, 1, 1),
                        tom = LocalDate.of(2018, 2, 1)
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        historikk.add(
                            TypeSMinfo.Historikk().apply {
                                tilltak = TypeSMinfo.Historikk.Tilltak().apply {
                                    type = "FA"
                                    fom = LocalDate.of(2018, 2, 2)
                                    tom = LocalDate.of(2018, 2, 8)
                                }
                            }
                        )
                    }
                )
            }
            ValidationRuleChain.EXTANION_OVER_FA(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule PERSON_MOVING_KODE_FL, should trigger rule") {
            val healthInformation = generateSykmelding()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
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

            ValidationRuleChain.PERSON_MOVING_KODE_FL(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo true
        }

        it("Should check rule PERSON_MOVING_KODE_FL, should NOT trigger rule") {
            val healthInformation = generateSykmelding()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            stans = "PL"
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

            ValidationRuleChain.PERSON_MOVING_KODE_FL(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule PERIOD_FOR_AA_ENDED, should trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2017, 2, 1),
                        tom = LocalDate.now().plusMonths(3).plusDays(1)
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            stans = "AA"
                            arbufoerTOM = LocalDate.of(2017, 2, 2)
                        }
                    }
                )
            }

            ValidationRuleChain.PERIOD_FOR_AA_ENDED(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo true
        }

        it("Should check rule PERIOD_FOR_AA_ENDED, should NOT trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2017, 2, 2),
                        tom = LocalDate.now().plusMonths(3).plusDays(1)
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            stans = "AA"
                            arbufoerTOM = LocalDate.of(2017, 2, 1)
                        }
                    }
                )
            }

            ValidationRuleChain.PERIOD_FOR_AA_ENDED(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule PERIOD_IS_AF, should trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2017, 2, 1),
                        tom = LocalDate.now().plusMonths(3).plusDays(1)
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            stans = "AF"
                            arbufoerTOM = LocalDate.of(2017, 2, 2)
                        }
                    }
                )
            }

            ValidationRuleChain.PERIOD_IS_AF(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo true
        }

        it("Should check rule PERIOD_IS_AF, should NOT trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2017, 2, 2),
                        tom = LocalDate.now().plusMonths(3).plusDays(1)
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            stans = "AF"
                            arbufoerTOM = LocalDate.of(2017, 2, 1)
                        }
                    }
                )
            }

            ValidationRuleChain.PERIOD_IS_AF(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule MAX_SICK_LEAVE_PAYOUT, should trigger rule") {

            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2017, 2, 1),
                        tom = LocalDate.of(2017, 2, 10)
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerTOM = LocalDate.of(2017, 2, 2)
                            stans = "MAX"
                        }
                    }
                )
            }

            ValidationRuleChain.MAX_SICK_LEAVE_PAYOUT(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo true
        }

        it("Should check rule MAX_SICK_LEAVE_PAYOUT, should trigger rule") {

            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2017, 8, 1),
                        tom = LocalDate.of(2017, 8, 10)
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerTOM = LocalDate.of(2017, 2, 2)
                            stans = "MAX"
                        }
                    }
                )
            }

            ValidationRuleChain.MAX_SICK_LEAVE_PAYOUT(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo true
        }

        it("Should check rule MAX_SICK_LEAVE_PAYOUT, should NOT trigger rule") {

            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2017, 2, 11),
                        tom = LocalDate.of(2017, 2, 20)
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerFOM = LocalDate.of(2017, 2, 2)
                            arbufoerTOM = LocalDate.of(2017, 2, 10)
                        }
                    }
                )

                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerFOM = LocalDate.of(2016, 2, 2)
                            arbufoerTOM = LocalDate.of(2016, 2, 10)
                            stans = "MAX"
                        }
                    }
                )
            }

            ValidationRuleChain.MAX_SICK_LEAVE_PAYOUT(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule MAX_SICK_LEAVE_PAYOUT, should NOT trigger rule") {

            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.now(),
                        tom = LocalDate.now().plusDays(10)
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerFOM = LocalDate.now()
                            stans = "AAA"
                        }
                    }
                )
            }

            ValidationRuleChain.MAX_SICK_LEAVE_PAYOUT(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule MAX_SICK_LEAVE_PAYOUT, should NOT trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.now(),
                        tom = LocalDate.now().plusDays(10)
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerFOM = LocalDate.now()
                            stans = "DOD"
                        }
                    }
                )
            }

            ValidationRuleChain.MAX_SICK_LEAVE_PAYOUT(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule MAX_SICK_LEAVE_PAYOUT, should NOT trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.now(),
                        tom = LocalDate.now().plusDays(10)
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerFOM = LocalDate.now().plusDays(15)
                        }
                    }
                )
            }

            ValidationRuleChain.MAX_SICK_LEAVE_PAYOUT(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule MAX_SICK_LEAVE_PAYOUT, should NOT trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.now(),
                        tom = LocalDate.now().plusDays(10)
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()

            ValidationRuleChain.MAX_SICK_LEAVE_PAYOUT(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING, should trigger rule") {
            val healthInformation = generateSykmelding()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.hovedStatus = StatusType().apply {
                kodeMelding = "5"
            }

            ValidationRuleChain.ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo true
        }

        it("Should check rule ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING, should NOT trigger rule") {
            val healthInformation = generateSykmelding()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.hovedStatus = StatusType().apply {
                kodeMelding = "0"
            }

            ValidationRuleChain.ERROR_FROM_IT_HOUVED_STATUS_KODEMELDING(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING, should trigger rule") {
            val healthInformation = generateSykmelding()
            val infotrygdForespResponse = deafaultInfotrygdForesp()

            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                status = StatusType().apply {
                    kodeMelding = "5"
                }
            }

            ValidationRuleChain.ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo true
        }

        it("Should check rule ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING, should NOT trigger rule") {
            val healthInformation = generateSykmelding()
            val infotrygdForespResponse = deafaultInfotrygdForesp()

            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                status = StatusType().apply {
                    kodeMelding = "0"
                }
            }

            ValidationRuleChain.ERROR_FROM_IT_SMHISTORIKK_STATUS_KODEMELDING(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING, should trigger rule") {
            val healthInformation = generateSykmelding()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.parallelleYtelser = InfotrygdForesp.ParallelleYtelser().apply {
                status = StatusType().apply {
                    kodeMelding = "5"
                }
            }

            ValidationRuleChain.ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo true
        }

        it("Should check rule ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING, should NOT trigger rule") {
            val healthInformation = generateSykmelding()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.parallelleYtelser = InfotrygdForesp.ParallelleYtelser().apply {
                status = StatusType().apply {
                    kodeMelding = "0"
                }
            }

            ValidationRuleChain.ERROR_FROM_IT_PARALELLYTELSER_STATUS_KODEMELDING(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING, should trigger rule") {
            val healthInformation = generateSykmelding()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.diagnosekodeOK = InfotrygdForesp.DiagnosekodeOK().apply {
                status = StatusType().apply {
                    kodeMelding = "5"
                }
            }

            ValidationRuleChain.ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo true
        }

        it("Should check rule ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING, should NOT trigger rule") {
            val healthInformation = generateSykmelding()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.diagnosekodeOK = InfotrygdForesp.DiagnosekodeOK().apply {
                status = StatusType().apply {
                    kodeMelding = "0"
                }
            }

            ValidationRuleChain.ERROR_FROM_IT_DIAGNOSE_OK_UTREKK_STATUS_KODEMELDING(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING, should trigger rule") {
            val healthInformation = generateSykmelding()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.pasient = InfotrygdForesp.Pasient().apply {
                status = StatusType().apply {
                    kodeMelding = "5"
                }
            }

            ValidationRuleChain.ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo true
        }

        it("Should check rule ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING, should NOT trigger rule") {
            val healthInformation = generateSykmelding()

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.pasient = InfotrygdForesp.Pasient().apply {
                status = StatusType().apply {
                    kodeMelding = "0"
                }
            }

            ValidationRuleChain.ERROR_FROM_IT_PASIENT_UTREKK_STATUS_KODEMELDING(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule ERROR_FROM_IT_ARBEIDUFORETOM_MANGLER, should NOT trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2019, 6, 27),
                        tom = LocalDate.of(2019, 6, 28)
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerFOM = LocalDate.of(2019, 6, 24)
                        }
                    }
                )
                status = StatusType().apply {
                    kodeMelding = "00"
                }
            }

            ValidationRuleChain.ARBEIDUFORETOM_MANGLER(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo true
        }

        it("Should check rule ERROR_FROM_IT_ARBEIDUFORETOM_MANGLER, should NOT trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2019, 6, 27),
                        tom = LocalDate.of(2019, 6, 28)
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                sykmelding.add(
                    TypeSMinfo().apply {
                        periode = TypeSMinfo.Periode().apply {
                            arbufoerFOM = LocalDate.of(2019, 6, 24)
                            arbufoerTOM = LocalDate.of(2019, 6, 25)
                        }
                    }
                )
                status = StatusType().apply {
                    kodeMelding = "00"
                }
            }

            ValidationRuleChain.ARBEIDUFORETOM_MANGLER(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }

        it("Should check rule ERROR_FROM_IT_ARBEIDUFORETOM_MANGLER, should NOT trigger rule") {
            val healthInformation = generateSykmelding(
                perioder = listOf(
                    generatePeriode(
                        fom = LocalDate.of(2019, 6, 27),
                        tom = LocalDate.of(2019, 6, 28)
                    )
                )
            )

            val infotrygdForespResponse = deafaultInfotrygdForesp()
            infotrygdForespResponse.sMhistorikk = InfotrygdForesp.SMhistorikk().apply {
                status = StatusType().apply {
                    kodeMelding = "04"
                }
            }

            ValidationRuleChain.ARBEIDUFORETOM_MANGLER(ruleData(healthInformation, infotrygdForespResponse)) shouldBeEqualTo false
        }
    }
})
